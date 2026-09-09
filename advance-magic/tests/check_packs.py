import hashlib
import json
import runpy
import struct
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
catalog = runpy.run_path(str(ROOT / 'tools/build_packs.py'))['spells']()
dist = ROOT / 'dist'
hashes = json.loads((dist / 'pack-hashes.json').read_text())
for name, digest in hashes.items():
    assert hashlib.sha1((dist / name).read_bytes()).hexdigest() == digest
    with zipfile.ZipFile(dist / name) as z:
        assert z.testzip() is None
        for file in z.namelist():
            if file.endswith(('.json', '.mcmeta')):
                json.loads(z.read(file))
            if file.endswith('.png'):
                data = z.read(file)
                assert data[:8] == b'\x89PNG\r\n\x1a\n'
                assert struct.unpack('>II', data[16:24]) == (32, 32)
with zipfile.ZipFile(dist / 'advance-magic-java.zip') as z:
    for name, _, _ in catalog:
        item = json.loads(z.read(f'assets/advance_magic/items/{name}.json'))
        assert item['model']['model'] == f'advance_magic:item/{name}'
        model = json.loads(z.read(f'assets/advance_magic/models/item/{name}.json'))
        assert model['parent'] == 'minecraft:item/handheld'
        assert model['textures']['layer0'] == f'advance_magic:item/{name}'
        assert z.read(f'assets/advance_magic/textures/item/{name}.png')
mapping = json.loads((dist / 'geyser-mappings.json').read_text())
definitions = mapping['items']['minecraft:carrot_on_a_stick']
assert len(definitions) == len(catalog) == 15
assert len({row['bedrock_identifier'] for row in definitions}) == 15
with zipfile.ZipFile(dist / 'advance-magic-bedrock.mcpack') as z:
    atlas = json.loads(z.read('textures/item_texture.json'))['texture_data']
    for definition in definitions:
        assert definition['model'] in {f'advance_magic:{s[0]}' for s in catalog}
        assert z.read(atlas[definition['bedrock_options']['icon']]['textures'] + '.png')
print('PASS: 15 Java models, 15 Bedrock mappings, textures, manifests, archives and hashes')
