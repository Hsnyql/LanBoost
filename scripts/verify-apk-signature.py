#!/usr/bin/env python3
"""Verify the APK and compare its signer with the public certificate pin."""
import os
from pathlib import Path
import re
import subprocess
import sys

if len(sys.argv) != 2:
    raise SystemExit('Usage: verify-apk-signature.py APK')
root = Path(__file__).resolve().parent.parent
expected = (root / 'signing-certificate.sha256').read_text().strip().lower()
if not re.fullmatch(r'[0-9a-f]{64}', expected):
    raise SystemExit('Invalid expected certificate SHA-256.')
sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
if not sdk:
    raise SystemExit('Set ANDROID_HOME to Android SDK with Build Tools 35.0.0.')
apksigner = Path(sdk) / 'build-tools/35.0.0/apksigner'
result = subprocess.run([str(apksigner), 'verify', '--print-certs', sys.argv[1]],
                        check=True, capture_output=True, text=True)
signers = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', result.stdout, re.M)
if [signer.lower() for signer in signers] != [expected]:
    raise SystemExit('APK signer does not match the fixed certificate; do not distribute it as an update.')
print('PASS: APK signature valid and matches the fixed signing certificate.')
