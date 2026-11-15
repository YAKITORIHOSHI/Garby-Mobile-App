#!/usr/bin/env python3
"""Print the SHA-256 of every signer certificate in an APK's v2/v3 signature blocks.

Standard library only, so the release workflow checks who signed the APK without depending
on the text format of apksigner, which changes between build-tools versions. apksigner still
verifies the signatures themselves.

usage: python3 tools/apk_signer_cert.py APP.apk    -> one "<scheme> <sha256>" line per signer
"""
import hashlib
import struct
import sys

SCHEMES = {0x7109871A: "v2", 0xF05368C0: "v3", 0x1B93AD61: "v3.1"}


def u32(data, offset):
    return struct.unpack_from("<I", data, offset)[0]


def u64(data, offset):
    return struct.unpack_from("<Q", data, offset)[0]


def prefixed(data, offset):
    """The uint32-length-prefixed item at offset, and the offset after it."""
    length = u32(data, offset)
    if offset + 4 + length > len(data):
        raise ValueError("length prefix runs past the end")
    return data[offset + 4:offset + 4 + length], offset + 4 + length


def sequence(data):
    items, offset = [], 0
    while offset < len(data):
        item, offset = prefixed(data, offset)
        items.append(item)
    return items


def signer_certificates(data):
    """[(scheme, sha256 hex of the signer's first certificate)] for every v2/v3 signer."""
    for eocd in range(len(data) - 22, max(-1, len(data) - 22 - 65536), -1):
        if u32(data, eocd) == 0x06054B50:
            break
    else:
        raise ValueError("not a ZIP file")
    central_directory = u32(data, eocd + 16)
    if data[central_directory - 16:central_directory] != b"APK Sig Block 42":
        raise ValueError("no APK signing block")
    size = u64(data, central_directory - 24)
    offset = central_directory - size        # first ID-value pair, after the leading size
    found = []
    while offset < central_directory - 24:
        length, block_id = u64(data, offset), u32(data, offset + 8)
        value = data[offset + 12:offset + 8 + length]
        offset += 8 + length
        if block_id not in SCHEMES:
            continue
        for signer in sequence(prefixed(value, 0)[0]):
            signed_data, _ = prefixed(signer, 0)
            _digests, after = prefixed(signed_data, 0)
            certificates, _ = prefixed(signed_data, after)
            first = sequence(certificates)[0]
            found.append((SCHEMES[block_id], hashlib.sha256(first).hexdigest()))
    if not found:
        raise ValueError("no v2/v3 signer")
    return found


if __name__ == "__main__":
    try:
        for scheme, digest in signer_certificates(open(sys.argv[1], "rb").read()):
            print(scheme, digest)
    except (ValueError, IndexError, struct.error) as error:
        sys.exit(f"apk_signer_cert: {error}")
