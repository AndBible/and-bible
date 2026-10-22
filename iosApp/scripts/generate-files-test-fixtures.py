#!/usr/bin/env python3
"""Generate deterministic Photos, SWORD, EPUB, invalid-ZIP and H2 feature fixtures for iOS file tests."""
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
    h2_fixtures(output, modules)


def _conf(modules, name):
    return (modules / "mods.d" / name).read_text(encoding="utf-8")


def h2_fixtures(output, modules):
    """H2 feature-path modules built from the K4 jsword fixtures.

    h2-cipher.zip   H2Cipher  : K4Cipher bytes (enciphered with key k4-Test-key), locked (empty CipherKey).
    h2-genbook.zip  K4GenBook : unchanged RawGenBook (keys /Part 1/Chapter 1, /Part 1/Chapter 2, /Part 2).
    h2-maps.zip     H2Maps    : the same RawGenBook data under its own prefix, Category=Maps.
    h2-dict.zip     K4ZLD     : unchanged zLD dictionary (AARON, ABBA, ZION).
    """
    cipher = _conf(modules, "K4Cipher.conf")
    assert "[K4Cipher]\n" in cipher and "DataPath=./modules/K4Cipher\n" in cipher and "CipherKey=k4-Test-key" in cipher
    cipher = (cipher.replace("[K4Cipher]", "[H2Cipher]")
              .replace("DataPath=./modules/K4Cipher", "DataPath=./modules/texts/ztext/h2cipher")
              .replace("CipherKey=k4-Test-key", "CipherKey="))
    names = [f"{testament}.{ext}" for testament in ("ot", "nt") for ext in ("bzs", "bzv", "bzz")]
    archive(output / "h2-cipher.zip", [("mods.d/H2Cipher.conf", cipher)] + [
        (f"modules/texts/ztext/h2cipher/{n}", (modules / "modules/K4Cipher" / n).read_bytes()) for n in names])

    gen_files = [f"K4GenBook.{ext}" for ext in ("idx", "dat", "bdt")]
    archive(output / "h2-genbook.zip", [("mods.d/K4GenBook.conf", (modules / "mods.d/K4GenBook.conf").read_bytes())] + [
        (f"modules/K4GenBook/{n}", (modules / "modules/K4GenBook" / n).read_bytes()) for n in gen_files])

    maps = _conf(modules, "K4GenBook.conf")
    assert "[K4GenBook]" in maps and "DataPath=./modules/K4GenBook/K4GenBook" in maps
    maps = (maps.replace("[K4GenBook]", "[H2Maps]").replace("DataPath=./modules/K4GenBook/K4GenBook", "DataPath=./modules/H2Maps/H2Maps")
            .rstrip("\n") + "\nCategory=Maps\n")
    archive(output / "h2-maps.zip", [("mods.d/H2Maps.conf", maps)] + [
        (f"modules/H2Maps/H2Maps.{n.split('.')[1]}", (modules / "modules/K4GenBook" / n).read_bytes()) for n in gen_files])

    dict_files = [f"K4ZLD.{ext}" for ext in ("zdx", "zdt", "idx", "dat")]
    archive(output / "h2-dict.zip", [("mods.d/K4ZLD.conf", (modules / "mods.d/K4ZLD.conf").read_bytes())] + [
        (f"modules/K4ZLD/{n}", (modules / "modules/K4ZLD" / n).read_bytes()) for n in dict_files])


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
