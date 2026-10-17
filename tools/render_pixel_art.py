#!/usr/bin/env python3
"""Render small Minecraft-style PNG textures from a JSON shape description.

Usage: python3 tools/render_pixel_art.py design.json output.png
"""

import json
import struct
import sys
import zlib
from pathlib import Path


def rgba(value):
    if isinstance(value, str):
        text = value.lstrip("#")
        if len(text) == 6:
            text += "ff"
        if len(text) != 8:
            raise ValueError(f"Invalid color: {value}")
        return tuple(int(text[index:index + 2], 16) for index in range(0, 8, 2))
    if isinstance(value, list) and len(value) in {3, 4}:
        return tuple(max(0, min(255, int(channel))) for channel in (value + [255])[:4])
    raise ValueError(f"Invalid color: {value}")


def blend(bottom, top):
    alpha = top[3] / 255
    inverse = 1 - alpha
    out_alpha = alpha + bottom[3] / 255 * inverse
    if out_alpha <= 0:
        return (0, 0, 0, 0)
    return tuple(
        round((top[index] * alpha + bottom[index] * bottom[3] / 255 * inverse) / out_alpha)
        for index in range(3)
    ) + (round(out_alpha * 255),)


def render(description):
    size = int(description.get("size", 16))
    if size < 8 or size > 64:
        raise ValueError("Texture size must be between 8 and 64")
    background = rgba(description.get("background", "#00000000"))
    pixels = [[background for _ in range(size)] for _ in range(size)]

    def paint(x, y, color):
        if 0 <= x < size and 0 <= y < size:
            pixels[y][x] = blend(pixels[y][x], color)

    def line(x0, y0, x1, y1, color, width=1):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        error = dx + dy
        while True:
            radius = max(0, width // 2)
            for oy in range(-radius, radius + 1):
                for ox in range(-radius, radius + 1):
                    paint(x0 + ox, y0 + oy, color)
            if x0 == x1 and y0 == y1:
                break
            doubled = 2 * error
            if doubled >= dy:
                error += dy
                x0 += sx
            if doubled <= dx:
                error += dx
                y0 += sy

    def polygon(points, color):
        minimum = max(0, min(point[1] for point in points))
        maximum = min(size - 1, max(point[1] for point in points))
        for y in range(minimum, maximum + 1):
            crossings = []
            for index, first in enumerate(points):
                second = points[(index + 1) % len(points)]
                if (first[1] <= y < second[1]) or (second[1] <= y < first[1]):
                    crossings.append(round(first[0] + (y - first[1]) * (second[0] - first[0]) / (second[1] - first[1])))
            crossings.sort()
            for index in range(0, len(crossings) - 1, 2):
                for x in range(crossings[index], crossings[index + 1] + 1):
                    paint(x, y, color)

    for shape in description.get("shapes", []):
        kind = shape.get("type")
        color = rgba(shape.get("color", "#ffffffff"))
        if kind == "pixel":
            paint(int(shape["x"]), int(shape["y"]), color)
        elif kind == "rect":
            for y in range(int(shape["y"]), int(shape["y"]) + int(shape["h"])):
                for x in range(int(shape["x"]), int(shape["x"]) + int(shape["w"])):
                    paint(x, y, color)
        elif kind == "ellipse":
            x, y, width, height = (int(shape[key]) for key in ("x", "y", "w", "h"))
            center_x, center_y = x + (width - 1) / 2, y + (height - 1) / 2
            radius_x, radius_y = max(width / 2, 0.5), max(height / 2, 0.5)
            for py in range(y, y + height):
                for px in range(x, x + width):
                    if ((px - center_x) / radius_x) ** 2 + ((py - center_y) / radius_y) ** 2 <= 1:
                        paint(px, py, color)
        elif kind == "line":
            line(int(shape["x1"]), int(shape["y1"]), int(shape["x2"]), int(shape["y2"]), color, int(shape.get("width", 1)))
        elif kind == "polygon":
            points = [[int(value) for value in point] for point in shape["points"]]
            if len(points) >= 3:
                polygon(points, color)
        else:
            raise ValueError(f"Unsupported shape type: {kind}")
    return size, pixels


def write_png(path, size, pixels):
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: render_pixel_art.py design.json output.png")
    description = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    size, pixels = render(description)
    write_png(Path(sys.argv[2]), size, pixels)


if __name__ == "__main__":
    main()
