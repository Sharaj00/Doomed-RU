"""Create a resumable draft and validate all protected formatting tokens."""
import json, pathlib, re, time, urllib.request, urllib.parse, concurrent.futures
ROOT=pathlib.Path(__file__).resolve().parents[1]
SOURCE=ROOT/'audit/visible-inputs.json'
OUT=ROOT/'audit/draft.json'
source=json.loads(SOURCE.read_text(encoding='utf-8'))
draft=json.loads(OUT.read_text(encoding='utf-8')) if OUT.exists() else {}
token=re.compile(r'§.|%(?:\d+\$)?[-+0 #]*(?:\d+)?(?:\.\d+)?(?:[bBhHsScCdoxXeEfgGaA]|[tT][HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc]|[%n])(?![A-Za-z])|<[a-zA-Z_]+>|\x01|\x02|\{\d+\}|(?:config|reference|textures|assets|data)/[a-zA-Z0-9_./-]+|https?://[^\s]+|/doom\b')
def protect(s):
    tokens=[]; dynamic=0
    def sub(m):
        nonlocal dynamic
        value=m.group()
        if value=='\x01': value='{'+str(dynamic)+'}'; dynamic+=1
        tokens.append(value); return f'ZXQ{len(tokens)-1}QXZ'
    return token.sub(sub,s),tokens
def translate_batch(batch):
    protected=[protect(re.sub(r'(?<!\n)\n(?!\n)', ' ',s)) for s in batch]
    q='\n'.join(f'⟦{i:03}⟧ '+p[0] for i,p in enumerate(protected))
    url='https://translate.googleapis.com/translate_a/single?'+urllib.parse.urlencode({'client':'gtx','sl':'en','tl':'ru','dt':'t','q':q})
    for attempt in range(5):
        try:
            req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'})
            result=json.loads(urllib.request.urlopen(req,timeout=40).read())
            text=''.join(x[0] for x in result[0] if x[0])
            parts=re.split(r'⟦\s*(\d+)\s*⟧\s*',text)
            found={int(parts[i]):parts[i+1].strip() for i in range(1,len(parts),2)}
            if len(found)!=len(batch): raise ValueError('Batch markers changed')
            out={}
            for i,(original,(p,tokens)) in enumerate(zip(batch,protected)):
                value=found[i]
                for j,t in enumerate(tokens):
                    marker=f'ZXQ{j}QXZ'
                    if value.count(marker)!=1: raise ValueError(f'Formatting token changed: {marker}')
                    value=value.replace(marker,t)
                # Preserve intentional leading/trailing spaces used around dynamic fields.
                value=original[:len(original)-len(original.lstrip())]+value+original[len(original.rstrip()):]
                out[original]=value
            return out
        except Exception:
            if attempt==4:
                if len(batch)>1:
                    merged={}
                    for s in batch: merged.update(translate_batch([s]))
                    return merged
                raise
            time.sleep(1+attempt*2)
pending=[s for s in source if s not in draft]
batches=[]; current=[]; size=0
for s in pending:
    if current and (size+len(s)>2200 or len(current)>=24): batches.append(current);current=[];size=0
    current.append(s);size+=len(s)+12
if current:batches.append(current)
print(f'{len(pending)} strings in {len(batches)} batches',flush=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    futures=[pool.submit(translate_batch,b) for b in batches]
    for count,f in enumerate(concurrent.futures.as_completed(futures),1):
        draft.update(f.result())
        OUT.write_text(json.dumps(draft,ensure_ascii=False,indent=2),encoding='utf-8')
        if count%10==0 or count==len(batches):print(f'{count}/{len(batches)} batches; {len(draft)} translated',flush=True)
