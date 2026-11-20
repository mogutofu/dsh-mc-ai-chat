#!/usr/bin/env python3
"""Tiny RCON client used by the NeoForge 1.21.1 smoke test.

Sends a few console commands to the dev server and prints their responses.
No third-party dependency: only socket + struct.
"""
import socket
import struct
import sys

HOST = "127.0.0.1"
PORT = 25575
PASSWORD = "mcai-smoke"
COMMANDS = ["weather", "ai", "vote status"]


def pack_packet(request_id, packet_type, body):
    payload = struct.pack("<ii", request_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(payload)) + payload


def read_packet(sock):
    header = b""
    while len(header) < 4:
        chunk = sock.recv(4 - len(header))
        if not chunk:
            raise EOFError("connection closed")
        header += chunk
    length = struct.unpack("<i", header)[0]
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise EOFError("connection closed")
        data += chunk
    request_id, _packet_type = struct.unpack("<ii", data[:8])
    body = data[8:-2].decode("utf-8", "replace")
    return request_id, body


def main():
    sock = socket.create_connection((HOST, PORT), timeout=15)
    sock.sendall(pack_packet(1, 3, PASSWORD))
    request_id, _body = read_packet(sock)
    if request_id == -1:
        print("RCON authentication failed")
        return 1

    for index, command in enumerate(COMMANDS, start=2):
        sock.sendall(pack_packet(index, 2, command))
        _request_id, body = read_packet(sock)
        print(">>> " + command)
        print(body)
        print("<<< end")

    sock.sendall(pack_packet(99, 2, "stop"))
    try:
        read_packet(sock)
    except (EOFError, socket.timeout, OSError):
        pass
    sock.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
