#!/usr/bin/env python3
"""Collect one physical-device run without clearing logs, battery stats or changing settings."""
import argparse
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--seconds', type=int, default=120)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not shutil.which(args.adb):
        parser.error('ADB não encontrado; use --adb com o executável instalado.')
    if args.seconds < 10:
        parser.error('Use pelo menos 10 segundos.')
    args.output.mkdir(parents=True, exist_ok=False)
    adb = [args.adb, '-s', args.serial]
    def save(name, command):
        result = subprocess.run(adb + command, capture_output=True, text=True, timeout=30)
        (args.output / name).write_text(result.stdout + result.stderr)
        return result
    result = save('device.txt', ['shell', 'getprop'])
    if result.returncode:
        sys.exit('Dispositivo indisponível. Veja device.txt.')
    save('memory-before.txt', ['shell', 'dumpsys', 'meminfo', 'com.stormvr.workspace.mr'])
    save('battery-before.txt', ['shell', 'dumpsys', 'battery'])
    save('power-before.txt', ['shell', 'dumpsys', 'batterystats', 'com.stormvr.workspace.mr'])
    # Only new diagnostic messages; no camera frames or raw landmarks.
    with (args.output / 'spatial.log').open('w') as output:
        process = subprocess.Popen(adb + ['logcat', '-v', 'raw', '-T', '1', 'StormVRSpatial:I', '*:S'], stdout=output, stderr=subprocess.STDOUT)
        try:
            time.sleep(args.seconds)
        except KeyboardInterrupt:
            pass
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait()
    save('memory-after.txt', ['shell', 'dumpsys', 'meminfo', 'com.stormvr.workspace.mr'])
    save('battery-after.txt', ['shell', 'dumpsys', 'battery'])
    save('power-after.txt', ['shell', 'dumpsys', 'batterystats', 'com.stormvr.workspace.mr'])
    rows = []
    for line in (args.output / 'spatial.log').read_text().splitlines():
        try:
            row = json.loads(line)
            if 'engine' in row: rows.append(row)
        except (ValueError, TypeError):
            pass
    (args.output / 'snapshots.json').write_text(json.dumps(rows, indent=2))
    print(f'{len(rows)} amostras; resultados em {args.output.resolve()}')
    if not rows:
        print('Ative Diagnóstico espacial e mantenha a Sala VR em primeiro plano.', file=sys.stderr)
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
