import json, pathlib, hashlib, zipfile, xml.etree.ElementTree as ET, io
root = pathlib.Path(__file__).resolve().parents[1]
def only_jar(module, prefix):
    found = [p for p in (root/module/'build/libs').glob(prefix + '-*.jar') if not p.name.endswith('-sources.jar')]
    assert len(found) == 1, found
    return found[0]
# One mod: AscensionLib nests domain, store and sqlite-jdbc and also carries the /ascend wiring.
jar = only_jar('ascensionlib', 'AscensionLib')
with zipfile.ZipFile(jar) as z:
    mod = json.loads(z.read('fabric.mod.json'))
    nested = {pathlib.Path(j['file']).name: z.read(j['file']) for j in mod['jars']}
    domain_name = next(n for n in nested if n.startswith('domain-'))
    store_name = next(n for n in nested if n.startswith('store-'))
    sqlite_name = next(n for n in nested if n.startswith('sqlite-jdbc-'))
    with zipfile.ZipFile(io.BytesIO(nested[domain_name])) as domain:
        names = domain.namelist()
        for cls in ['Progression', 'RewardPolicy', 'v1/RankedProgression', 'v1/MaterialWallet', 'v1/ProfileV1']:
            assert f'com/cobbleascend/domain/{cls}.class' in names, cls
        for name in ['balance.json', 'affixes.json', 'ranked-catalog.json', 'enemy-tiers.json']:
            bundled = json.loads(domain.read('cobbleascend/' + name))
            source = json.loads((root/'design'/name).read_text(encoding='utf-8-sig'))
            assert bundled == source, name
    with zipfile.ZipFile(io.BytesIO(nested[store_name])) as store:
        assert 'com/cobbleascend/store/ProgressionStore.class' in store.namelist()
    with zipfile.ZipFile(io.BytesIO(nested[sqlite_name])) as sqlite:
        assert any(n.startswith('org/sqlite/native/Windows/') for n in sqlite.namelist()), 'native libraries missing'
    assert mod['depends']['cobblemon'] == '1.8.1'
    assert mod['id'] == 'ascensionlib' and 'cobbleraids' not in mod['depends']
    for cls in ['AscensionLib', 'AscensionApi', 'ProfileService', 'AscensionRuntime', 'AscendWiring']:
        assert f'com/ascensionlib/{cls}.class' in z.namelist(), cls
def totals(module):
    tests = [ET.parse(p).getroot().attrib for p in (root/module/'build/test-results/test').glob('TEST-*.xml')]
    return {k: sum(int(t[k]) for t in tests) for k in ['tests', 'failures', 'errors', 'skipped']}
summary = {m: totals(m) for m in ('domain', 'store')}
for m, s in summary.items():
    assert s['tests'] > 0 and s['failures'] == s['errors'] == 0, (m, s)
def live(n):
    return (root/f'verification/live-run-{n}.log').read_text(encoding='utf-8', errors='replace')
first, missing, mismatch, recovered = live(1), live(3), live(4), live(5)
assert 'Showdown has been started!' in first and 'SQLite driver OK' in first
assert 'Created a new progression store for this world' in first and 'Done (' in first
assert 'canonical progression store active' in first
assert 'Created a new progression store' not in live(2) and 'canonical progression store active' in live(2)
assert 'progression database is missing' in missing and 'DISABLED' in missing
assert 'authority mismatch' in mismatch
assert 'canonical progression store active' in recovered
report = {'artifact': str(jar), 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'tests': summary,
          'nested': sorted(nested),
          'packaging': 'Nested domain, store and sqlite-jdbc jars verified; bundled catalogs match design/',
          'loader': 'Live dev-server runs (verification/live-run-1..5.log, no player): store created on first start, reopened with the same authority after restart, progression disabled (not reset) when the database is missing or the authority mismatches, recovered when restored. SQLite native library loaded under Fabric Knot on the dev classpath (not nested jar-in-jar). No capture, hatch, level-up, PC or trade was exercised.',
          'runtime': {'minecraft': '1.21.1', 'cobblemon': '1.8.1+1.21.1', 'fabricLoader': '0.17.2', 'fabricApi': '0.116.6+1.21.1', 'java': '21.0.12'},
          'cobblemonArtifact': {'modrinthVersion': 'gBW3vLC7', 'sha512': 'e9c475c6a8e73bfb378445944812f6e31271cdf50d3227124b44b31c295bf7e9085246ea05d005efff9b7227e7b4e3e34ff1480b1a4b3b97211b5fa127d234ed'}}
(root/'verification/summary.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
