"""Original synthetic glTF: triangle, two weighted joints, one POSITION morph.
No external assets. Output is deterministic; no Blender or third-party model needed.
"""
import base64
import json
import pathlib
import struct

blob = bytearray()
views, accessors = [], []

def accessor(values, fmt, component, kind, count, **extra):
    while len(blob) % 4:
        blob.append(0)
    data = struct.pack('<' + fmt * len(values), *values)
    views.append(dict(buffer=0, byteOffset=len(blob), byteLength=len(data)))
    blob.extend(data)
    accessors.append(dict(bufferView=len(views)-1, componentType=component,
                          count=count, type=kind, **extra))
    return len(accessors)-1

pos = accessor([-.5,0,0, .5,0,0, 0,1,0], 'f', 5126, 'VEC3', 3,
               min=[-.5,0,0], max=[.5,1,0])
norm = accessor([0,0,1]*3, 'f', 5126, 'VEC3', 3)
joints = accessor([0,1,0,0]*3, 'H', 5123, 'VEC4', 3)
weights = accessor([1,0,0,0, 1,0,0,0, 0,1,0,0], 'f', 5126, 'VEC4', 3)
morph = accessor([0,0,0, 0,0,0, .5,0,0], 'f', 5126, 'VEC3', 3)
identity = [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]
inverse = identity.copy()
inverse[13] = -1
ibm = accessor(identity+inverse, 'f', 5126, 'MAT4', 2)
times = accessor([0,1], 'f', 5126, 'SCALAR', 2, min=[0], max=[1])
translations = accessor([0,1,0, 0,2,0], 'f', 5126, 'VEC3', 2)
expressions = accessor([0,.75], 'f', 5126, 'SCALAR', 2)
doc = dict(asset=dict(version='2.0', generator='AGNOR original P3.1 fixture'),
           scene=0, scenes=[dict(nodes=[0,2])],
           nodes=[dict(name='root', children=[1]),
                  dict(name='tip', translation=[0,1,0]),
                  dict(name='mesh', mesh=0, skin=0, weights=[0])],
           skins=[dict(joints=[0,1], skeleton=0, inverseBindMatrices=ibm)],
           animations=[dict(name='probe',
               samplers=[dict(input=times, output=translations, interpolation='LINEAR'),
                         dict(input=times, output=expressions, interpolation='LINEAR')],
               channels=[dict(sampler=0, target=dict(node=1, path='translation')),
                         dict(sampler=1, target=dict(node=2, path='weights'))])],
           meshes=[dict(weights=[0], primitives=[dict(
               attributes=dict(POSITION=pos, NORMAL=norm, JOINTS_0=joints, WEIGHTS_0=weights),
               targets=[dict(POSITION=morph)], material=0)])],
           materials=[dict(doubleSided=True, pbrMetallicRoughness=dict(
               baseColorFactor=[.2,.8,.4,1], metallicFactor=0, roughnessFactor=1))],
           accessors=accessors, bufferViews=views,
           buffers=[dict(byteLength=len(blob), uri='data:application/octet-stream;base64,' +
                         base64.b64encode(blob).decode())])
target = pathlib.Path(__file__).resolve().parents[1]/'src/p3Check/resources/rig-morph.gltf'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(doc, indent=2)+'\n', encoding='utf-8')
