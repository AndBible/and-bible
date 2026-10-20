#!/usr/bin/env python3
"""Generate deterministic Photos, SWORD, EPUB and invalid-ZIP fixtures for iOS file tests."""
import argparse
import pathlib
import struct
import zlib
import zipfile


def archive(path, entries):
    with zipfile.ZipFile(path, "w") as output:
        for name, data in entries:
            info = zipfile.ZipInfo(name, (2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_STORED
            info.external_attr = 0o100644 << 16
            output.writestr(info, data.encode("utf-8") if isinstance(data, str) else data)


def incoming_fixtures(output):
    root = pathlib.Path(__file__).resolve().parents[2]
    modules = root / "jsword/src/commonTest/resources/k4-modules/space dir/ä"
    names = ["mods.d/K4RawText.conf"] + [f"modules/K4RawText/{name}" for name in ("ot", "ot.vss", "nt", "nt.vss")]
    archive(output / "f-rawtext.zip", [(name, (modules / name).read_bytes()) for name in names])
    archive(output / "f-runtime.epub", [
        ("mimetype", "application/epub+zip"),
        ("META-INF/container.xml", '<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>'),
        ("content.opf", '<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">f-runtime-fixed</dc:identifier><dc:title>F Runtime Fixture</dc:title><dc:creator>AndBible tests</dc:creator><dc:language>en</dc:language></metadata><manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/><item id="one" href="ch1.xhtml" media-type="application/xhtml+xml"/><item id="two" href="ch2.xhtml" media-type="application/xhtml+xml"/></manifest><spine toc="ncx"><itemref idref="one"/><itemref idref="two"/></spine></package>'),
        ("toc.ncx", '<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head/><docTitle><text>F Runtime Fixture</text></docTitle><navMap><navPoint id="one" playOrder="1"><navLabel><text>One</text></navLabel><content src="ch1.xhtml"/></navPoint><navPoint id="two" playOrder="2"><navLabel><text>Two</text></navLabel><content src="ch2.xhtml"/></navPoint></navMap></ncx>'),
        ("ch1.xhtml", '<html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title></head><body><p>F runtime chapter one proof.</p></body></html>'),
        ("ch2.xhtml", '<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Two</title></head><body><p>F runtime chapter two proof.</p></body></html>'),
    ])
    (output / "f-invalid.zip").write_bytes(bytes((0x50, 0x4B, 3, 4, 0)))


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=pathlib.Path, required=True)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    colors = ((255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 0))
    rows = b"".join(b"\0" + b"".join(bytes(colors[(y // 8) * 2 + x // 8]) for x in range(16)) for y in range(16))
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b"")
    (args.output_dir / "andbible-port-quadrants.png").write_bytes(png)
    incoming_fixtures(args.output_dir)


if __name__ == "__main__":
    main()
