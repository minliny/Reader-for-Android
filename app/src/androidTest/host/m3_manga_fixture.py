#!/usr/bin/env python3
"""Local HTTP inputs for the M3 manga test. No encoded media is shipped."""
import argparse
from collections import Counter
from functools import lru_cache
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import struct
from threading import Lock
import zlib


@lru_cache(maxsize=3)
def png(width, height, level):
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data))

    compressor = zlib.compressobj(level)
    blue = b"\0" + b"\x20\x4c\x9e" * width
    red = b"\0" + b"\xb0\x27\x52" * width
    body = bytearray()
    for index in range(height):
        body.extend(compressor.compress(blue if index < height // 2 else red))
    body.extend(compressor.flush())
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", body) + chunk(b"IEND", b"")


class MangaHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    requests = Counter()
    lock = Lock()
    offline = False

    def reply(self, code, content, mime="text/plain; charset=utf-8"):
        data = content.encode() if isinstance(content, str) else content
        self.send_response(code)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.close_connection = True
        self.end_headers()
        try:
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        path = self.path
        with self.lock:
            if path.startswith("/m3/manga/"):
                self.requests[path] += 1
            if path in ("/test/offline", "/test/online"):
                MangaHandler.offline = path == "/test/offline"
            offline = MangaHandler.offline
            counts = dict(self.requests)
        if path == "/test/state":
            self.reply(200, json.dumps(counts), "application/json")
        elif path in ("/ready", "/test/offline", "/test/online"):
            self.reply(200, "ok")
        elif path == "/m3/manga/toc":
            self.reply(200, "<ol><li><a href='/m3/manga/chapter'>图页章</a></li></ol>", "text/html; charset=utf-8")
        elif path == "/m3/manga/chapter":
            self.reply(200, "".join(f"<img src='/m3/manga/{name}.png'>" for name in ("small", "long", "budget")),
                       "text/html; charset=utf-8")
        elif path == "/m3/manga/small.png":
            self.reply(200, png(80, 160, 5), "image/png")
        elif path == "/m3/manga/long.png":
            self.reply(503, "controlled unavailable") if offline else self.reply(200, png(96, 22000, 0), "image/png")
        elif path == "/m3/manga/budget.png":
            self.reply(200, png(1200, 20000, 5), "image/png")
        else:
            self.reply(404, "unknown fixture path")


if __name__ == "__main__":
    arguments = argparse.ArgumentParser()
    arguments.add_argument("--port-file", required=True, type=Path)
    with ThreadingHTTPServer(("127.0.0.1", 0), MangaHandler) as server:
        port = server.server_port
        arguments.parse_args().port_file.write_text(str(port))
        print(f"M3 manga loopback on {port}", flush=True)
        server.serve_forever()
