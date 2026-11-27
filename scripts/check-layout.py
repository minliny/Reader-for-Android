#!/usr/bin/env python3
"""Check authored line limits, dependency direction and isolation in tracked source."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
package = Path('app/src/main/kotlin/io/reader/rebuild')
levels = {'core': set(), 'design': set(), 'host': {'core'}, 'features': {'core', 'host', 'design'}}
pattern = re.compile(r'^import\s+io\.reader\.rebuild\.([a-z]+)\.', re.M)
files = sorted(path for path in root.rglob('*') if path.is_file()
               and path.suffix in {'.kt', '.java', '.sql', '.py', '.kts', '.sh', '.xml', '.js', '.html'}
               and not any(part in {'.build', '.gradle', 'build'} for part in path.relative_to(root).parts))
counts = {}
for path in files:
    rel = path.relative_to(root)
    contents = path.read_text()
    if 'generated' not in rel.parts:
        counts[str(rel)] = len(contents.splitlines())
    if rel.is_relative_to(package) and path.suffix == '.kt':
        owner = rel.relative_to(package).parts[0]
        for imported in pattern.findall(contents):
            assert imported == owner or imported in levels[owner], (rel, imported)
        if owner != 'features':
            assert 'import io.reader.rebuild.features' not in contents, rel
    if rel.is_relative_to(Path('app/src/main')):
        assert 'androidx.test' not in contents and 'org.junit' not in contents, rel
        assert 'com.reader.core.host.' not in contents, rel
longest = max(counts, key=counts.get)
assert counts[longest] <= 800, (longest, counts[longest])
for path in (root / 'app/src/main').rglob('*'):
    assert path.suffix != '.epub', path
print(f'{len(counts)} non-generated source files, longest {counts[longest]} lines ({longest})')
print('Dependency direction: features → design/host/core; host → core; design and core independent')
print('Production source isolation: no tests, EPUB fixture or legacy host-adapter in main')
