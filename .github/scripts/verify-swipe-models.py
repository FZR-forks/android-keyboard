#!/usr/bin/env python3
"""Verify that the release packages the exact model data pinned by upstream."""

import hashlib
from pathlib import Path
import re
import subprocess
import sys
import zipfile


def verify(apk_path):
    model_repo = Path(__file__).resolve().parents[2] / "java/assets/futo-swipe"
    models = subprocess.check_output(
        ["git", "-C", str(model_repo), "ls-files", "*.pte"], text=True
    ).splitlines()
    if not models:
        raise ValueError("No tracked swipe models found")

    with zipfile.ZipFile(apk_path) as apk:
        for model in models:
            pointer = subprocess.check_output(
                ["git", "-C", str(model_repo), "show", f"HEAD:{model}"], text=True
            )
            oid = re.search(r"^oid sha256:([a-f0-9]{64})$", pointer, re.MULTILINE)
            size = re.search(r"^size (\d+)$", pointer, re.MULTILINE)
            if not oid or not size:
                raise ValueError(f"Missing LFS metadata for {model}")

            asset = f"assets/futo-swipe/{model}"
            data = apk.read(asset)
            if len(data) != int(size[1]) or hashlib.sha256(data).hexdigest() != oid[1]:
                raise ValueError(f"Packaged model does not match pinned LFS data: {asset}")
            print(f"PASS: {asset} ({len(data)} bytes, SHA-256 {oid[1]})")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(f"Usage: {sys.argv[0]} APK")
    verify(sys.argv[1])
