#!/usr/bin/env python3
"""Offline integrity check of the pinned, bundled MediaPipe hand model."""
from pathlib import Path
import hashlib
p=Path(__file__).resolve().parents[2]/'Source/android/app/src/main/assets/hand_landmarker.task'
data=p.read_bytes()
assert len(data)==7819105, 'Unexpected hand model size'
assert hashlib.sha256(data).hexdigest()=='fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1', 'Hand model checksum mismatch'
print('hand_landmarker.task: 7819105 bytes, SHA-256 verified')
