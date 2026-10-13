"""Extract translation inputs only from the supplied original Doomed artifact."""
import json, re, pathlib, collections, hashlib, zipfile
ROOT = pathlib.Path(__file__).resolve().parents[1]
AUDIT = ROOT / 'audit'
RES = ROOT / 'src/main/resources'
def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
jar = ROOT.parent / 'minecraft/mods/doomedmatu-0.4.7.3.jar'
save(AUDIT / 'artifact.json', {'file': str(jar), 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest()})
strings = collections.defaultdict(list)
excluded = []
allowed = ('/client/screen/', '/client/hud/', '/client/tablet/', '/clinical/', '/journal/', '/item/')
extra = ('Quips', 'QuipHandler', 'BodyPart', 'CarePlan', 'ClientLeutenDialogueResources', 'LeutenCodedClient', 'DialoguePresets', 'ClientDoomedData', 'ClientMoodleText', 'ClientOripathyPresentation', 'ChemTooltips', 'WoundUi', 'BloodUi', 'ClientAlerts')
methods=json.loads((AUDIT/'methods.json').read_text(encoding='utf8'))
# Config comments are visible in RulesScreen, but are not themselves rendering methods.
config_source=(AUDIT/'decompiled/net/mattlives/doomedmatu/config/DoomedConfig.java').read_text(encoding='utf8')
rule_comments=[]
for call in re.finditer(r'builder\.comment\((?:new String\[\]\{)?((?:"(?:\\.|[^"\\])*"\s*,?\s*)+)(?:\})?\)',config_source):
    parts=[json.loads(s) for s in re.findall(r'"(?:\\.|[^"\\])*"',call.group(1))]
    text='\n'.join(parts)
    rule_comments.append(text)
    strings[text].append({'owner':'net/mattlives/doomedmatu/config/DoomedConfig$Holder','method':'<init>','visible_consumer':'RulesScreen'})
save(AUDIT/'rule-comments-source.json',rule_comments)
boundaries={(m['owner'],m['name']) for m in methods if any('/Component.m_237113_' in c or '/Font.' in c or '/GuiGraphics.' in c for c in m['calls'])}
for c in json.loads((AUDIT / 'constants.json').read_text(encoding='utf-8')):
    text = c['text']
    owner = c['owner']
    if owner.endswith('/minigame/MatuEncounter') and c['method'] in ('step','command','finishFoodUse','respond','flirt','nextFight','<clinit>') and re.search(r'[A-Za-z]',text) and re.search(r'\s|[.!?]',text):
        strings[text].append({'owner':owner,'method':c['method'],'recipe':c['recipe'],'visible_consumer':'MatuEncounterScreen'})
        continue
    reason = None
    if not (any(x in owner for x in allowed) or owner.rsplit('/',1)[-1].split('$')[0] in extra or (owner,c['method']) in boundaries): reason = 'Outside the text-source inventory'
    elif not re.search('[A-Za-z]{2}', text): reason = 'Numbers, separators or glyphs'
    elif re.fullmatch(r'[a-z0-9_.:/-]+',text) and not re.fullmatch('[a-z]+',text): reason = 'Resource path, translation key or identifier'
    elif not re.search(r'\s',text) and '/' in text and re.search(r'\.(png|ogg|json|csv|txt|glsl|md)',text): reason = 'Asset or documentation path'
    elif any(x in text for x in ('java.', 'net.minecraft', 'net.mattlives', '[Doomed', 'Failed to ', 'Unknown packet', 'doomedmatu:', 'minecraft:', 'Argument', 'Exception', 'schema', '\\s+', '\\b')): reason = 'Internal diagnostic or code token'
    elif c['method'] in ('values','valueOf','key','getKey','id','getId','parse','fromId','fromKey'): reason = 'Identifier API'
    elif c['method'] == '<clinit>' and re.fullmatch('[A-Z][A-Z0-9_]+',text) and ' ' not in text: reason = 'Enum or internal constant; visible use audited separately'
    elif re.fullmatch(r'[a-z]+(?:_[a-z0-9]+)+',text): reason = 'Internal snake-case identifier'
    elif re.fullmatch('[a-z]+',text) and c['method'] == '<clinit>': reason = 'Unqualified registry or condition key'
    elif re.fullmatch(r'<[a-z_]+>',text): reason = 'Substitution token without visible text'
    elif not re.search(r'\s',text) and re.search(r'[a-z][A-Z]',text) and text not in ('SpO2','MatuOS','WITNESS','Neurograft','Regenex','Nocifen','Revivol','Biochem','Roselight'): reason = 'NBT or Java camel-case identifier'
    elif text.startswith(('[^','#?[','\\')): reason = 'Regular expression or validation mask'
    if reason:
        excluded.append({**c,'reason':reason}); continue
    strings[text].append({'owner':owner,'method':c['method'],'recipe':c['recipe']})
with zipfile.ZipFile(jar) as z:
    encounter=json.loads(z.read('data/doomedmatu/matu_encounter.json'))
    for fight in encounter['fights']:
        for line in fight.get('dialogue',[]):strings[line].append({'resource':['data/doomedmatu/matu_encounter.json','fights','dialogue']})
    lang = json.loads(z.read('assets/doomedmatu/lang/en_us.json'))
    resources = {}
    def walk(value, path):
        if isinstance(value,dict):
            for key,item in value.items():
                if key != '$schema': walk(item,path+[key])
        elif isinstance(value,list):
            for i,item in enumerate(value): walk(item,path+[i])
        elif isinstance(value,str): strings[value].append({'resource':path})
    for name in ('moodle/text.json','oripathy/presentation.json','dialogue/leuten_coded.json'):
        resources[name] = json.loads(z.read('assets/doomedmatu/'+name)); walk(resources[name],[name])
    # Empty quips.json is not populated with invented keys or lines.
    for key,value in lang.items(): strings[value].append({'key':key})
    # NPC dialogue is visible text as well, while IDs, commands and node links stay intact.
    for name in z.namelist():
        if name.startswith('data/doomedmatu/doomed_dialogue/') and name.endswith('.json'):
            data=json.loads(z.read(name))
            for line in data.get('lines',[]):
                if isinstance(line,str):strings[line].append({'resource':[name,'lines']})
            def npc(v,path):
                if isinstance(v,dict):
                    for k,x in v.items():
                        if k in ('text','label','greeting') and isinstance(x,str): strings[x].append({'resource':[name]+path+[k]})
                        elif isinstance(x,(dict,list)): npc(x,path+[k])
                elif isinstance(v,list):
                    for i,x in enumerate(v): npc(x,path+[i])
            npc(data,[])
save(AUDIT / 'visible-inputs.json', strings)
save(AUDIT / 'excluded-inputs.json', excluded)
save(AUDIT / 'language-source.json', lang)
save(AUDIT / 'resource-source.json', resources)
print(f'{len(strings)} unique translation inputs, {len(lang)} language keys, {len(excluded)} excluded constant occurrences')
