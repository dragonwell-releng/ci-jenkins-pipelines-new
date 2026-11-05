# syntax=docker/dockerfile:1.7
# ********************************************************************************
# Copyright (c) 2026 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made
# available under the terms of the Apache Software License 2.0
# which is available at https://www.apache.org/licenses/LICENSE-2.0.
#
# SPDX-License-Identifier: Apache-2.0
# ********************************************************************************

ARG base_image=ubuntu:24.04
ARG qemu_image=docker.io/tonistiigi/binfmt:qemu-v10.2.3-68@sha256:400a4873b838d1b89194d982c45e5fb3cda4593fbfd7e08a02e76b03b21166f0

FROM ${qemu_image} AS qemu

FROM ${base_image} AS host
ARG apt_mirror=http://archive.ubuntu.com/ubuntu
RUN sed -i "s|http://archive.ubuntu.com/ubuntu|${apt_mirror}|g; s|http://security.ubuntu.com/ubuntu|${apt_mirror}|g" \
        /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        autoconf automake bison build-essential bzip2 ca-certificates ccache cpio curl file flex \
        gcc-14 g++-14 git libasound2-dev libcups2-dev libfontconfig1-dev libfreetype-dev \
        libgmp-dev libisl-dev libmpc-dev libmpfr-dev libx11-dev libxext-dev libxrandr-dev \
        libxrender-dev libxt-dev libxtst-dev patch pkg-config python3 rpm texinfo unzip xorriso \
        xz-utils zip zlib1g-dev libzstd-dev \
    && rm -rf /var/lib/apt/lists/*
ENV CC=gcc-14 CXX=g++-14

FROM host AS toolchain
ARG jobs=32
ARG iso_url=https://mirrors.openanolis.cn/anolis/23.5/isos/GA/riscv64/AnolisOS-23.5-riscv64-dvd.iso
ARG binutils_url=https://mirrors.openanolis.cn/anolis/23.5/os/source/Packages/binutils-2.42-4.an23_rva23.src.rpm
ARG gcc_url=https://mirrors.openanolis.cn/anolis/23.5/os/source/Packages/gcc-14.3.0-5.an23_rva23.src.rpm
ARG bootjdk_url=https://download.java.net/java/GA/jdk25.0.2/b1e0dfa218384cb9959bdcb897162d4e/10/GPL/openjdk-25.0.2_linux-x64_bin.tar.gz

RUN mkdir -p /inputs \
    && curl -fL --retry 4 --connect-timeout 30 -o /inputs/AnolisOS-23.5-riscv64-dvd.iso "${iso_url}" \
    && curl -fL --retry 4 --connect-timeout 30 -o /inputs/binutils-2.42-4.an23_rva23.src.rpm "${binutils_url}" \
    && curl -fL --retry 4 --connect-timeout 30 -o /inputs/gcc-14.3.0-5.an23_rva23.src.rpm "${gcc_url}" \
    && curl -fL --retry 4 --connect-timeout 30 -o /inputs/bootjdk.tar.gz "${bootjdk_url}" \
    && cd /inputs \
    && printf '%s  %s\n' \
        e07d07ad7ced46a8e3af50bc0b13618b654a626f5dee25675aa3ea9f202c542f AnolisOS-23.5-riscv64-dvd.iso \
        455bd0f47f420243c77d26d32baab1c852b83064aa5f78cceb970559f74fd0a1 binutils-2.42-4.an23_rva23.src.rpm \
        2d47d4307b5cdc74ffa336f70ad5e36ea7ac2eadc930307a69a6afee5141d849 gcc-14.3.0-5.an23_rva23.src.rpm \
        555ce0821e4fe175ea50d54518cd6fbece9663c1998de529bc6ce429534457df bootjdk.tar.gz \
        > SHA256SUMS \
    && sha256sum -c SHA256SUMS

COPY <<'PY' /scripts/prepare-sysroot.py
#!/usr/bin/env python3
import os
import pathlib
import re
import subprocess
import sys
import tempfile

iso, destination = sys.argv[1:]
root = pathlib.Path(destination).resolve()
root.mkdir(parents=True, exist_ok=True)
listing = subprocess.check_output([
    "xorriso", "-indev", iso, "-find", "/os/Packages", "-name", "*.rpm", "-exec", "lsdl"
], text=True)
packages = re.findall(r"'(/os/Packages/[^']+\.rpm)'", listing)
families = (
    "glibc", "kernel-headers", "libgcc", "libstdc++", "libatomic", "alsa-lib", "cups-libs",
    "cups-devel", "fontconfig", "freetype", "libX", "libxcb", "libICE", "libSM", "libpng",
    "libjpeg-turbo", "zlib", "libxcrypt", "xorg-x11-proto", "xorg-x11-util", "expat",
    "bzip2-libs", "brotli", "harfbuzz", "libuuid", "libbsd", "libmd", "anolis-release",
    "filesystem", "setup", "tzdata",
)
selected = sorted(
    package for package in packages
    if pathlib.PurePosixPath(package).name.startswith(families)
    and not any(part in package for part in (
        "-debuginfo-", "-debugsource-", "-docs-", "-doc-", "-benchtests-",
        "-locale-source-", "-all-langpacks-",
    ))
)
assert any("gcc-14.3.0-5.an23_rva23" in package for package in selected), "Unexpected DVD GCC"
assert any("glibc-devel-" in package for package in selected), "Missing glibc headers"
with tempfile.TemporaryDirectory() as temp:
    command = ["xorriso", "-osirrox", "on", "-indev", iso]
    for package in selected:
        command += ["-extract", package, str(pathlib.Path(temp) / pathlib.PurePosixPath(package).name)]
    subprocess.run(command, check=True)
    for package in selected:
        rpm = pathlib.Path(temp) / pathlib.PurePosixPath(package).name
        with subprocess.Popen(["rpm2cpio", str(rpm)], stdout=subprocess.PIPE) as process:
            subprocess.run(
                ["cpio", "-idm", "--quiet", "--no-absolute-filenames"],
                cwd=root,
                stdin=process.stdout,
                check=True,
            )
            process.stdout.close()
            if process.wait():
                raise SystemExit(f"rpm2cpio failed: {rpm}")
for base, directories, files in os.walk(root):
    for name in directories + files:
        link = pathlib.Path(base) / name
        if link.is_symlink():
            target = os.readlink(link)
            if target.startswith("/"):
                link.unlink()
                link.symlink_to(os.path.relpath(root / target.lstrip("/"), link.parent))
provenance = root.parent / "provenance"
provenance.mkdir(exist_ok=True)
(provenance / "sysroot-packages.txt").write_text("\n".join(selected) + "\n")
print(f"Extracted {len(selected)} Anolis GA RPMs to {root}")
PY

COPY <<'PY' /scripts/prepare-sources.py
#!/usr/bin/env python3
import pathlib
import re
import subprocess
import sys
import tarfile

kind, rpm, output = sys.argv[1:]
destination = pathlib.Path(output).resolve()
destination.mkdir(parents=True, exist_ok=True)
with subprocess.Popen(["rpm2cpio", rpm], stdout=subprocess.PIPE) as process:
    subprocess.run(["cpio", "-idm", "--quiet"], cwd=destination, stdin=process.stdout, check=True)
    process.stdout.close()
    if process.wait():
        raise SystemExit("rpm2cpio failed")
spec = (destination / f"{kind}.spec").read_text()
archive = next(destination.glob(f"{kind}-*.tar.xz"))
with tarfile.open(archive) as source:
    root = destination / source.getmembers()[0].name.split("/")[0]
    source.extractall(destination)
patches = {int(number): name for number, name in re.findall(r"^Patch(\d+):\s*(\S+)", spec, re.M)}
prep = spec.split("\n%prep\n", 1)[1].split("\n%build\n", 1)[0]
if kind == "gcc":
    order = [int(number) for number in re.findall(r"^%patch(\d+)\s+-p\s*1", prep, re.M)]
else:
    assert "%autosetup -p1" in prep
    order = sorted(patches)
for number in order:
    with (destination / patches[number]).open("rb") as patch:
        subprocess.run(["patch", "--batch", "-p1"], cwd=root, stdin=patch, check=True)
if kind == "gcc":
    (root / "gcc/DEV-PHASE").write_text("Anolis OS 14.3.0-5.an23_rva23 cross compiler\n")
    subprocess.run(["bash", "contrib/gcc_update", "--touch"], cwd=root, check=True)
print(root)
(destination / "source-dir").write_text(str(root))
PY

COPY <<'SH' /scripts/build-toolchain.sh
#!/usr/bin/env bash
set -euo pipefail
prefix=/opt/anolis23.5/cross
sysroot=/opt/anolis23.5/sysroot
target=riscv64-unknown-linux-gnu
jobs=${JOBS:-32}
mkdir -p /build/binutils /build/gcc "$prefix"
python3 /scripts/prepare-sources.py binutils /inputs/binutils-2.42-4.an23_rva23.src.rpm /build/binutils-src
python3 /scripts/prepare-sources.py gcc /inputs/gcc-14.3.0-5.an23_rva23.src.rpm /build/gcc-src
cd /build/binutils
"$(cat /build/binutils-src/source-dir)/configure" --target="$target" --prefix="$prefix" \
    --with-sysroot="$sysroot" --disable-multilib --disable-nls --disable-werror \
    --disable-gprofng --disable-gdb --enable-plugins
make -j"$jobs" MAKEINFO=true
make install MAKEINFO=true
export PATH="$prefix/bin:$PATH"
cd /build/gcc
"$(cat /build/gcc-src/source-dir)/configure" --target="$target" --prefix="$prefix" \
    --with-sysroot="$sysroot" --with-native-system-header-dir=/usr/include \
    --with-arch=rv64gc --with-abi=lp64d --enable-languages=c,c++ \
    --disable-bootstrap --disable-multilib --disable-nls --enable-shared --enable-threads=posix \
    --disable-libsanitizer --disable-libquadmath --disable-libssp --disable-libvtv \
    --disable-libgomp --disable-libitm --disable-libcc1 --with-system-zlib \
    --with-pkgversion='Anolis 23.5 gcc-14.3.0-5.an23_rva23 cross'
make -j"$jobs" all-gcc all-target-libgcc all-target-libstdc++-v3
make install-gcc install-target-libgcc install-target-libstdc++-v3
"$target-gcc" --version
printf 'int main() { return 0; }\n' | "$target-gcc" -march=rva23u64 -mabi=lp64d -x c - -o /build/probe
"$target-readelf" -A /build/probe
mkdir -p /opt/anolis23.5/provenance
cp /inputs/SHA256SUMS /opt/anolis23.5/provenance/
cp /build/{gcc,binutils}-src/*.spec /opt/anolis23.5/provenance/
rm -rf /build
SH

RUN python3 /scripts/prepare-sysroot.py \
        /inputs/AnolisOS-23.5-riscv64-dvd.iso /opt/anolis23.5/sysroot \
    && chmod +x /scripts/build-toolchain.sh \
    && JOBS="${jobs}" /scripts/build-toolchain.sh \
    && mkdir -p /opt/bootjdk \
    && tar -xf /inputs/bootjdk.tar.gz -C /opt/bootjdk --strip-components=1 \
    && /opt/bootjdk/bin/java -version

FROM host AS builder
COPY --from=toolchain /opt/ /opt/
COPY --from=qemu /usr/bin/qemu-riscv64 /usr/local/bin/qemu-riscv64
RUN printf '%s  %s\n' \
        3ddd4d6ec959cbf040176a335e33fd6c21d1097073d686b8fff02cd516e083b4 \
        /usr/local/bin/qemu-riscv64 | sha256sum -c - \
    && /usr/local/bin/qemu-riscv64 --version | grep -F 'qemu-riscv64 version 10.2.3' \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ant jq pigz wget \
    && rm -rf /var/lib/apt/lists/*

ENV PATH=/opt/anolis23.5/cross/bin:/opt/bootjdk/bin:$PATH \
    JAVA_HOME=/opt/bootjdk \
    RISCV64=/opt/anolis23.5/cross \
    RISCV_SYSROOT=/opt/anolis23.5/sysroot \
    JDK_BOOT_VERSION=25 \
    JDK25_BOOT_DIR=/opt/bootjdk \
    RISCV_BUILD_JDK=/opt/bootjdk \
    QEMU_RISCV64=/usr/local/bin/qemu-riscv64 \
    QEMU_CPU_MODEL=rva23u64,zifencei=true

WORKDIR /openjdk-build
CMD ["bash"]
