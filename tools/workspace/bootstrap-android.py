#!/usr/bin/env python3
"""Install a pinned Android toolchain under HOME, without sudo or root-partition caches.
Run with --accept-licenses after reviewing Android SDK licenses.
Downloads may be large (NDK + SDK + Gradle + JDK); at least 10 GiB free required.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=Path(os.environ.get('STORMVR_TOOLCHAIN',str(Path.home()/'Android'/'stormvr')))
CACHE=BASE/'downloads'

def read(url):
    return subprocess.check_output(['curl','--http1.1','-fsSL','--retry','2','--connect-timeout','20','--max-time','90',url])

def download(url,path,expected,algorithm='sha256'):
    path.parent.mkdir(parents=True,exist_ok=True)
    def digest():
        h=hashlib.new(algorithm)
        with path.open('rb') as stream:
            for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
        return h.hexdigest()
    if path.exists() and digest()==expected:return
    for attempt in range(2):
        try:
            subprocess.run(['curl','--http1.1','-fL','--retry','3','--retry-all-errors','--connect-timeout','30','-C','-',url,'-o',str(path)],check=True)
            if digest()==expected:return
        except subprocess.CalledProcessError:
            if attempt:raise
        # Only this tool's downloaded archive is removed; source/user configuration is untouched.
        path.unlink(missing_ok=True)
    raise RuntimeError(f'Checksum mismatch after retry: {path}')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--accept-licenses',action='store_true');args=p.parse_args()
    if not args.accept_licenses:p.error('Review SDK licenses and rerun with --accept-licenses to install SDK/NDK')
    BASE.mkdir(parents=True,exist_ok=True)
    if shutil.disk_usage(BASE).free < 10*1024**3:p.error('At least 10 GiB free required in toolchain filesystem')
    metadata=json.loads(read('https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=linux'))[0]['binary']['package']
    jdk_archive=CACHE/'jdk17.tar.gz';download(metadata['link'],jdk_archive,metadata['checksum'])
    jdk=BASE/'jdk'
    if not jdk.exists():
        jdk.mkdir()
        with tarfile.open(jdk_archive) as archive:archive.extractall(jdk,filter='data')
    java=next(jdk.glob('*/bin/java')).parent.parent
    version='8.7';gradle_zip=CACHE/f'gradle-{version}-bin.zip'
    sha='544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d'
    download(f'https://services.gradle.org/distributions/gradle-{version}-bin.zip',gradle_zip,sha)
    if not (BASE/f'gradle-{version}').exists():
        with zipfile.ZipFile(gradle_zip) as archive:archive.extractall(BASE)
        (BASE/f'gradle-{version}/bin/gradle').chmod(0o755)
    url0='https://services.gradle.org/distributions/gradle-8.7-bin.zip'
    number=int.from_bytes(hashlib.md5(url0.encode()).digest(),'big')
    alphabet='0123456789abcdefghijklmnopqrstuvwxyz'; suffix=''
    while number: number,remainder=divmod(number,36);suffix=alphabet[remainder]+suffix
    dist=BASE/'gradle-cache'/'wrapper'/'dists'/'gradle-8.7-bin'/suffix
    dist.mkdir(parents=True,exist_ok=True)
    link=dist/'gradle-8.7'
    if not link.exists():link.symlink_to(BASE/'gradle-8.7',target_is_directory=True)
    (dist/'gradle-8.7-bin.zip.ok').touch()
    xml=ET.fromstring(read('https://dl.google.com/android/repository/repository2-1.xml'))
    package=next(x for x in xml.iter('remotePackage') if x.get('path')=='cmdline-tools;12.0')
    archive=next(a for a in package.findall('./archives/archive') if a.findtext('host-os')=='linux')
    url=archive.findtext('./complete/url');checksum=archive.findtext('./complete/checksum')
    tools_zip=CACHE/'cmdline-tools-12.zip';download('https://dl.google.com/android/repository/'+url,tools_zip,checksum,'sha1')
    sdk=BASE/'sdk';sdk.mkdir(exist_ok=True)
    tools=sdk/'cmdline-tools'/'latest'
    if not tools.exists():
        stage=BASE/'cmdline-stage';stage.mkdir(exist_ok=True)
        with zipfile.ZipFile(tools_zip) as z:z.extractall(stage)
        tools.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(stage/'cmdline-tools'),tools)
        for path in (tools/'bin').iterdir():path.chmod(0o755)
    env=os.environ.copy();env.update(JAVA_HOME=str(java),ANDROID_HOME=str(sdk),ANDROID_SDK_ROOT=str(sdk),GRADLE_USER_HOME=str(BASE/'gradle-cache'))
    env['PATH']=str(java/'bin')+':'+env['PATH']
    manager=str(tools/'bin'/'sdkmanager')
    subprocess.run([manager,'--sdk_root='+str(sdk),'--licenses'],input='y\n'*100,text=True,env=env,check=True)
    subprocess.run([manager,'--sdk_root='+str(sdk),'platform-tools','platforms;android-34','platforms;android-35','build-tools;34.0.0','ndk;27.0.12077973'],env=env,check=True)
    # Never reuse the tracked Windows local.properties. Gradle's SDK override is local.
    properties=ROOT/'Source/android/local.properties'
    properties.write_text('sdk.dir='+str(sdk)+'\n')
    import shlex
    envfile=BASE/'environment.sh'
    envfile.write_text('\n'.join('export '+k+'='+shlex.quote(v) for k,v in env.items() if k in ('JAVA_HOME','ANDROID_HOME','ANDROID_SDK_ROOT','GRADLE_USER_HOME','PATH'))+'\n')
    print('Toolchain ready. Source '+str(envfile)+' and run Source/android/gradlew :app:assembleDebug')

if __name__=='__main__':main()
