import hashlib
import json
import pathlib
import re
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
original = ROOT.parent / 'minecraft/mods/doomedmatu-0.4.7.3.jar'
release = ROOT / 'build/libs/DoomedRU-2.0.0.jar'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

record = json.loads((ROOT / 'audit/artifact.json').read_text(encoding='utf8'))
assert sha(original) == record['sha256'], 'Original JAR changed'
client = json.loads((ROOT / 'audit/client-result.json').read_text(encoding='utf8'))
assert client['status'] == 'passed', 'Client audit did not pass'
assert sha(ROOT / 'production-run/mods' / release.name) == sha(release), 'Untested release artifact'
with zipfile.ZipFile(original) as archive:
    english = json.loads(archive.read('assets/doomedmatu/lang/en_us.json'))
with zipfile.ZipFile(release) as archive:
    names = archive.namelist()
    russian = json.loads(archive.read('assets/doomedmatu/lang/ru_ru.json'))
    assert set(english) == set(russian), 'Missing or foreign language keys'
    assert not any(n.startswith('assets/') and not n.endswith('/') and not n.startswith(('assets/doomedmatu/', 'assets/doomed_ru_fresh/')) for n in names)
    targets = json.loads(archive.read('doomed_ru_fresh.targets.json'))
    assert all(n.startswith('net.mattlives.doomedmatu.') for n in targets)
    resources = [n for n in names if n.startswith('assets/') and not n.endswith('/')]
    dictionary=json.loads(archive.read('assets/doomed_ru_fresh/literal_ru.json'))
    comments=json.loads((ROOT/'audit/rule-comments-source.json').read_text(encoding='utf8'))
    assert all(re.sub(r'\s+',' ',s).strip() in dictionary for s in comments)
    traits=json.loads((ROOT/'audit/trait-translations.json').read_text(encoding='utf8'))
    assert len(traits)==22
    assert all(dictionary[s]==t for v in traits.values() for s,t in zip(v['english'],v['russian']))
preset=json.loads((ROOT.parent/'minecraft/config/doomedmatu/dialogue_presets/russian.json').read_text(encoding='utf8'))
preset_before=json.loads((ROOT/'audit/backups/russian.before-1.0.1.json').read_text(encoding='utf8'))
assert set(preset)==set(preset_before)
preset_lines=0
for key,old in preset_before.items():
    new=preset[key]
    if isinstance(old,list):
        assert len(old)==len(new)
        for a,b in zip(old,new):
            assert re.findall(r'<[^>]+>',a)==re.findall(r'<[^>]+>',b)
            assert re.search('[\u0400-\u04ff]',b)
            preset_lines+=1
    else:assert old==new
result = {
    'original_sha256': sha(original),
    'release_sha256': sha(release),
    'release': str(release),
    'language_keys': len(russian),
    'core_config_comments':len(comments),
    'traits':len(traits),
    'translated_preset_lines':preset_lines,
    'foreign_language_keys': [],
    'foreign_transform_targets': [],
    'packaged_resources': resources,
    'client_audit': client,
}
(ROOT / 'audit/release-verification.json').write_text(json.dumps(result, indent=2), encoding='utf8')
print(json.dumps({k: v for k, v in result.items() if k != 'packaged_resources'}, indent=2))
