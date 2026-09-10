"""Check actual ELF version requirements, including nested packaged native JARs.

The managed Amazon Linux 2023 runtime provides glibc 2.34. A successful native
test on a newer Ubuntu runner does not establish compatibility with that runtime.
"""
import io
from pathlib import Path
import re
import struct
import sys
import zipfile


def required_versions(data):
    assert data[:6] == b"\x7fELF\x02\x01", "Expected ELF64 little-endian native library"
    offset = struct.unpack_from("<Q", data, 40)[0]
    stride, count = struct.unpack_from("<HH", data, 58)
    sections = [struct.unpack_from("<IIQQQQIIQQ", data, offset + i * stride)
                for i in range(count)]
    for section in sections:
        if section[1] != 0x6FFFFFFE:  # SHT_GNU_verneed, not version definitions
            continue
        strings = sections[section[6]]
        names = data[strings[4]:strings[4] + strings[5]]
        position, end = section[4], section[4] + section[5]
        while position < end:
            _, auxiliaries, _, first, next_need = struct.unpack_from("<HHIII", data, position)
            auxiliary = position + first
            for _ in range(auxiliaries):
                _, _, _, name, next_aux = struct.unpack_from("<IHHII", data, auxiliary)
                yield names[name:names.index(b"\0", name)].decode("ascii")
                auxiliary += next_aux
            if not next_need:
                break
            position += next_need


def native_libraries(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            if name.endswith("-linux-x86_64.jar"):
                yield from native_libraries(archive.read(name))
            elif "/linux-x86_64/" in name and ".so" in name:
                content = archive.read(name)
                if content.startswith(b"\x7fELF"):
                    yield name, content


def check(path):
    checked = 0
    for name, content in native_libraries(path.read_bytes()):
        checked += 1
        for version in required_versions(content):
            match = re.fullmatch(r"GLIBC_(\d+)\.(\d+)(?:\.(\d+))?", version)
            if match:
                required = tuple(int(value or 0) for value in match.groups())
                assert required <= (2, 34, 0), f"{name} requires {version}; runtime has GLIBC_2.34"
    assert checked, "No Linux x86_64 native libraries checked"
    print(f"Verified GLIBC <= 2.34 requirements in {checked} native libraries")


if __name__ == "__main__":
    check(Path(sys.argv[1]) if len(sys.argv) > 1 else Path("target/reference-api.jar"))
