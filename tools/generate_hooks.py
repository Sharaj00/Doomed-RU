import json,pathlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
methods=json.loads((ROOT/'audit/methods.json').read_text(encoding='utf8'))
names={'txt','txtW','txtR','txtC','wrapped','wrappedHeight','ellipsize','fitTriageText','fit','fitToWidth','fitScale','fitCentered','drawFitString','drawFitCentered','drawWrapped','splitNarrator','wrappedRescueLine','wrap','drawScaledString','drawCenteredScaledString','txtEllipsized','txtCEllipsized','txtREllipsized','drawFullTextTooltip','queueAffection','showAffection'}
entries={}
classes=set(json.loads((ROOT/'audit/classes.json').read_text(encoding='utf8')))
for m in methods:
    boundary=any('/Font.' in c or '/GuiGraphics.' in c for c in m['calls'])
    if m['owner'].startswith('net/mattlives/doomedmatu/client/') and (m['name'] in names or boundary) and 'Ljava/lang/String;' in m['desc'].split(')')[0]:
        entries.setdefault(m['owner'],[]).append(m['name']+m['desc'])
    if m['owner'].endswith('/ClientQuipStore') and m['name']=='put':entries.setdefault(m['owner'],[]).append(m['name']+m['desc'])
extras=['ClientMoodleText','ClientOripathyPresentation','ClientLeutenDialogueResources','ClientQuipStore','LeutenCodedClient']
classes.update('net.mattlives.doomedmatu.client.'+x for x in extras)
classes.update(o.replace('/','.') for o in entries)
classes.add('net.mattlives.doomedmatu.body.BodyPart')
classes.add('net.mattlives.doomedmatu.minigame.MatuEncounter')
classes.add('net.mattlives.doomedmatu.client.screen.DoomedSettingsScreen$SearchEntry')
res=ROOT/'src/main/resources'
(res/'doomed_ru_fresh.entries.json').write_text(json.dumps(entries,indent=2),encoding='utf8')
(res/'doomed_ru_fresh.mixins.json').write_text(json.dumps({'required':True,'minVersion':'0.8','package':'local.doomed.russianfresh.mixin','compatibilityLevel':'JAVA_17','plugin':'local.doomed.russianfresh.TextPlugin','client':['DoomedTextMixin'],'injectors':{'defaultRequire':1}},indent=2),encoding='utf8')
targets=',\n    '.join(json.dumps(c) for c in sorted(classes))
q=ROOT/'src/main/java/local/doomed/russianfresh/mixin/DoomedTextMixin.java'
q.parent.mkdir(parents=True,exist_ok=True)
q.write_text('package local.doomed.russianfresh.mixin;\nimport org.spongepowered.asm.mixin.Mixin;\n@Mixin(targets={\n    '+targets+'\n},remap=false)\npublic abstract class DoomedTextMixin {}\n',encoding='utf8')
(ROOT/'audit/transform-targets.json').write_text(json.dumps(sorted(classes),indent=2),encoding='utf8')
(res/'doomed_ru_fresh.targets.json').write_text(json.dumps(sorted(classes),indent=2),encoding='utf8')
print(len(classes),'scoped classes;',sum(map(len,entries.values())),'full-text entry hooks')
