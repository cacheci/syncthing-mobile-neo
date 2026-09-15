#!/usr/bin/env python3
"""Build the bundled Syncthing Android executables.

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
import time
import urllib.request
from pathlib import Path
from typing import NoReturn, Optional


HOST_TOOLCHAIN_DIRS = {
    "Darwin": "darwin-x86_64",
    "Linux": "linux-x86_64",
}

GO_ARCHIVES = {
    ("1.27.1", "darwin", "arm64"): (
        "go1.27.1.darwin-arm64.tar.gz",
        "ee215d57e0ec269c60cc9ceca68e6bda321ba9ee5afe24f4b0988703c2d87d12",
    ),
    ("1.27.1", "linux", "amd64"): (
        "go1.27.1.linux-amd64.tar.gz",
        "63d339f0da5ab53635a56f2490a7984dfe12dfcff22ad749f63edaf590168445",
    ),
    ("1.27.1", "linux", "arm64"): (
        "go1.27.1.linux-arm64.tar.gz",
        "3450b45a3f9ee8568792736a5c5e70a1f2e9b36c35a8f74958c03e51d7d92bec",
    ),
}

GO_INSTALL_MARKER = ".syncthing-go-archive"

ANDROID_TARGETS = (
    ("arm64-v8a", "arm64", "aarch64-linux-android28-clang", None),
    ("armeabi-v7a", "arm", "armv7a-linux-androideabi28-clang", "7"),
    ("x86", "386", "i686-linux-android28-clang", None),
    ("x86_64", "amd64", "x86_64-linux-android28-clang", None),
)


def log(message: str) -> None:
    print(f"[build-syncthing] {message}", flush=True)


def log_error(message: str) -> None:
    print(f"[build-syncthing] Error: {message}", file=sys.stderr, flush=True)


def fail(message: str) -> NoReturn:
    raise RuntimeError(message)


def read_catalog_version(catalog: Path, key: str) -> str:
    pattern = re.compile(rf'^\s*{re.escape(key)}\s*=\s*["\']([^"\']+)["\']\s*$')
    for line in catalog.read_text(encoding="utf-8").splitlines():
        match = pattern.match(line)
        if match:
            return match.group(1)
    fail(f"Version catalog is missing {key}: {catalog}")


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
                fail(f"Go source archive contains an unsafe path: {member.name}")
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
        fail(f"Unsupported build host: {platform.system()}")

    goarch = {
        "x86_64": "amd64",
        "amd64": "amd64",
        "arm64": "arm64",
        "aarch64": "arm64",
    }.get(platform.machine().lower())
    if goarch is None:
        fail(f"Unsupported build host architecture: {platform.machine()}")
    return goos, goarch


def downloaded_go(project_dir: Path, expected_version: str) -> Path:
    goos, goarch = go_host()
    archive_info = GO_ARCHIVES.get((expected_version, goos, goarch))
    if archive_info is None:
        fail(
            f"Toolchain verification failed: "
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
        log(f"Downloading Go toolchain {expected_version}: {url}")
        urllib.request.urlretrieve(url, archive_file)
        actual_sha256 = sha256(archive_file)
        if actual_sha256 != expected_sha256:
            fail(
                f"Toolchain verification failed: {expected_sha256} & {actual_sha256}",
            )

        extraction_dir = Path(tempfile.mkdtemp(prefix=".go-extract-", dir=third_party_dir))
        safely_extract(archive_file, extraction_dir)
        extracted_go = extraction_dir / "go"
        extracted_binary = extracted_go / "bin" / "go"
        if not extracted_binary.is_file():
            fail(f"Toolchain not found: {extracted_binary}")
        actual_version = go_version(extracted_binary)
        if actual_version != expected_version:
            fail(f"Incorrect Go toolchain: {expected_version} & {actual_version or 'unavailable'}")

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
            fail("Android SDK not found; configure local.properties, ANDROID_SDK_ROOT, or ANDROID_HOME")
        ndk = sdk / "ndk" / expected_version

    source_properties = ndk / "source.properties"
    actual_version = None
    if source_properties.is_file():
        for line in source_properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("Pkg.Revision"):
                actual_version = line.partition("=")[2].strip()
                break
    if actual_version != expected_version:
        fail(f"Android NDK version mismatch: expected {expected_version}, actual {actual_version or 'unavailable'}")
    return ndk.resolve()


def verify_source(source_dir: Path, expected_commit: str) -> None:
    if not (source_dir / "build.go").is_file():
        fail("Syncthing submodule is not initialized; initialize third_party/syncthing first")
    result = subprocess.run(
        ["git", "-C", str(source_dir), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    )
    actual_commit = result.stdout.strip()
    if actual_commit != expected_commit:
        fail(f"Syncthing submodule commit mismatch: expected {expected_commit}, actual {actual_commit}")


def build(project_dir: Path, source_dir: Path, output_dir: Path) -> None:
    started_at = time.monotonic()
    catalog = project_dir / "gradle" / "libs.versions.toml"
    log(f"Reading version catalog: {catalog}")
    syncthing_version = read_catalog_version(catalog, "syncthing-version")
    syncthing_commit = read_catalog_version(catalog, "syncthing-commit")
    ndk_version = read_catalog_version(catalog, "ndk")
    expected_go_version = read_catalog_version(catalog, "go")
    log(
        f"Build configuration: Syncthing v{syncthing_version}, "
        f"Go {expected_go_version}, NDK {ndk_version}, "
        f"{len(ANDROID_TARGETS)} target ABIs",
    )

    log(f"Verifying Syncthing source: {source_dir}")
    verify_source(source_dir, syncthing_commit)
    log(f"Syncthing source verified: {syncthing_commit}")

    log("Preparing Go toolchain")
    go = downloaded_go(project_dir, expected_go_version)
    log(f"Using Go toolchain: {go}")
    log("Locating Android NDK")
    ndk = find_ndk(project_dir, ndk_version)
    log(f"Using Android NDK: {ndk}")
    host_dir = HOST_TOOLCHAIN_DIRS.get(platform.system())
    if host_dir is None:
        fail(f"Unsupported build host: {platform.system()}")
    toolchain_bin = ndk / "toolchains" / "llvm" / "prebuilt" / host_dir / "bin"

    for index, (android_abi, goarch, compiler_name, goarm) in enumerate(ANDROID_TARGETS, start=1):
        target_started_at = time.monotonic()
        compiler = toolchain_bin / compiler_name
        if not compiler.is_file():
            fail(f"Android {android_abi} compiler not found: {compiler}")

        output = output_dir / android_abi / "libsyncthingnative.so"
        output.parent.mkdir(parents=True, exist_ok=True)
        go_target = f"android/{goarch}" + (f" GOARM={goarm}" if goarm is not None else "")
        log(f"[{index}/{len(ANDROID_TARGETS)}] Building:  {android_abi} ({go_target})")
        log(f"[{index}/{len(ANDROID_TARGETS)}] Compiler: {compiler}")
        log(f"[{index}/{len(ANDROID_TARGETS)}] Output: {output}")
        environment = os.environ.copy()
        environment.update(
            {
                "BUILD_HOST": "syncthingG",
                "BUILD_USER": "reproducible-build",
                "BUILDDEBUG": "1",
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
        if goarm is not None:
            environment["GOARM"] = goarm
        else:
            environment.pop("GOARM", None)

        try:
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
                    goarch,
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
        except subprocess.CalledProcessError as error:
            fail(f"Syncthing {android_abi} build failed with exit code {error.returncode}")
        if not output.is_file():
            fail(f"Syncthing {android_abi} build failed: {output}")
        elapsed = time.monotonic() - target_started_at
        log(f"[{index}/{len(ANDROID_TARGETS)}] {android_abi} build succeeded in {elapsed:.1f} seconds")

    total_elapsed = time.monotonic() - started_at
    log(f"Built all {len(ANDROID_TARGETS)} ABIs in {total_elapsed:.1f} seconds")


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
        log_error(f"Syncthing build failed: {error}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
