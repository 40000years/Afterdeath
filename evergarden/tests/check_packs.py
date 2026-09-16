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
relic_expected = {"voidscape:" + name for name in (
    "void_key", "rift_pickaxe", "smelter_pickaxe", "storm_bow", "nova_bow", "rift_blade", "eternal_aegis",
    "scroll_eternity", "scroll_limit_break", "scroll_unique", "astral_dust", "key_shard", "repair_stone", "void_elixir",
    "thorn_mask", "thorn_crown", "astral_mask", "astral_crown", "chrono_mask", "chrono_crown"
)}
assert len(definitions) == len(identifiers)
assert relic_expected.issubset(identifiers), f"Missing relics: {relic_expected - identifiers}"
assert len(identifiers) == 171, f"Expected 171 identifiers including the azure portal, got {len(identifiers)}"
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
    manifest = json.loads(bedrock.read('manifest.json'))
    assert manifest['header']['version'] == [3, 3, 0]
    assert manifest['modules'][0]['version'] == [3, 3, 0]
    assert not any('nether_portal' in name or name.endswith('/portal.png') for name in java.namelist())
    assert 'textures/blocks/portal.png' not in bedrock.namelist()
    assert json.loads(java.read('assets/voidscape/textures/item/azure_portal.png.mcmeta'))['animation']['frametime'] == 2
    assert 'USE_UV_ANIM' in json.loads(bedrock.read('materials/evergarden_portal.material'))['materials']['evergarden_portal:entity_alphablend']['+defines']
    for base, entries in mapping['items'].items():
        selector = json.loads(java.read('assets/minecraft/items/' + base.split(':')[1] + '.json'))['model']
        assert selector['property'] == 'minecraft:custom_model_data' and 'fallback' in selector
        cases = {c['when'] for c in selector['cases']}
        for entry in entries:
            assert entry['model'] == base
            assert entry['predicate']['value'] in cases
            name = entry['bedrock_identifier'].split(':')[1]
            assert java.read(f'assets/voidscape/textures/item/{name}.png') == bedrock.read(atlas[entry['bedrock_options']['icon']]['textures'] + '.png')
            if name.endswith(('_mask', '_crown')) or '_stage_' in name:
                model = json.loads(java.read(f'assets/voidscape/models/item/{name}.json'))
                assert 'head' in model['display']
                if name.endswith(('_mask', '_crown')):
                    assert len(model['elements']) >= 4
                attachment = json.loads(bedrock.read(f'attachables/{name}.json'))['minecraft:attachable']['description']
                assert attachment['identifier'] == entry['bedrock_identifier']
                geometry = json.loads(bedrock.read(f'models/entity/{name}.geo.json'))['minecraft:geometry'][0]
                assert geometry['description']['identifier'] == attachment['geometry']['default']
                if '_stage_' not in name:
                    assert 'item_slot_to_bone_name' in geometry['bones'][0]['binding']
                if '_stage_' in name:
                    assert geometry['bones'][0]['name'] == 'head'
                    assert geometry['bones'][0]['pivot'] == [0, 24, 0]
                    assert base == 'minecraft:iron_helmet', 'Plant attachables require a head-equippable base'
                    # Match vanilla crop.json density: two X planes plus two Z planes.
                    assert len(geometry['bones'][0]['cubes']) == 4
                    # Box UV previously sampled only the top of the sprite,
                    # clipping the sprout drawn in its lower half.
                    plane_faces = [('north', 'south'), ('north', 'south'), ('east', 'west'), ('east', 'west')]
                    for cube, faces in zip(geometry['bones'][0]['cubes'], plane_faces):
                        # Small stand at -0.65, geometry in pixels /16, scale .5.
                        # The base must be above the farmland surface (-1/16).
                        assert abs(-0.8125 + cube['origin'][1] / 16 * 0.5 + 1 / 16) < 1e-6
                        for face in faces:
                            assert cube['uv'][face] == {'uv': [0, 0], 'uv_size': [32, 32]}, name
    assert json.loads(java.read('assets/minecraft/items/bow.json'))['model']['fallback']['type'] == 'minecraft:condition'
    assert json.loads(java.read('assets/minecraft/items/shield.json'))['model']['fallback']['on_false']['model']['type'] == 'minecraft:shield'
with zipfile.ZipFile(dist / 'evergarden-3.0.0.jar') as jar:
    for filename in (*hashes, 'geyser-mappings.json', 'pack-hashes.json'):
        assert jar.read('resource-packs/' + filename) == (dist / filename).read_bytes()
print('PASS: 20 model selectors, six Java/Bedrock wearable models, vanilla fallbacks, no cross-plugin Geyser ID collisions, hashes and embedded assets')
