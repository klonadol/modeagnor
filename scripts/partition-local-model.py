"""Local-only lossless rest-pose partition for GemRender's 256 palette slots.
Preserves vertices/weights/materials; each piece has independent flattened joint slots.
Original hierarchy is retained in capability JSON for future host rig mapping.
Not a Content Pack loader. Does not copy assets into source or release artifacts.
"""
import copy, json, pathlib, runpy, struct, sys, math, hashlib
api=runpy.run_path(str(pathlib.Path(__file__).with_name('inspect-models.py')))
source=pathlib.Path(sys.argv[1]); out=pathlib.Path(sys.argv[2]); out.mkdir(parents=True,exist_ok=True)
d,b=api['load'](source); cache={}
def read(a):
    if a not in cache:cache[a]=api['values'](d,b,a)
    return cache[a]
prefix=sys.argv[3] if len(sys.argv)>3 else 'agnor:models/dev'
for i,image in enumerate(d.get('images',[])):
    assert 'bufferView' in image, 'Only embedded local textures supported by this adapter'
    view=d['bufferViews'][image['bufferView']]; offset=view.get('byteOffset',0)
    ext='png' if image.get('mimeType')=='image/png' else 'jpg'
    name=f'texture-{i:03}.{ext}'
    (out/name).write_bytes(b[offset:offset+view['byteLength']])
    image.setdefault('extras',{})['resourceLocation']=prefix+'/'+name
    image.pop('bufferView',None);image.pop('mimeType',None);image['uri']=name
assert len(d['skins'])==1 and not d.get('extensionsRequired')
identity=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]
def mul(a,c): return [sum(a[k*4+r]*c[col*4+k] for k in range(4)) for col in range(4) for r in range(4)]
def local(n):
    if 'matrix' in n:return n['matrix']
    x,y,z,w=n.get('rotation',[0,0,0,1]); sx,sy,sz=n.get('scale',[1,1,1]); tx,ty,tz=n.get('translation',[0,0,0])
    return [(1-2*y*y-2*z*z)*sx,(2*x*y+2*z*w)*sx,(2*x*z-2*y*w)*sx,0,
            (2*x*y-2*z*w)*sy,(1-2*x*x-2*z*z)*sy,(2*y*z+2*x*w)*sy,0,
            (2*x*z+2*y*w)*sz,(2*y*z-2*x*w)*sz,(1-2*x*x-2*y*y)*sz,0,tx,ty,tz,1]
parents={c:i for i,n in enumerate(d['nodes']) for c in n.get('children',[])}; globals={}
def world(i):
    if i not in globals:globals[i]=mul(world(parents[i]),local(d['nodes'][i])) if i in parents else local(d['nodes'][i])
    return globals[i]
