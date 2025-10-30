#!/usr/bin/env python3
import os
from pathlib import Path
import re
import subprocess
import sys

# run from ~/oboe/samples/minimaloboe directory
def symbolicate(stack, abi):
    android_home = os.getenv("ANDROID_HOME")
    ndk_version = "27.0.12077973" # default for AGP used in :minimaloboe app

    if android_home is None:
        print("ANDROID_HOME not set")
        sys.exit(1)

    llvm_symbolizer = (Path(android_home) / "ndk" / ndk_version / "toolchains" / "llvm" / "prebuilt"
        / "linux-x86_64" / "bin" / "llvm-symbolizer")

    sym_dir = (Path.cwd()  / "build" / "intermediates" / "merged_native_libs" / "debug"
        / f"mergeDebugNativeLibs" / "out" / "lib" / abi)

    if not stack.is_file():
        print(f"stack.txt not found: {stack}")
        sys.exit(1)
    if not llvm_symbolizer.exists():
        print(f"llvm_symbolizer not found: {llvm_symbolizer}")
        sys.exit(1)
    else:
        print(f"Using llvm_symbolizer: {llvm_symbolizer}", file=sys.stderr)
    if not sym_dir.is_dir():
        print(f"sym_dir not found: {sym_dir}")
        sys.exit(1)
    else:
        print(f"Using symbol directory: {sym_dir}", file=sys.stderr)

    with open(stack, "r") as f:
        lines = f.read().splitlines()
    pattern = re.compile(r"(lib[^\/+]+\.so)\+(0x[A-Fa-f0-9]+)")

    for line in lines:
        match = pattern.search(line)
        if match:
            lib_name, address = match.groups()
            lib_path = sym_dir / lib_name
            if lib_path.is_file():
                args = [str(llvm_symbolizer), f"--obj={lib_path}", address]
                try:
                    result = subprocess.run(args, capture_output=True, text=True, check=True).stdout
                    for out_line in result.rstrip("\n").splitlines():
                        print(f"    {out_line}")
                except subprocess.CalledProcessError:
                    print(f"{line} [Desymbolication failed]")
            else:
                print(f"{line} [No symbols available]")
            print()
        else:
            print(line)

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python symbolicate.py <stack.txt> <abi>")
        sys.exit(1)

    stack, abi = sys.argv[1:]
    symbolicate(Path(stack), abi)
