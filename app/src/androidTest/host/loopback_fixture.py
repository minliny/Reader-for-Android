#!/usr/bin/env python3
"""Test-only HTTP endpoints for the Android instrumentation runner."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import threading
from urllib.parse import urlsplit


TITLE = "受控回环书"
AUTHOR = "测试作者"
TEXT = "墨纸青松，受控回环正文。中文𠀀与Café都在这里。用于校验在线阅读锚点与换源、脱机缓存。"
BOOK = re.compile(r"/book/(alpha|beta|rules|extra)/(search|detail|toc|chapter/[1-4]|login)")
write_lock = threading.Lock()


def rule(origin, name):
    url = f"{origin}/book/{name}"
    return {
        "bookSourceName": f"回环 {name}",
        "bookSourceUrl": url,
        "bookSourceGroup": "Android 真机回环",
        "bookSourceType": 0,
        "loginUrl": f"{url}/login",
        "enabled": True,
        "searchUrl": f"{url}/search?q={{{{key}}}}",
        "ruleSearch": {
            "bookList": "ul.results li", "name": "span.name@text",
            "author": "span.author@text", "bookUrl": "a.detail@href"
        },
        "ruleBookInfo": {"name": "h1@text", "author": "p.author@text", "tocUrl": "a.toc@href"},
        "ruleToc": {"chapterList": "ol.toc li", "chapterName": "a@text", "chapterUrl": "a@href"},
        "ruleContent": {"content": "article.content@text"},
    }


def make_handler(control):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"
        origin = property(lambda self: f"http://127.0.0.1:{self.server.server_port}")

        def send(self, status, value, content_type="text/plain; charset=utf-8", headers=()):
            data = value.encode("utf-8") if isinstance(value, str) else value
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            self.close_connection = True
            for key, field in headers:
                self.send_header(key, field)
            self.end_headers()
            try:
                self.wfile.write(data)
            except (BrokenPipeError, ConnectionResetError):
                pass

        def json(self, value):
            self.send(200, json.dumps(value, ensure_ascii=False), "application/json; charset=utf-8")

        def do_POST(self):
            if self.path != "/echo":
                self.send(404, "unknown POST")
                return
            self.send(200, self.rfile.read(int(self.headers["Content-Length"])).hex())

        def do_GET(self):
            path = urlsplit(self.path).path
            if path == "/ready":
                self.send(200, "live")
            elif path == "/cookies":
                self.send(200, self.headers.get("Cookie", ""))
            elif path == "/503":
                self.send(503, "controlled unavailable")
            elif path == "/slow":
                self.gated_response("released")
            elif path == "/seed":
                self.json([rule(self.origin, name) for name in ("alpha", "beta")])
            elif path == "/rules/v1":
                self.json([rule(self.origin, "rules")])
            elif path == "/rules/v2":
                self.json([rule(self.origin, name) for name in ("rules", "extra")])
            elif path == "/media":
                self.media()
            elif path.startswith("/test/"):
                self.control(path)
            else:
                self.book(path)

        def control(self, path):
            if path == "/test/offline":
                control["offline"] = True
            elif path == "/test/online":
                control["offline"] = False
            elif path in ("/test/hold", "/test/hold-search"):
                control["hold_search"] = path.endswith("-search")
                control["seen"].clear()
                control["release"].clear()
            elif path == "/test/release":
                control["release"].set()
            elif path == "/test/seen":
                self.send(200, "yes" if control["seen"].is_set() else "no")
                return
            else:
                self.send(404, "unknown control")
                return
            self.send(200, "ok")

        def gated_response(self, text):
            if self.wait_for_release():
                self.send(200, text)
            else:
                self.send(504, "fixture release timeout")

        def wait_for_release(self):
            control["seen"].set()
            return control["release"].wait(35)

        def media(self):
            match = re.fullmatch(r"bytes=(\d+)-(\d+)", self.headers.get("Range", ""))
            size = 192 * 1024
            if not match:
                self.send(400, "fixture only serves explicit ranges")
                return
            first, last = (int(part) for part in match.groups())
            if first >= size or last < first:
                self.send(416, "invalid range")
                return
            last = min(last, size - 1)
            headers = (("Content-Range", f"bytes {first}-{last}/{size}"),)
            self.send(206, bytes(i % 251 for i in range(first, last + 1)), "application/octet-stream", headers)

        def book(self, path):
            match = BOOK.fullmatch(path)
            if not match:
                self.send(404, "unknown page")
                return
            name, route = match.groups()
            if route.startswith("chapter/") and control["offline"]:
                self.send(503, "controlled offline chapter")
                return
            needs_gate = route == "chapter/4" or (route == "search" and control["hold_search"])
            if needs_gate and not control["release"].is_set() and not self.wait_for_release():
                self.send(504, "fixture release timeout")
                return
            base = f"{self.origin}/book/{name}"
            headers = ()
            if route == "search":
                body = (f'<ul class="results"><li><span class="name">{TITLE}</span>'
                        f'<span class="author">{AUTHOR}</span><a class="detail" href="{base}/detail">详情</a></li></ul>')
            elif route == "detail":
                body = f'<h1>{TITLE}</h1><p class="author">{AUTHOR}</p><a class="toc" href="{base}/toc">目录</a>'
            elif route == "toc":
                entries = "".join(f'<li><a href="{base}/chapter/{i}">第{n}章</a></li>'
                                  for i, n in enumerate("一二三四", start=1))
                body = f'<ol class="toc">{entries}</ol>'
            elif route == "login":
                headers = (("Set-Cookie", f"browser_{name}=fromWeb; Path=/"),)
                body = "<title>Reader 回环登录</title><main>受控登录回执</main>"
            else:
                body = f'<article class="content">{TEXT} 章 {route[-1]}</article>'
            self.send(200, '<!doctype html><meta charset="utf-8">' + body, "text/html; charset=utf-8", headers)

        def log_message(self, template, *fields):
            with write_lock:
                print(template % fields, flush=True)

    return Handler


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port-file", type=Path, required=True)
    args = parser.parse_args()
    control = {"offline": False, "hold_search": False, "seen": threading.Event(), "release": threading.Event()}
    control["release"].set()
    with ThreadingHTTPServer(("127.0.0.1", 0), make_handler(control)) as server:
        args.port_file.write_text(f"{server.server_port}\n", encoding="ascii")
        print(f"fixture ready on loopback port {server.server_port}", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
