#!/usr/bin/env python3
"""Drive the installed Reader development app through the M2 product UI on an approved AVD."""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.request import urlopen
from uuid import uuid4
import xml.etree.ElementTree as ET


parser = argparse.ArgumentParser()
parser.add_argument("--adb", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--avd", required=True, help="Exact isolated AVD name")
parser.add_argument("--output", required=True, type=Path)
parser.add_argument("--fixture", type=Path, default=Path(__file__).with_name("loopback_fixture.py"))
args = parser.parse_args()
args.output.mkdir()
target = args.serial
package = "io.reader.minliny.rebuild.android.dev"
remote_window = f"/data/local/tmp/reader-xplat-product-{uuid4().hex}.xml"
sequence = 0


def adb(*command):
    return subprocess.check_output([args.adb, "-s", target, *command], timeout=20)


def window():
    global sequence
    adb("shell", "uiautomator", "dump", remote_window)
    sequence += 1
    result = adb("exec-out", "cat", remote_window)
    (args.output / f"window-{sequence:03d}.xml").write_bytes(result)
    return [node.attrib for node in ET.fromstring(result).iter("node") if node.get("package") == package]


def bound(node):
    left, top, right, bottom = [int(value) for value in re.findall(r"\d+", node["bounds"])]
    return ((left + right) // 2, (top + bottom) // 2)


def wait_for(description, predicate, seconds=20):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        nodes = window()
        accepted = [node for node in nodes if predicate(node)]
        if accepted:
            return accepted[-1]
    raise RuntimeError(f"{description}: visible nodes {[(n.get('text'), n.get('content-desc')) for n in nodes]}")


def has_label(text):
    return lambda node: text in {node.get("text"), node.get("content-desc")}


def click(text):
    node = wait_for(text, has_label(text))
    x, y = bound(node)
    adb("shell", "input", "tap", str(x), str(y))
    print(f"CLICK {text} ({x},{y})", flush=True)
    time.sleep(0.45)


def put_text(value):
    x, y = bound(wait_for("editable field", lambda node: node.get("class") == "android.widget.EditText"))
    adb("shell", "input", "tap", str(x), str(y))
    wait_for("focused editable field", lambda node: node.get("class") == "android.widget.EditText" and node.get("focused") == "true")
    assert re.fullmatch(r"[A-Za-z0-9:/._-]+", value), "Controlled ASCII input required"
    time.sleep(0.5)
    for start in range(0, len(value), 4):
        adb("shell", "input", "text", value[start:start + 4])
        expected = value[:start + 4]
        wait_for("inserted text", lambda node: node.get("class") == "android.widget.EditText"
                 and expected in node.get("text", ""))
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    print(f"INPUT {value}", flush=True)


def screenshot(label):
    (args.output / f"{label}.png").write_bytes(adb("exec-out", "screencap", "-p"))


def verify_running_target():
    name = adb("emu", "avd", "name").decode().splitlines()[0]
    assert name == args.avd, f"Expected {args.avd}, observed {name}"
    assert adb("shell", "pm", "path", package).strip(), "Installed Reader development app required"
    protected = adb("shell", "pm", "list", "packages", "--show-versioncode", "io.reader.minliny").decode()
    return [line for line in protected.splitlines() if line.split()[0] == "package:io.reader.minliny"]


def launch_app():
    adb("shell", "am", "force-stop", package)
    receipt = adb("shell", "am", "start", "-W", "-n",
                  f"{package}/io.reader.rebuild.features.shell.ReaderActivity").decode()
    assert "Status: ok" in receipt, receipt
    print(receipt, flush=True)


before = verify_running_target()
port_file = args.output / "port.txt"
port = None
with (args.output / "http.log").open("w") as server_log, subprocess.Popen(
    [sys.executable, str(args.fixture), "--port-file", str(port_file)], stdout=server_log, stderr=subprocess.STDOUT
) as server:
    try:
        for _ in range(60):
            if port_file.exists() and port_file.stat().st_size:
                break
            assert server.poll() is None
            time.sleep(0.1)
        port = int(port_file.read_text())
        origin = f"http://127.0.0.1:{port}"
        assert urlopen(f"{origin}/ready", timeout=3).read() == b"live"
        adb("reverse", f"tcp:{port}", f"tcp:{port}")
        launch_app()
        auto = [
            "更多", "书源管理", "导入书源", "网址",
            lambda: put_text(f"{origin}/seed"), "预览书源",
            lambda: wait_for("two previewed sources", has_label("可导入 2 条 · 已存在 0 条")),
            lambda: screenshot("source-preview"),
            "确认导入", lambda: wait_for("confirmation dialog", has_label("确认导入书源？")),
            "确认导入", lambda: wait_for("import results", has_label("导入结果")),
            lambda: screenshot("source-imported"),
            "返回", lambda: wait_for("imported source", has_label("回环 alpha")), "返回",
            "全局搜索", lambda: put_text("ReaderM2"), "搜索", "受控回环书",
            lambda: wait_for("online detail author", has_label("测试作者")),
            lambda: screenshot("online-detail"),
            "开始阅读",
        ]
        for step in auto:
            click(step) if isinstance(step, str) else step()
        time.sleep(1)
        viewport = max((tuple(int(value) for value in re.findall(r"\d+", node["bounds"]))
                        for node in window()), key=lambda rect: (rect[2] - rect[0]) * (rect[3] - rect[1]))
        left, top, right, bottom = viewport
        adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        wait_for("quick controls", has_label("展开设置"))
        screenshot("online-reading")
        click("目录")
        wait_for("first chapter", has_label("第一章"))
        screenshot("online-directory")

        print("ANDROID_PRODUCT_NAVIGATION adb hardware input shelf=true sourceImport=2 search=true detail=true reading=true directory=true", flush=True)

        # Return to the shelf, then delete only the two sources admitted under
        # this run's unique HTTP fixture. Existing development data is untouched.
        launch_app()
        click("更多")
        click("书源管理")
        put_text(origin)
        click("批量")
        wait_for("fixture alpha", has_label("回环 alpha"))
        wait_for("fixture beta", has_label("回环 beta"))
        visible_urls = {node.get("text") for node in window() if node.get("text", "").startswith(f"{origin}/")}
        assert visible_urls == {f"{origin}/book/alpha", f"{origin}/book/beta"}, visible_urls
        click("全选结果")
        wait_for("exact fixture selection", has_label("已选 2 项"))
        click("批量操作")
        click("删除所选")
        wait_for("fixture removal confirmation", has_label("删除所选书源？"))
        click("删除")
        wait_for("fixture removal readback", has_label("没有符合筛选的书源"))
        print("ANDROID_PRODUCT_FIXTURE_CLEANUP sources=2 exactOrigin=true", flush=True)
    finally:
        if port is not None:
            adb("reverse", "--remove", f"tcp:{port}")
        adb("shell", "rm", "-f", remote_window)
        server.terminate()
        assert before == verify_running_target()
