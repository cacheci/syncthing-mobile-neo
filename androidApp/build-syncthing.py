#!/usr/bin/env python3
"""Build the bundled Syncthing Android ARM64 executable.

The workflow is adapted to this project from researchxxl/syncthing-android's
build-syncthing.py (MPL-2.0). Generated files stay outside the source tree.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
from pathlib import Path
from typing import NoReturn, Optional


HOST_TOOLCHAIN_DIRS = {
    "Darwin": "darwin-x86_64",
    "Linux": "linux-x86_64",
}

GO_ARCHIVES = {
    ("1.26.5", "darwin", "arm64"): (
        "go1.26.5.darwin-arm64.tar.gz",
        "efb87ff28af9a188d0536ef5d42e63dd52ba8263cd7344a993cc48dd11dedb6a",
    ),
    ("1.26.5", "linux", "amd64"): (
        "go1.26.5.linux-amd64.tar.gz",
        "5c2c3b16caefa1d968a94c1daca04a7ca301a496d9b086e17ad77bb81393f053",
    ),
    ("1.26.5", "linux", "arm64"): (
        "go1.26.5.linux-arm64.tar.gz",
        "fe4789e92b1f33358680864bbe8704289e7bb5fc207d80623c308935bd696d49",
    ),
}

GO_INSTALL_MARKER = ".syncthing-go-archive"


def fail(message: str) -> NoReturn:
    raise RuntimeError(message)


def read_catalog_version(catalog: Path, key: str) -> str:
    pattern = re.compile(rf'^\s*{re.escape(key)}\s*=\s*["\']([^"\']+)["\']\s*$')
    for line in catalog.read_text(encoding="utf-8").splitlines():
        match = pattern.match(line)
        if match:
            return match.group(1)
    fail(f"版本目录缺少 {key}: {catalog}")


def go_version(go: Path) -> Optional[str]:
    result = subprocess.run(
        [str(go), "version"],
        check=False,
        capture_output=True,
        text=True,
        timeout=10,
    )
    match = re.search(r"\bgo(\d+\.\d+(?:\.\d+)?)\b", result.stdout)
    return match.group(1) if result.returncode == 0 and match else None


def safely_extract(archive: Path, destination: Path) -> None:
    destination_root = destination.resolve()
    with tarfile.open(archive, "r:gz") as tar:
        for member in tar.getmembers():
            member_path = (destination / member.name).resolve()
            if destination_root != member_path and destination_root not in member_path.parents:
                fail(f"Go 源码压缩包包含不安全路径：{member.name}")
        try:
            tar.extractall(destination, filter="data")
        except TypeError:
            tar.extractall(destination)


def sha256(file: Path) -> str:
    digest = hashlib.sha256()
    with file.open("rb") as input_file:
        while chunk := input_file.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def go_host() -> tuple[str, str]:
    goos = {
        "Darwin": "darwin",
        "Linux": "linux",
    }.get(platform.system())
    if goos is None:
        fail(f"不支持的构建主机：{platform.system()}")

    goarch = {
        "x86_64": "amd64",
        "amd64": "amd64",
        "arm64": "arm64",
        "aarch64": "arm64",
    }.get(platform.machine().lower())
    if goarch is None:
        fail(f"不支持的构建主机架构：{platform.machine()}")
    return goos, goarch


def downloaded_go(project_dir: Path, expected_version: str) -> Path:
    goos, goarch = go_host()
    archive_info = GO_ARCHIVES.get((expected_version, goos, goarch))
    if archive_info is None:
        fail(
            f"工具链校验失败："
            "{expected_version} {goos}-{goarch}",
        )
    filename, expected_sha256 = archive_info

    third_party_dir = project_dir / "third_party"
    install_dir = third_party_dir / "go"
    go_binary = install_dir / "bin" / "go"
    marker = install_dir / GO_INSTALL_MARKER
    expected_marker = f"{filename}\n{expected_sha256}\n"
    if (
        go_binary.is_file()
        and os.access(go_binary, os.X_OK)
        and marker.is_file()
        and marker.read_text(encoding="utf-8") == expected_marker
        and go_version(go_binary) == expected_version
    ):
        return go_binary.resolve()

    third_party_dir.mkdir(parents=True, exist_ok=True)
    archive_file: Optional[Path] = None
    extraction_dir: Optional[Path] = None
    try:
        with tempfile.NamedTemporaryFile(
            prefix=f".{filename}.",
            suffix=".download",
            dir=third_party_dir,
            delete=False,
        ) as temporary_archive:
            archive_file = Path(temporary_archive.name)

        url = f"https://go.dev/dl/{filename}"
        print(f"下载 Go 工具链 {expected_version}: {url}")
        urllib.request.urlretrieve(url, archive_file)
        actual_sha256 = sha256(archive_file)
        if actual_sha256 != expected_sha256:
            fail(
                f"工具链校验失败：{expected_sha256} & {actual_sha256}",
            )

        extraction_dir = Path(tempfile.mkdtemp(prefix=".go-extract-", dir=third_party_dir))
        safely_extract(archive_file, extraction_dir)
        extracted_go = extraction_dir / "go"
        extracted_binary = extracted_go / "bin" / "go"
        if not extracted_binary.is_file():
            fail(f"找不到工具链：{extracted_binary}")
        actual_version = go_version(extracted_binary)
        if actual_version != expected_version:
            fail(f"Go 工具链错误： {expected_version} & {actual_version or '无法读取'}")

        if install_dir.exists():
            shutil.rmtree(install_dir)
        extracted_go.replace(install_dir)
        marker = install_dir / GO_INSTALL_MARKER
        marker.write_text(expected_marker, encoding="utf-8")
        return (install_dir / "bin" / "go").resolve()
    finally:
        if archive_file is not None:
            archive_file.unlink(missing_ok=True)
        if extraction_dir is not None:
            shutil.rmtree(extraction_dir, ignore_errors=True)


def read_local_sdk(project_dir: Path) -> Optional[Path]:
    local_properties = project_dir / "local.properties"
    if not local_properties.is_file():
        return None
    for line in local_properties.read_text(encoding="utf-8").splitlines():
        if line.startswith("sdk.dir="):
            value = line.partition("=")[2].strip().replace("\\:", ":").replace("\\\\", "\\")
            return Path(value).expanduser()
    return None


def find_ndk(project_dir: Path, expected_version: str) -> Path:
    configured = os.environ.get("ANDROID_NDK_HOME") or os.environ.get("ANDROID_NDK_ROOT")
    if configured:
        ndk = Path(configured).expanduser()
    else:
        sdk_value = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
        sdk = Path(sdk_value).expanduser() if sdk_value else read_local_sdk(project_dir)
        if sdk is None:
            fail("找不到 Android SDK；请配置 local.properties、ANDROID_SDK_ROOT 或 ANDROID_HOME")
        ndk = sdk / "ndk" / expected_version

    source_properties = ndk / "source.properties"
    actual_version = None
    if source_properties.is_file():
        for line in source_properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("Pkg.Revision"):
                actual_version = line.partition("=")[2].strip()
                break
    if actual_version != expected_version:
        fail(f"Android NDK 版本不匹配：期望 {expected_version}，实际 {actual_version or '无法读取'}")
    return ndk.resolve()


def verify_source(source_dir: Path, expected_commit: str) -> None:
    if not (source_dir / "build.go").is_file():
        fail("Syncthing 子模块未初始化，请先初始化 third_party/syncthing")
    result = subprocess.run(
        ["git", "-C", str(source_dir), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    )
    actual_commit = result.stdout.strip()
    if actual_commit != expected_commit:
        fail(f"Syncthing 子模块 commit 不匹配：期望 {expected_commit}，实际 {actual_commit}")


def build(project_dir: Path, source_dir: Path, output_dir: Path) -> None:
    catalog = project_dir / "gradle" / "libs.versions.toml"
    syncthing_version = read_catalog_version(catalog, "syncthing-version")
    syncthing_commit = read_catalog_version(catalog, "syncthing-commit")
    ndk_version = read_catalog_version(catalog, "ndk")
    expected_go_version = read_catalog_version(catalog, "go")
    verify_source(source_dir, syncthing_commit)

    go = downloaded_go(project_dir, expected_go_version)
    ndk = find_ndk(project_dir, ndk_version)
    host_dir = HOST_TOOLCHAIN_DIRS.get(platform.system())
    if host_dir is None:
        fail(f"不支持的构建主机：{platform.system()}")
    compiler = ndk / "toolchains" / "llvm" / "prebuilt" / host_dir / "bin" / "aarch64-linux-android28-clang"
    if not compiler.is_file():
        fail(f"找不到 Android ARM64 编译器：{compiler}")

    output = output_dir / "arm64-v8a" / "libsyncthingnative.so"
    output.parent.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment.update(
        {
            "BUILD_HOST": "syncthingG",
            "BUILD_USER": "reproducible-build",
            "CGO_ENABLED": "1",
            "EXTRA_LDFLAGS": "-checklinkname=0",
            "GO111MODULE": "on",
            "GOFLAGS": "-buildvcs=false",
            "GOTOOLCHAIN": "local",
            "PATH": f"{go.parent}{os.pathsep}{os.environ.get('PATH', '')}",
            "SOURCE_DATE_EPOCH": "0",
            "STTRACE": "",
        }
    )
    subprocess.run(
        [
            str(go),
            "run",
            "build.go",
            "-gocmd",
            str(go),
            "-goos",
            "android",
            "-goarch",
            "arm64",
            "-cc",
            str(compiler),
            "-version",
            f"v{syncthing_version}",
            "-no-upgrade",
            "-build-out",
            str(output),
            "build",
        ],
        cwd=source_dir,
        env=environment,
        check=True,
    )
    if not output.is_file():
        fail(f"Syncthing 构建失败：{output}")
    print(f"Syncthing 构建成功：{output}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-dir", type=Path, required=True)
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    arguments = parser.parse_args()
    try:
        build(
            arguments.project_dir.resolve(),
            arguments.source_dir.resolve(),
            arguments.output_dir.resolve(),
        )
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Syncthing 核心构建失败：{error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
