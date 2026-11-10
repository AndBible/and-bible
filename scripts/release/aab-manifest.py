#!/usr/bin/env python3
"""Print one attribute of the <manifest> element of an Android App Bundle (proto-format base/manifest/AndroidManifest.xml).

Usage: aab-manifest.py <bundle.aab> <attribute>   e.g. versionCode, versionName, package

The bundletool jar in the Gradle cache is not runnable on its own (no main manifest, dependencies elsewhere), so this
reads the aapt2 Resources.proto XmlNode directly: XmlNode.element(1) -> XmlElement.attribute(4) -> XmlAttribute
name(2) / value(3). Only the top element's own attributes are read, never a child's. Exit 1 if the attribute is absent.
"""
import sys
import zipfile


def fields(buf):
    """Yield (field number, wire type, value) for one protobuf message; length-delimited values are bytes."""
    i = 0
    while i < len(buf):
        key, i = varint(buf, i)
        num, wt = key >> 3, key & 7
        if wt == 0:
            v, i = varint(buf, i)
        elif wt == 1:
            v, i = buf[i:i + 8], i + 8
        elif wt == 2:
            n, i = varint(buf, i)
            v, i = buf[i:i + n], i + n
        elif wt == 5:
            v, i = buf[i:i + 4], i + 4
        else:
            raise ValueError("unsupported wire type %d" % wt)
        yield num, wt, v


def varint(buf, i):
    shift = result = 0
    while True:
        b = buf[i]
        i += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, i
        shift += 7


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    path, want = sys.argv[1], sys.argv[2].encode()
    with zipfile.ZipFile(path) as z:
        node = z.read("base/manifest/AndroidManifest.xml")
    element = next(v for n, wt, v in fields(node) if n == 1 and wt == 2)
    for n, wt, v in fields(element):
        if n == 4 and wt == 2:
            attr = {an: av for an, awt, av in fields(v) if awt == 2}
            if attr.get(2) == want and 3 in attr:
                print(attr[3].decode())
                return
    sys.exit(1)


if __name__ == "__main__":
    main()
