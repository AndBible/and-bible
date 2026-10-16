#!/usr/bin/env python3
"""Check an unscaled screencap of the settled light pure-MONO reader.

Capture/setup are deliberately external: the controller must verify the production
reader is foreground, search is closed, no keyboard/OS overlay is visible, and
paper is white with noise disabled. This checks pixels, not that setup contract.
Uses only the standard library; supports Android screencap's 8-bit RGB/RGBA PNGs.
"""

import argparse
import struct
import zlib
from collections import Counter
from pathlib import Path


def read_png(path):
    data = Path(path).read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("Not a PNG")
    offset, compressed, header = 8, bytearray(), None
    while offset < len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        if zlib.crc32(kind + payload) & 0xffffffff != crc:
            raise ValueError("PNG chunk CRC mismatch")
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            break
        offset += length + 12
    if header is None:
        raise ValueError("Missing PNG header")
    width, height, depth, color, compression, filtering, interlace = header
    if depth != 8 or color not in (2, 6) or any((compression, filtering, interlace)):
        raise ValueError("Requires noninterlaced 8-bit RGB/RGBA PNG")
    channels = 3 if color == 2 else 4
    stride = width * channels
    raw = zlib.decompress(compressed)
    if len(raw) != height * (stride + 1):
        raise ValueError("Unexpected decoded PNG size")
    previous = bytearray(stride)
    rows = []
    for y in range(height):
        start = y * (stride + 1)
        mode = raw[start]
        row = bytearray(raw[start + 1:start + 1 + stride])
        if mode not in range(5):
            raise ValueError("Unknown PNG row filter")
        for i in range(stride):
            left = row[i - channels] if i >= channels else 0
            up = previous[i]
            upper_left = previous[i - channels] if i >= channels else 0
            if mode == 1:
                predictor = left
            elif mode == 2:
                predictor = up
            elif mode == 3:
                predictor = (left + up) // 2
            elif mode == 4:
                p = left + up - upper_left
                distances = (abs(p - left), abs(p - up), abs(p - upper_left))
                predictor = (left, up, upper_left)[distances.index(min(distances))]
            else:
                predictor = 0
            row[i] = (row[i] + predictor) & 255
        rows.append([tuple(row[i:i + channels]) for i in range(0, stride, channels)])
        previous = row
    return width, height, rows


def check(path, width=1440, height=3120):
    actual_width, actual_height, rows = read_png(path)
    if (actual_width, actual_height) != (width, height):
        raise ValueError(f"Wrong capture dimensions: {actual_width} x {actual_height}; "
                         f"expected {width} x {height}")
    failures = []
    for y in range(height - 5, height):
        nonwhite = [x for x, pixel in enumerate(rows[y])
                    if pixel[:3] != (255, 255, 255) or (len(pixel) == 4 and pixel[3] != 255)]
        if nonwhite:
            colors = Counter(rows[y][x] for x in nonwhite)
            failures.append(f"y={y}: {len(nonwhite)} non-white pixels, "
                            f"x={nonwhite[0]}..{nonwhite[-1]}, colors={dict(colors)}")
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("png", type=Path)
    args = parser.parse_args()
    try:
        failures = check(args.png)
    except (ValueError, OSError, struct.error, zlib.error) as error:
        print(f"INVALID CAPTURE: {error}")
        return 2
    if failures:
        print("FAIL: settled light pure-MONO reader bottom edge is not opaque white")
        print("\n".join(failures))
        return 1
    print("PASS: all 7200 pixels in the final five rows are opaque white (1440 x 3120)")
    print("Capture setup/foreground reader identity require independent controller verification.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
