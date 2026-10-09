#!/usr/bin/env python3
"""Build and sign the universal APK; keep the delivery outside the source tree."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--no-build", action="store_true", help="Sign an already compiled release")
args = parser.parse_args()
version = re.search(r'versionName = "([^"]+)"', (root / "app/build.gradle.kts").read_text()).group(1)
local = root / "local.properties"
sdk = os.environ.get("ANDROID_HOME") or (re.search(r"^sdk.dir=(.+)$", local.read_text(), re.M).group(1) if local.exists() else "")
if not sdk:
    raise SystemExit("Configure ANDROID_HOME ou sdk.dir dans local.properties.")
sdk = Path(sdk)
build_tools = sorted((sdk / "build-tools").iterdir(), key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.name)))[-1]
keystore = Path(os.environ.get("TVSAMA_KEYSTORE", str(Path.home() / ".android/debug.keystore"))).expanduser()
if not keystore.is_file():
    raise SystemExit("Restaurez la clé de signature existante avant de construire une mise à jour.")
env = os.environ.copy()
env.setdefault("TVSAMA_STORE_PASSWORD", "android")
env.setdefault("TVSAMA_KEY_PASSWORD", env["TVSAMA_STORE_PASSWORD"])
if not args.no_build:
    subprocess.run([str(root / "gradlew"), ":app:assembleRelease", "--console=plain"], cwd=root, check=True)
unsigned = root / "app/build/outputs/apk/release/app-release-unsigned.apk"
output_dir = root.parent / "releases"
output_dir.mkdir(exist_ok=True)
output = output_dir / f"TvSama-{version}-universal.apk"
with tempfile.TemporaryDirectory(prefix=".apk-sign-", dir=output_dir) as temp:
    aligned = Path(temp) / "aligned.apk"
    signed = Path(temp) / "signed.apk"
    subprocess.run([str(build_tools / "zipalign"), "-P", "16", "-f", "4", str(unsigned), str(aligned)], check=True)
    subprocess.run([str(build_tools / "apksigner"), "sign", "--ks", str(keystore),
                    "--ks-key-alias", env.get("TVSAMA_KEY_ALIAS", "androiddebugkey"),
                    "--ks-pass", "env:TVSAMA_STORE_PASSWORD", "--key-pass", "env:TVSAMA_KEY_PASSWORD",
                    "--v4-signing-enabled", "false", "--out", str(signed), str(aligned)], env=env, check=True)
    verified = subprocess.run([str(build_tools / "apksigner"), "verify", "--print-certs", str(signed)],
                              capture_output=True, text=True, check=True)
    subprocess.run([str(build_tools / "zipalign"), "-c", "-P", "16", "4", str(signed)], check=True)
    signed.replace(output)
digest = hashlib.sha256(output.read_bytes()).hexdigest()
output.with_suffix(".apk.sha256").write_text(f"{digest}  {output.name}\n")
print(output)
print(f"SHA-256: {digest}")
print(next(line for line in verified.stdout.splitlines() if "certificate SHA-256" in line))
