"""Read-only local GLB/VRM capability inventory; no model files enter Git."""
import json, struct, pathlib
ROOT=pathlib.Path(r'C:/Users/User/Documents/Codex/AGNOR/assets/models/разное')
def load(p):
    raw=p.read_bytes(); n=struct.unpack_from('<I',raw,12)[0]
    return json.loads(raw[20:20+n]),raw[28+n:]
def values(d,b,index):
    a=d['accessors'][index]; v=d['bufferViews'][a['bufferView']]
    fmt={5121:'B',5123:'H',5125:'I',5126:'f'}[a['componentType']]
    size={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
    step=v.get('byteStride',struct.calcsize(fmt)*size)
    base=v.get('byteOffset',0)+a.get('byteOffset',0)
    return [struct.unpack_from('<'+fmt*size,b,base+i*step) for i in range(a['count'])]
if __name__=='__main__':
    for p in list(ROOT.glob('*.glb'))+list(ROOT.glob('*.vrm')):
        d,b=load(p); used=set()
        for m in d.get('meshes',[]):
            for prim in m['primitives']:
                at=prim['attributes']
                if 'JOINTS_0' in at:
                    for js,ws in zip(values(d,b,at['JOINTS_0']),values(d,b,at['WEIGHTS_0'])):
                        used.update(j for j,w in zip(js,ws) if w>0)
        print(p.name,'nodes',len(d.get('nodes',[])),'joints',[len(s['joints']) for s in d.get('skins',[])], 'weighted joint indices',len(used))
