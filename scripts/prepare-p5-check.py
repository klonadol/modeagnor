"""Original synthetic Lorebook fixture, no models, secrets or user worlds."""
import pathlib,json,hashlib
root=pathlib.Path(__file__).resolve().parents[1]/'build/p5-check'
for role in ('server','clientA','clientB','integrated'):
    run=root/role;pack=run/'mods/agnor/packs/facts';pack.mkdir(parents=True,exist_ok=True)
    def entry(id,content,mode='ALWAYS',triggers=[]):
        return dict(id=id,title='Fixture knowledge',content=content,mode=mode,triggers=triggers,priority=5,role='user',enabled=True,definition='agnor:dev',dimension='*')
    lore=json.dumps(dict(type='agnor:lorebook',schemaVersion=1,entries=[entry('facts:pack','P5 pack knowledge'),entry('facts:gate','Код ворот PACK-AMBER.','KEYWORD',['ворота'])]),ensure_ascii=False).encode()
    (pack/'lore.json').write_bytes(lore)
    manifest=dict(schemaVersion=1,apiVersion=1,id='facts',version='1.0.0',priority=0,dependencies={},overrides=[],provenance=dict(source='https://github.com/klonadol/modeagnor',author='AGNOR original test fixture',license='CC0-1.0',rights='ALLOWED'),
        assets=[dict(id='facts:lore',path='lore.json',format='json',sha256=hashlib.sha256(lore).hexdigest())],definitions=[dict(id='facts:fixture',revision=1,displayName='Fixture',prompt='',model='',rig='')])
    (pack/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False),encoding='utf-8')
    if role!='server':(run/'options.txt').write_text('resourcePacks:["vanilla"]\nmaxFps:60\nrenderDistance:3\nsimulationDistance:5\nguiScale:2\npauseOnLostFocus:false\nsoundCategory_master:0.0\n',encoding='ascii')
server=root/'server';(server/'eula.txt').write_text('eula=true\n',encoding='ascii')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25587\nonline-mode=false\nlevel-name=p5-fixture\nlevel-type=minecraft:flat\nview-distance=3\nsimulation-distance=2\nspawn-protection=0\n',encoding='ascii')
for save in (server/'p5-fixture/agnor',root/'integrated/saves/p5-world/agnor'):
    save.mkdir(parents=True,exist_ok=True)
    (save/'main-dialogue.properties').write_text('version=1\nenabled=true\nendpoint=http://127.0.0.1:11434\nadapter=OLLAMA\nmodel=ternary-bonsai-text-20k:latest\ntimeoutMillis=90000\nmaxOutputTokens=80\nworkers=1\nqueue=8\nmaxResponseBytes=65536\n',encoding='ascii')
(root/'coord').mkdir(parents=True,exist_ok=True)
print('Prepared original P5 fixture',root)
