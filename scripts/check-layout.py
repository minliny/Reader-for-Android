#!/usr/bin/env python3
"""Check authored line limits, dependency direction and isolation in tracked source."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
package = Path('app/src/main/kotlin/io/reader/rebuild')
levels = {'core': set(), 'design': set(), 'host': {'core'}, 'features': {'core', 'host', 'design'}}
pattern = re.compile(r'^import\s+io\.reader\.rebuild\.([a-z]+)\.', re.M)
feature_pattern = re.compile(r'^import\s+io\.reader\.rebuild\.features\.([a-z]+)\.', re.M)
feature_edges = {}
files = sorted(path for path in root.rglob('*') if path.is_file()
               and path.suffix in {'.kt', '.java', '.sql', '.py', '.kts', '.sh', '.xml', '.js', '.html', '.md', '.svg', '.json'}
               and not any(part in {'.build', '.gradle', '.kotlin', 'build'} for part in path.relative_to(root).parts))
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
        if owner == 'features':
            feature = rel.relative_to(package).parts[1]
            feature_edges.setdefault(feature, set()).update(
                imported for imported in feature_pattern.findall(contents) if imported != feature
            )
        if owner != 'features':
            assert 'import io.reader.rebuild.features' not in contents, rel
        if owner != 'design':
            assert not re.search(r'\bReaderAppRole\b', contents), rel
    if rel.is_relative_to(Path('app/src/main')):
        assert 'androidx.test' not in contents and 'org.junit' not in contents, rel
        assert 'com.reader.core.host.' not in contents, rel
def visit(feature, active, checked):
    assert feature not in active, ("feature dependency cycle", active, feature)
    if feature in checked:
        return
    for dependency in sorted(feature_edges.get(feature, ())):
        visit(dependency, (*active, feature), checked)
    checked.add(feature)


checked = set()
for feature in sorted(feature_edges):
    visit(feature, (), checked)
longest = max(counts, key=counts.get)
assert counts[longest] <= 800, (longest, counts[longest])
fixture_names = {path.name for directory in ('app/src/test/resources', 'app/src/androidTest/assets')
                 for path in (root / directory).rglob('*') if path.is_file()}
for path in (root / 'app/src/main').rglob('*'):
    assert path.suffix not in {'.epub', '.mobi'} and path.name not in fixture_names, path
print(f'{len(counts)} non-generated source files, longest {counts[longest]} lines ({longest})')
print('Dependency direction: features → design/host/core; host → core; design and core independent')
print('Feature imports are acyclic')
for feature, dependencies in sorted(feature_edges.items()):
    if dependencies:
        print(f"  {feature} → {', '.join(sorted(dependencies))}")
print('ReaderAppRole references outside design: 0')
print('Production source isolation: no test fixtures, book files or legacy host-adapter in main')