skin=d['skins'][0]; ibm=read(skin['inverseBindMatrices']); parts=[]
for ni,node in enumerate(d['nodes']):
    if 'mesh' not in node:continue
    assert node.get('skin')==0
    for primitive in d['meshes'][node['mesh']]['primitives']:
        assert primitive.get('mode',4)==4
        attrs={k:read(v) for k,v in primitive['attributes'].items()}
        morphs=[{name:read(index) for name,index in target.items()} for target in primitive.get('targets',[])]
        assert 'JOINTS_1' not in attrs
        indices=[x[0] for x in read(primitive['indices'])] if 'indices' in primitive else list(range(len(attrs['POSITION'])))
        groups=[]; triangles=[]; bones=set()
        for start in range(0,len(indices),3):
            tri=indices[start:start+3]
            needed={j for v in tri for j,w in zip(attrs['JOINTS_0'][v],attrs['WEIGHTS_0'][v]) if w>0}
            if triangles and len(bones|needed)>110:groups.append((triangles,bones)); triangles=[]; bones=set()
            triangles+=tri; bones|=needed
        if triangles:groups.append((triangles,bones))
        for indices,bones in groups:
            doc=copy.deepcopy(d); data=bytearray();doc['accessors']=[];doc['bufferViews']=[]
            boneids=sorted(bones); remap={old:i for i,old in enumerate(boneids)}
            verts=list(dict.fromkeys(indices)); vi={old:i for i,old in enumerate(verts)}
            def append(rows,component,kind):
                fmt={5121:'B',5123:'H',5125:'I',5126:'f'}[component]
                while len(data)%4:data.append(0)
                packed=struct.pack('<'+fmt*sum(len(r) for r in rows),*(v for r in rows for v in r))
                view=len(doc['bufferViews']); doc['bufferViews'].append(dict(buffer=0,byteOffset=len(data),byteLength=len(packed)));data.extend(packed)
                a=dict(bufferView=view,componentType=component,count=len(rows),type=kind)
                if kind=='VEC3':a.update(min=[min(r[k] for r in rows) for k in range(3)],max=[max(r[k] for r in rows) for k in range(3)])
                doc['accessors'].append(a);return len(doc['accessors'])-1
            newattrs={}
            for name,rows in attrs.items():
                original=d['accessors'][primitive['attributes'][name]]
                selected=[rows[v] for v in verts]
                if name=='JOINTS_0':selected=[tuple(remap.get(j,0) for j in row) for row in selected]
                idx=append(selected,original['componentType'],original['type']);newattrs[name]=idx
                if original.get('normalized'):doc['accessors'][idx]['normalized']=True
            prim=dict(attributes=newattrs,indices=append([(vi[i],) for i in indices],5125,'SCALAR'),material=primitive.get('material',0))
            if morphs:
                prim['targets']=[{name:append([rows[v] for v in verts],5126,'VEC3') for name,rows in target.items()} for target in morphs]
            doc['meshes']=[dict(primitives=[prim],extras=d['meshes'][node['mesh']].get('extras',{}))]
            if morphs:doc['meshes'][0]['weights']=[0]*len(morphs)
            doc['nodes']=[dict(name=d['nodes'][skin['joints'][j]].get('name',f'joint{j}'),matrix=world(skin['joints'][j])) for j in boneids]
            doc['nodes'].append(dict(name='mesh',mesh=0,skin=0,matrix=world(ni)))
            doc['skins']=[dict(joints=list(range(len(boneids))),inverseBindMatrices=append([ibm[j] for j in boneids],5126,'MAT4'))]
            doc['scenes']=[dict(nodes=list(range(len(doc['nodes']))))];doc['scene']=0
            doc.pop('animations',None); doc.pop('extensions',None)
            doc['buffers']=[dict(byteLength=len(data))]
            text=json.dumps(doc,separators=(',',':')).encode();text+=b' '*((-len(text))%4);data+=b'\0'*((-len(data))%4)
            raw=struct.pack('<III',0x46546c67,2,28+len(text)+len(data))+struct.pack('<II',len(text),0x4e4f534a)+text+struct.pack('<II',len(data),0x004e4942)+data
            name=f'part-{len(parts):03}.glb';(out/name).write_bytes(raw);parts.append(dict(file=name,joints=len(boneids),slots=len(boneids)*2+1,sourceJointIndices=boneids,sourceMeshNode=ni,morphCount=len(morphs)))
(out/'capabilities.json').write_text(json.dumps(dict(source=str(source),sha256=hashlib.sha256(source.read_bytes()).hexdigest(),parts=parts,nodes=d['nodes'],skin=skin,inverseBindMatrices=ibm,morphs=any(p['morphCount'] for p in parts),morphTargetNames=d['meshes'][0].get('extras',{}).get('targetNames',[]),vrm=d.get('extensions',{}).get('VRM',{}),clips=False,flattened=True),indent=2),encoding='utf-8')
print(json.dumps(dict(parts=len(parts),maxSlots=max(p['slots'] for p in parts),source=str(source))))
