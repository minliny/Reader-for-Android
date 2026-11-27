#!/usr/bin/env python3
"""Verify built Android artifacts against the pinned payload and unsigned-dev contract."""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk'))
TOOLS = SDK / 'build-tools/36.1.0'
ELF = next((SDK / 'ndk/26.3.11579264/toolchains/llvm/prebuilt').glob('*/bin/llvm-readelf'))
MAIN = 'app/src/main/'
RUNTIME = 'assets/reader_ebook_runtime/'
FIXTURE = 'assets/fixtures/m1-window.epub'
LIBRARY = 'lib/arm64-v8a/libreader_core_jni.so'
IDENTITY = 'io.reader.minliny.rebuild.android.dev'


def sha(bytes_):
    return hashlib.sha256(bytes_).hexdigest()


def check_package(apk, test):
    badging = subprocess.check_output([TOOLS / 'aapt2', 'dump', 'badging', apk], text=True)
    expected = IDENTITY + '.test' if test else IDENTITY
    assert badging.startswith(f"package: name='{expected}' "), badging[:200]
    assert "minSdkVersion:'26'" in badging and "targetSdkVersion:'36'" in badging, badging
    if test:
        manifest = subprocess.check_output([TOOLS / 'aapt2', 'dump', 'xmltree',
                                            '--file', 'AndroidManifest.xml', apk], text=True)
        instrumentation = manifest.split('E: instrumentation', 1)[1].split('\n      E: ', 1)[0]
        assert re.search(r'targetPackage\(0x[0-9a-f]+\)="' + re.escape(IDENTITY) + '"', instrumentation), instrumentation
        assert 'androidx.test.runner.AndroidJUnitRunner' in instrumentation
        print(instrumentation.strip())
    print(badging.splitlines()[0])
    checked = subprocess.run([TOOLS / 'apksigner', 'verify', '--verbose', apk],
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    print(checked.stdout, end='')
    assert checked.returncode != 0 and 'DOES NOT VERIFY' in checked.stdout, apk
    subprocess.run([TOOLS / 'zipalign', '-c', '-P', '16', '-v', '4', apk], check=True)
    print(f'APK sha256 {sha(apk.read_bytes())} {apk}')


def main():
    production, instrument = map(Path, sys.argv[1:3])
    outputs = json.loads((ROOT / 'pinned-inputs.sha256.json').read_text())['outputs']
    desired = {key.removeprefix(MAIN): row['sha256'] for key, row in outputs.items() if key.startswith(MAIN + RUNTIME)}
    fixture_sha = outputs['app/src/androidTest/' + FIXTURE]['sha256']
    native = ROOT / '.build/native/android-jni/libs/arm64-v8a/libreader_core_jni.so'
    for apk, test in [(production, False), (instrument, True)]:
        with zipfile.ZipFile(apk) as zipped:
            names = zipped.namelist()
            assert len(names) == len(set(names)), apk
            assert not [p for p in names if re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA|EC)', p, re.I)], apk
            dex = b''.join(zipped.read(p) for p in names if re.fullmatch(r'classes\d*\.dex', p))
            assert b'Lio/reader/rebuild/NativeCoreTest;' not in dex, apk
            assert (b'Lio/reader/rebuild/EbookWebViewTest;' in dex) == test, apk
            if test:
                assert [p for p in names if p.endswith('.epub')] == [FIXTURE]
                assert sha(zipped.read(FIXTURE)) == fixture_sha
                assert not [p for p in names if p.startswith(RUNTIME)]
                print(f'Test-only EPUB sha256 {fixture_sha} {FIXTURE}')
            else:
                assert not [p for p in names if p.endswith('.epub') or p.startswith('assets/fixtures')]
                actual = {p: sha(zipped.read(p)) for p in names if p.startswith(RUNTIME)}
                assert actual == desired == {p: sha((ROOT / MAIN / p).read_bytes()) for p in desired}
                for name, digest in sorted(actual.items()):
                    print(f'Production asset sha256 {digest} {name}')
                assert [p for p in names if p.endswith('.so')] == [LIBRARY]
                assert zipped.getinfo(LIBRARY).compress_type == zipfile.ZIP_STORED
                binary = zipped.read(LIBRARY)
                assert sha(binary) == sha(native.read_bytes())
                with tempfile.TemporaryDirectory() as temp:
                    artifact = Path(temp) / 'jni.so'
                    artifact.write_bytes(binary)
                    readelf = subprocess.check_output([ELF, '-lW', artifact], text=True)
                    loads = [line.split()[-1] for line in readelf.splitlines() if line.strip().startswith('LOAD ')]
                    assert loads and set(loads) == {'0x4000'}, readelf
                    print(f'{len(loads)} packaged ELF LOAD segments aligned at 16384, JNI sha256 {sha(binary)}')
        check_package(apk, test)


if __name__ == '__main__':
    main()
