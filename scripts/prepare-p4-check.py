"""Prepare only ignored, local test packs; never include restricted assets in source/JAR."""
import pathlib, json, hashlib, shutil, sys, base64, struct
repo=pathlib.Path(__file__).resolve().parents[1]
root=repo/'build/p4-check'
source=repo/'build/p33-check/client/resourcepacks/agnor-local/assets/agnor/models/face'
assert source.is_dir(), 'Prepare the accepted P3 face resources first'
for role in ('server','clientA','clientB','integrated'):
    run=root/role
    pack=run/'mods/agnor/packs/firefly'
    model=pack/'models/face'
    model.mkdir(parents=True,exist_ok=True)
    for file in source.iterdir():
        if file.suffix in ('.glb','.png'): shutil.copyfile(file,model/file.name)
    shutil.copyfile(repo/'local-assets/firefly/capabilities.json',pack/'rig.json')
    assets=[]
    for file in sorted(pack.rglob('*')):
        if not file.is_file() or file.name=='manifest.json': continue
        path=file.relative_to(pack).as_posix()
        assets.append(dict(id='firefly:'+path.replace('.','_'),path=path,format={'.glb':'glb','.png':'png','.json':'json'}[file.suffix],sha256=hashlib.sha256(file.read_bytes()).hexdigest()))
    body=next(a['id'] for a in assets if a['path']=='models/face/part-000.glb')
    rig=next(a['id'] for a in assets if a['path']=='rig.json')
    manifest=dict(schemaVersion=1,apiVersion=1,id='firefly',version='1.0.0',priority=0,dependencies={},overrides=[],
        provenance=dict(source='local:accepted-p3-firefly',author='See local replacement register',license='Restricted local development only',rights='LOCAL_ONLY'),
        assets=assets,definitions=[dict(id='firefly:companion',revision=1,displayName='P4 External Firefly',prompt='Your name is Firefly. Mention starlight briefly when greeting.',model=body,rig=rig)])
    (pack/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    if role!='server':
        (run/'options.txt').write_text('resourcePacks:["vanilla"]\nguiScale:2\nmaxFps:60\nrenderDistance:4\nsimulationDistance:5\nentityDistanceScaling:1.0\npauseOnLostFocus:false\nsoundCategory_master:0.0\n',encoding='ascii')
server=root/'server'
(server/'eula.txt').write_text('eula=true\n',encoding='ascii')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25586\nonline-mode=false\nlevel-name=p4-fixture\nlevel-type=minecraft:flat\nview-distance=4\nsimulation-distance=2\nspawn-protection=0\n',encoding='ascii')
for save in (server/'p4-fixture/agnor',root/'integrated/saves/p4-world/agnor'):
    save.mkdir(parents=True,exist_ok=True)
    (save/'main-dialogue.properties').write_text('version=1\nenabled=true\nendpoint=http://127.0.0.1:11434\nadapter=OLLAMA\nmodel=ternary-bonsai-text-20k:latest\ntimeoutMillis=90000\nmaxOutputTokens=48\nworkers=1\nqueue=8\nmaxResponseBytes=65536\n',encoding='ascii')
(root/'coord').mkdir(parents=True,exist_ok=True)
if '--sync' in sys.argv:
    doc=json.loads((repo/'src/p3Check/resources/rig-morph.gltf').read_text(encoding='utf-8'))
    binary=base64.b64decode(doc['buffers'][0].pop('uri').split(',')[1]);binary+=b'\0'*((-len(binary))%4)
    doc['materials'][0]['extensions']={'KHR_materials_unlit':{}}
    doc['materials'][0]['pbrMetallicRoughness']['baseColorFactor']=[1,.7,.1,1]
    text=json.dumps(doc,separators=(',',':')).encode();text+=b' '*((-len(text))%4)
    model=struct.pack('<III',0x46546c67,2,28+len(text)+len(binary))+struct.pack('<II',len(text),0x4e4f534a)+text+struct.pack('<II',len(binary),0x004e4942)+binary
    for role in ('server','integrated'):
        pack=root/role/'mods/agnor/packs/public';pack.mkdir(parents=True,exist_ok=True)
        files={'body.glb':model,'notes.txt':('AGNOR original transfer fixture. '*1800).encode()}
        for name,data in files.items():(pack/name).write_bytes(data)
        manifest=dict(schemaVersion=1,apiVersion=1,id='public',version='1.0.0',priority=0,dependencies={},overrides=[],
            provenance=dict(source='https://github.com/klonadol/modeagnor',author='AGNOR original synthetic fixture',license='CC0-1.0',rights='ALLOWED'),
            assets=[dict(id='public:'+name.replace('.','_'),path=name,format='glb' if name.endswith('.glb') else 'text',sha256=hashlib.sha256(data).hexdigest()) for name,data in files.items()],
            definitions=[dict(id='public:companion',revision=1,displayName='P4 Lumen',prompt='Your name is Lumen. P4_TRANSFER_STYLE: Speak warmly about starlight.',model='public:body_glb',rig='')])
        (pack/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    file=root/'clientB/mods/agnor/packs/firefly/manifest.json';manifest=json.loads(file.read_text(encoding='utf-8'))
    manifest['definitions'][0]['displayName']='Intentionally mismatched local version'
    file.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print('Prepared local-only P4 packs at',root)
