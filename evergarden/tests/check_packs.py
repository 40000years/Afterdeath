"""Check model routing, Geyser coexistence, six wearable models and release integrity."""
import hashlib
import json
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
dist = root / 'dist'
mapping = json.loads((dist / 'geyser-mappings.json').read_text())
other = json.loads((root.parent / 'advance-magic/dist/geyser-mappings.json').read_text())
definitions = [d for group in mapping['items'].values() for d in group]
identifiers = {d['bedrock_identifier'] for d in definitions}
other_ids = {d['bedrock_identifier'] for group in other['items'].values() for d in group}
assert len(definitions) == len(identifiers) == 13
assert not identifiers & other_ids, 'Duplicate Geyser custom item IDs across plugins'
hashes = json.loads((dist / 'pack-hashes.json').read_text())
for filename, digest in hashes.items():
    assert hashlib.sha1((dist / filename).read_bytes()).hexdigest() == digest
with zipfile.ZipFile(dist / 'evergarden-java.zip') as java, zipfile.ZipFile(dist / 'evergarden-bedrock.mcpack') as bedrock:
    for archive in (java, bedrock):
        assert archive.testzip() is None
        for name in archive.namelist():
            if name.endswith(('.json', '.mcmeta')):
                json.loads(archive.read(name))
    atlas = json.loads(bedrock.read('textures/item_texture.json'))['texture_data']
    for base, entries in mapping['items'].items():
        selector = json.loads(java.read('assets/minecraft/items/' + base.split(':')[1] + '.json'))['model']
        assert selector['property'] == 'minecraft:custom_model_data' and 'fallback' in selector
        cases = {c['when'] for c in selector['cases']}
        for entry in entries:
            assert entry['model'] == base
            assert entry['predicate']['value'] in cases
            name = entry['bedrock_identifier'].split(':')[1]
            assert java.read(f'assets/voidscape/textures/item/{name}.png') == bedrock.read(atlas[entry['bedrock_options']['icon']]['textures'] + '.png')
            if name.endswith(('_mask', '_crown')):
                model = json.loads(java.read(f'assets/voidscape/models/item/{name}.json'))
                assert len(model['elements']) >= 4 and 'head' in model['display']
                attachment = json.loads(bedrock.read(f'attachables/{name}.json'))['minecraft:attachable']['description']
                assert attachment['identifier'] == entry['bedrock_identifier']
                geometry = json.loads(bedrock.read(f'models/entity/{name}.geo.json'))['minecraft:geometry'][0]
                assert geometry['description']['identifier'] == attachment['geometry']['default']
                assert 'item_slot_to_bone_name' in geometry['bones'][0]['binding']
    assert json.loads(java.read('assets/minecraft/items/bow.json'))['model']['fallback']['type'] == 'minecraft:condition'
    assert json.loads(java.read('assets/minecraft/items/shield.json'))['model']['fallback']['on_false']['model']['type'] == 'minecraft:shield'
with zipfile.ZipFile(dist / 'evergarden-3.0.0.jar') as jar:
    for filename in (*hashes, 'geyser-mappings.json', 'pack-hashes.json'):
        assert jar.read('resource-packs/' + filename) == (dist / filename).read_bytes()
print('PASS: 13 model selectors, six Java/Bedrock wearable models, vanilla fallbacks, no cross-plugin Geyser ID collisions, hashes and embedded assets')
