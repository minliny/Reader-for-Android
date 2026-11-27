#!/usr/bin/env python3
"""Serve deterministic synthetic WAV audio on an ephemeral, TLS-only loopback port."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import io
import math
from pathlib import Path
import ssl
import struct
from urllib.parse import parse_qs, urlsplit
import wave


def audio_fixture():
    destination = io.BytesIO()
    with wave.open(destination, "wb") as audio:
        audio.setparams((1, 2, 16000, 0, "NONE", "not compressed"))
        audio.writeframes(b"".join(
            struct.pack("<h", round(1500 * math.sin(2 * math.pi * 270 * i / 16000))) for i in range(64000)
        ))
    return destination.getvalue()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port-file", type=Path, required=True)
    parser.add_argument("--certificate", type=Path, required=True)
    parser.add_argument("--key", type=Path, required=True)
    args = parser.parse_args()
    wave_data = audio_fixture()

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def do_GET(self):
            target = urlsplit(self.path)
            values = parse_qs(target.query)
            if target.path == "/ready":
                self.send(200, b"ready", "text/plain")
            elif target.path == "/wave3.wav" and values.get("q") == ["Audio fixture."] and values.get("rate") == ["1.25"]:
                self.send(200, wave_data, "audio/wav")
            else:
                self.send(404, b"Unexpected fixture request", "text/plain")

        def send(self, status, body, mime):
            self.send_response(status)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)
            self.close_connection = True

    tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    tls.load_cert_chain(args.certificate, args.key)
    with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
        server.socket = tls.wrap_socket(server.socket, server_side=True)
        args.port_file.write_text(str(server.server_port))
        print(f"TLS loopback listening: {server.server_port}", flush=True)
        server.serve_forever()


if __name__ == "__main__":
    main()
