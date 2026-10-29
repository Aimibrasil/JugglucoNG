#!/usr/bin/env python3
"""Run the native rendezvous failover tests (C++20 compiler required)."""

import os
from pathlib import Path
import subprocess
import tempfile


def main():
    root = Path(__file__).resolve().parents[1]
    with tempfile.TemporaryDirectory(prefix="juggluco-rendezvous-") as temporary:
        executable = Path(temporary) / "rendezvous-failover-tests"
        subprocess.run([
            os.environ.get("CXX", "c++"), "-std=c++20", "-O1", "-g", "-pthread",
            "-Wall", "-Werror", f"-I{root / 'Common/src/main/cpp'}",
            str(root / "Common/src/test/cpp/RendezvousFailoverTests.cpp"),
            "-o", str(executable),
        ], check=True)
        return subprocess.run([str(executable)], timeout=60).returncode


if __name__ == "__main__":
    raise SystemExit(main())
