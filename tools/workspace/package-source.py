#!/usr/bin/env python3
"""Package the current checkout without builds, SDK caches, local config or credentials."""
import argparse
import hashlib
import subprocess
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[2]
EXCLUDED={'.git','.gradle','.idea','.cxx','build','dist','__pycache__','.pytest_cache','.venv'}
SECRET_SUFFIXES={'.pem','.key','.p12','.pfx','.jks','.keystore','.crt','.cer'}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output',type=Path)
    args=parser.parse_args()
    output=args.output.resolve()
    if output.is_relative_to(ROOT):parser.error('Output must be outside the source tree')
    output.parent.mkdir(parents=True,exist_ok=True)
    files=[]
    names=subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'],cwd=ROOT).decode().split('\0')
    for name in sorted(set(names)-{''}):
        path=ROOT/name
        relative=Path(name)
        if any(part in EXCLUDED for part in relative.parts):continue
        if not path.is_file() or path.is_symlink():continue
        if path.name=='local.properties' or path.name.startswith('.env') or path.suffix.lower() in SECRET_SUFFIXES|{'.apk','.aab','.zip'}:continue
        files.append((relative,path))
    manifest=[]
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
        for relative,path in sorted(files):
            name='StormVR-workspace-mr/'+relative.as_posix()
            archive.write(path,name)
            manifest.append(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+relative.as_posix())
        archive.writestr('StormVR-workspace-mr/MANIFEST.sha256','\n'.join(manifest)+'\n')
    with zipfile.ZipFile(output) as archive:
        bad=archive.testzip()
        if bad:raise RuntimeError('Corrupt ZIP entry: '+bad)
    print(f'{output}: {len(files)} source files, {output.stat().st_size} bytes')

if __name__=='__main__':main()
