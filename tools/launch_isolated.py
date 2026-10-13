"""Launch the release artifact with cached production Forge, without using accounts or the live profile."""
import pathlib,json,zipfile,subprocess,os,shutil
ROOT=pathlib.Path(__file__).resolve().parents[1]
LAUNCHER=pathlib.Path('C:/Games/PineconeMC')
LIB=LAUNCHER/'libraries'
RUN=ROOT/'production-run'
RUN.mkdir(exist_ok=True);(RUN/'mods').mkdir(exist_ok=True);(RUN/'temp').mkdir(exist_ok=True)
for name in ('doomedmatu-0.4.7.3.jar','doomedguns-0.2.1.jar'):
    shutil.copy2(ROOT.parent/'minecraft/mods'/name,RUN/'mods'/name)
for name in ('emi-1.1.24+1.20.1+forge.jar','jei-1.20.1-forge-15.20.0.130.jar'):
    shutil.copy2(ROOT.parent/'minecraft/mods'/name,RUN/'mods'/name)
shutil.copy2(ROOT/'build/libs/DoomedRU-2.0.0.jar',RUN/'mods/DoomedRU-2.0.0.jar')
(RUN/'options.txt').write_text('lang:ru_ru\nguiScale:2\n',encoding='utf8')
installer=LIB/'net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-installer.jar'
with zipfile.ZipFile(installer) as z:version=json.loads(z.read('version.json'))
def allowed(entry):
    result=not entry.get('rules')
    for rule in entry.get('rules',[]):
        os_rule=rule.get('os',{})
        if os_rule.get('name','windows')=='windows' and os_rule.get('arch','x86_64') not in ('x86','arm64') and not rule.get('features'):
            result=rule['action']=='allow'
    return result
libraries={}
for data in [version]+[json.loads((LAUNCHER/'meta'/uid/file).read_text(encoding='utf8')) for uid,file in [('net.minecraft','1.20.1.json'),('org.lwjgl3','3.3.1.json'),('net.minecraftforge','47.4.23.json')]]:
    for entry in data['libraries']:
        if not allowed(entry):continue
        parts=entry['name'].split(':');group,artifact,ver=parts[:3];classifier='-'+parts[3] if len(parts)>3 else ''
        path=LIB/group.replace('.','/')/artifact/ver/(artifact+'-'+ver+classifier+'.jar')
        if not path.exists():raise FileNotFoundError(path)
        libraries[(group,artifact,classifier)]=path
for name in ('forge-1.20.1-47.4.23-client.jar','forge-1.20.1-47.4.23-universal.jar'):
    libraries[('forge',name,'')]=installer.parent/name
extra=LIB/'net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-extra.jar'
libraries[('minecraft','extra','')]=extra
cp=';'.join(map(str,libraries.values()))
substitutions={'library_directory':str(LIB),'classpath_separator':';','version_name':'1.20.1'}
args=[]
for arg in version['arguments']['jvm']:
    for k,v in substitutions.items():arg=arg.replace('${'+k+'}',v)
    args.append(arg)
args+=['-Xmx3G','-Ddoomed.ru.audit=true','-Djava.io.tmpdir='+str(RUN/'temp'),'-DlegacyClassPath='+cp,'-cp',cp,version['mainClass']]
args+=['--username','TranslationAudit','--version','1.20.1','--gameDir',str(RUN),'--assetsDir',str(LAUNCHER/'assets'),'--assetIndex','5','--uuid','00000000000000000000000000000042','--accessToken','0','--userType','legacy','--width','1280','--height','800']
args+=version['arguments']['game']
with (ROOT/'audit/production-console.log').open('w',encoding='utf8') as output:
    process=subprocess.Popen([str(LAUNCHER/'java/java-runtime-gamma/bin/java.exe')]+args,cwd=RUN,stdout=output,stderr=subprocess.STDOUT)
    (ROOT/'audit/production-pid.txt').write_text(str(process.pid))
    print('Isolated production client PID',process.pid,flush=True)
    print('Exit code',process.wait(),flush=True)
