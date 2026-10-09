#!/usr/bin/env python3
"""Remove an explicit list of rebuildable outputs; preview unless --apply is given."""
import argparse
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--apply", action="store_true")
parser.add_argument("--remove-test-sdk", action="store_true", help="Remove the optional sibling android-test-sdk after stopping its emulator")
args = parser.parse_args()
paths = [root / p for p in (
    ".gradle", ".kotlin", "build", "app/build", "streamflix/.gradle", "streamflix/build",
    "streamflix/app/build", "streamflix/navigation/build", "streamflix/retrofit-jsoup-converter/build",
    "windows/dist", "windows/node_modules", "app/src/main/java/fr/nekotv/MainActivity.kt.backup",
    "scripts/__pycache__",
)]
for pattern in ("*.apk", "*.apk.idsig", "*.apk.sha256", "*.exe", "*.exe.sha256"):
    paths.extend((root / "releases").glob(pattern))
# Preserve written conclusions and the current Android proof set; old captures
# and machine-generated reports are reproducible outputs, not test fixtures.
for folder in ("verification/2026-10-05", "verification/2026-10-06", "verification/2026-10-08", "windows/verification"):
    paths.extend(p for p in (root / folder).rglob("*") if p.is_file() and p.suffix != ".md")
paths.append(root / "verification/source-audit.jsonl")
if args.remove_test_sdk:
    sdk = root.parent / "android-test-sdk"
    local = root / "local.properties"
    if local.exists() and str(sdk) in local.read_text():
        raise SystemExit("Le SDK de test est configuré comme SDK de compilation : conservez-le ou reconfigurez sdk.dir.")
    paths.append(sdk)
for path in paths:
    allowed_root = root.parent if path == root.parent / "android-test-sdk" else root
    if not path.parent.resolve().is_relative_to(allowed_root):
        raise SystemExit(f"Refus de suivre un dossier parent hors du projet : {path}")
    if path.exists() or path.is_symlink():
        print(("Suppression : " if args.apply else "Prévu : ") + str(path))
        if args.apply:
            if path.is_dir() and not path.is_symlink():
                shutil.rmtree(path)
            else:
                path.unlink()
