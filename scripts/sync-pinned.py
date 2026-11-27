#!/usr/bin/env python3
"""Project exact files from pinned Git objects and verify every byte."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parents[1]
MAIN = Path('app/src/main')
KOTLIN = MAIN / 'kotlin/io/reader/rebuild'


def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(WORKSPACE / repo), *args])


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--write', action='store_true')
    mode.add_argument('--check', action='store_true')
    args = parser.parse_args()
    pins = json.loads((ROOT / 'pins.json').read_bytes())
    sources = dict(core='Reader-Core', ui='Reader-UI', harmony='Reader-for-HarmonyOS')

    def blob(which, path):
        return git(sources[which], 'show', f'{pins[which]}:{path}')

    manifest_path = 'reader_ebook_runtime/manifest.json'
    manifest_bytes = blob('ui', manifest_path)
    manifest = json.loads(manifest_bytes)
    assert manifest['harmonyCommit'] == pins['harmony']
    harmony_dependency_path = 'scripts/core-dependency.json'
    harmony_dependency = blob('harmony', harmony_dependency_path)
    source_paths = dict(core={}, ui={})
    core_map = source_paths['core']
    core_map['bindings/android/generated/ReaderProtocolModels.kt'] = KOTLIN / 'core/generated/ReaderProtocolModels.kt'
    for path in git('Reader-Core', 'ls-tree', '-r', '--name-only', pins['core'],
                    'bindings/android/src/main/java', 'bindings/android/src/main/kotlin').decode().splitlines():
        if path.rsplit('/', 1)[-1] == 'ReaderEventListener.kt':
            continue  # Direct callbacks are not the queue ABI used by this client.
        core_map[path] = MAIN / Path(path).relative_to('bindings/android/src/main')
    for name in ('local-reading.txt', 'reading-lifecycle.json', 'scalar-selection.json'):
        core_map[f'protocol/fixtures/m1/{name}'] = Path(f'app/src/test/resources/m1/{name}')
    core_map['tools/m1-rpc-smoke/hold_storage_write.py'] = Path('app/src/test/resources/m1/hold_storage_write.py')
    ui_map = source_paths['ui']
    for name in ('ReaderThemeTokens', 'ReaderDesignMetrics', 'ReaderIconRegistry'):
        ui_map[f'theme/generated/android/{name}.kt'] = KOTLIN / f'design/generated/{name}.kt'
    for path in manifest['productionResources']:
        ui_map[path] = MAIN / 'assets' / path
    for path in manifest['testResources']:
        ui_map[path] = Path('app/src/androidTest/assets/fixtures') / Path(path).name
    assert len(manifest['productionResources']) == 3

    inputs = {harmony_dependency_path: sha(harmony_dependency), manifest_path: sha(manifest_bytes)}
    for path in ('reader-command', 'reader-event', 'reader-runtime-config'):
        name = f'protocol/{path}.schema.json'
        inputs[name] = sha(blob('core', name))
    for path, digest in {**manifest['designInputs'], **manifest['toolInputs']}.items():
        assert sha(blob('ui', path)) == digest, path
        inputs[path] = digest
    upstream_hashes = {**manifest['productionResources'], **manifest['testResources'], **manifest['generatedDesign']}
    outputs = {}
    mismatches = []

    def update(target, data):
        absolute = ROOT / target
        if args.write:
            absolute.parent.mkdir(exist_ok=True, parents=True)
            absolute.write_bytes(data)
        if not absolute.is_file() or absolute.read_bytes() != data:
            mismatches.append(str(target))

    for source, entries in source_paths.items():
        for path, target in entries.items():
            data = blob(source, path)
            digest = sha(data)
            assert upstream_hashes.get(path, digest) == digest, path
            outputs[str(target)] = {'source': f'{source}:{path}', 'sha256': digest}
            update(target, data)
    storage_fixture = 'crates/reader-storage/tests/fixtures/library47-schema.sql'
    storage_bytes = blob('core', storage_fixture)
    lines = storage_bytes.splitlines(keepends=True)
    assert 800 < len(lines) <= 1600
    inputs[storage_fixture] = sha(storage_bytes)
    for number, start in enumerate(range(0, len(lines), 800), 1):
        target = Path(f'app/src/test/resources/m1/library47-schema.{number}.sql')
        data = b''.join(lines[start:start + 800])
        outputs[str(target)] = {'source': f'core:{storage_fixture}[part {number}]', 'sha256': sha(data)}
        update(target, data)
    runtime = MAIN / 'assets/reader_ebook_runtime'
    actual_resources = {str(path.relative_to(ROOT)) for path in (ROOT / runtime).glob('**/*') if path.is_file()}
    assert actual_resources == {p for p in outputs if p.startswith(str(runtime) + '/')}, actual_resources
    metadata = {'pins': pins, 'harmonyPairedCore': json.loads(harmony_dependency)['coreCommit'],
                'inputs': inputs, 'outputs': outputs}
    update(Path('pinned-inputs.sha256.json'), (json.dumps(metadata, sort_keys=True, indent=2) + '\n').encode())
    if mismatches:
        raise SystemExit('Pinned output drift: ' + ', '.join(mismatches))
    print(f'{len(inputs)} input hashes and {len(outputs)} pinned output hashes verified, 0 drift')


if __name__ == '__main__':
    main()
