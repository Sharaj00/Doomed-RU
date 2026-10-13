import json,pathlib,re,collections
ROOT=pathlib.Path(__file__).resolve().parents[1]
AUDIT=ROOT/'audit'; RES=ROOT/'src/main/resources'
def read(name):return json.loads((AUDIT/name).read_text(encoding='utf8'))
def save(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
inputs=read('visible-inputs.json');draft=read('draft.json')
dictionary={s:draft[s] for s in inputs}
reviewed={}
for name in ('reviewed.json','codex_reviewed.json','ui_reviewed.json','subtitles_reviewed.json','editorial_reviewed.json','update_reviewed.json','traits_reviewed.json'):
    p=ROOT/'tools'/name
    if p.exists():reviewed.update(json.loads(p.read_text(encoding='utf8')))
dictionary.update(reviewed)
for s,t in list(dictionary.items()):
    dictionary[s]=re.sub(r'[ЮУ]НИ[-‑– ]ЗДОРОВЬЕ', 'UNI-HEALTH',t,flags=re.I)
    if '\n' in s:dictionary.setdefault(re.sub(r'\s+',' ',s).strip(),dictionary[s])
original=read('language-source.json')
lang={k:dictionary[v] for k,v in original.items()}
assert set(lang)==set(original),'Language key set must be identical to the supplied Doomed artifact'
format_token=re.compile(r'§.|%(?:\d+\$)?[-+0 #]*(?:\d+)?(?:\.\d+)?(?:[bBhHsScCdoxXeEfgGaA]|[tT][HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc]|[%n])(?![A-Za-z])|<[a-zA-Z_]+>')
for source,target in dictionary.items():
    a=collections.Counter(format_token.findall(source));b=collections.Counter(format_token.findall(target))
    if a!=b:raise ValueError(f'Formatting changed: {source!r}: {a} -> {b}')
    if '\x01' in source:
        expect=collections.Counter('{'+str(i)+'}' for i in range(source.count('\x01')))
        got=collections.Counter(re.findall(r'\{\d+\}',target))
        if got!=expect:raise ValueError(f'Dynamic placeholders changed: {source!r}')
save(RES/'assets/doomedmatu/lang/ru_ru.json',lang)
def translate(value):
    if isinstance(value,dict):return {k:(v if k=='$schema' else translate(v)) for k,v in value.items()}
    if isinstance(value,list):return [translate(v) for v in value]
    if isinstance(value,str):return dictionary[value]
    return value
for name,data in read('resource-source.json').items():save(RES/'assets/doomed_ru_fresh'/name,translate(data))
# Casing changes at visible consumers are derived only from approved Doomed text.
for source,target in list(dictionary.items()):
    if len(source)<=80 and '\x01' not in source and not format_token.search(source):
        dictionary.setdefault(source.lower(),target.lower())
        dictionary.setdefault(source.upper(),target.upper())
# Identity mappings are intentionally preserved tokens, not translations.
dictionary={s:t for s,t in dictionary.items() if s!=t}
save(RES/'assets/doomed_ru_fresh/literal_ru.json',dictionary)
methods=read('methods.json')
system_boundaries={(m['owner'],m['name']) for m in methods if '/client/' not in m['owner'] and any('/Component.m_237113_' in c for c in m['calls'])}
server=[s for s,origins in inputs.items() if len(s.replace('\x01',''))>=30 and any((o.get('owner'),o.get('method')) in system_boundaries for o in origins)]
save(RES/'assets/doomed_ru_fresh/server_messages.json',server)
save(AUDIT/'coverage.json',{'original_language_keys':len(original),'translated_language_keys':len(lang),'missing_language_keys':[],'extra_language_keys':[], 'literal_and_template_entries':len(dictionary),'manual_overrides':len(reviewed),'resource_counts':{k:len(v) for k,v in read('resource-source.json').items()},'previous_attempts_used':False,'translation_basis':'Machine draft plus local manual terminology, UI and defusal review; complete editorial review is not claimed.'})
print(f'{len(lang)}/{len(original)} language keys; {len(dictionary)} literal/casing/template entries; {len(reviewed)} manual overrides')
