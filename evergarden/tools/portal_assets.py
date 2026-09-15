"""Animated azure portal sprite and dedicated wearable planes for both clients."""
import math
import struct
import zlib

def register(java, bedrock, textures, mappings, selectors, write_json):
    rows=[]
    for frame in range(16):
        for y in range(32):
            row=bytearray([0])
            for x in range(32):
                dx=(x-15.5)/16;dy=(y-15.5)/16
                angle=math.atan2(dy,dx);radius=math.hypot(dx,dy)
                wave=(math.sin(angle*3-radius*14-frame*math.tau/16)+1)/2
                row.extend((int(20+70*wave),int(135+110*wave),255,220))
            rows.append(bytes(row))
    def chunk(kind,data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',32,512,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows),9))+chunk(b'IEND',b'')
    for path in (java/'assets/voidscape/textures/item/azure_portal.png',bedrock/'textures/items/azure_portal.png'):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    write_json(java/'assets/voidscape/textures/item/azure_portal.png.mcmeta',{'animation':{'frametime':2,'interpolate':True}})
    write_json(java/'assets/voidscape/models/item/azure_portal.json',{
        'textures':{'portal':'voidscape:item/azure_portal'},
        'elements':[{'from':[0,0,7.9],'to':[16,16,8.1], 'faces':{f:{'texture':'#portal','uv':[0,0,16,16]} for f in ('north','south')}}],
        'display':{'head':{'translation':[0,-4,0],'scale':[1.6,1.6,1.6]}}})
    model={'type':'minecraft:model','model':'voidscape:item/azure_portal'}
    write_json(java/'assets/voidscape/items/azure_portal.json',{'model':model})
    selectors.setdefault('iron_helmet',[]).append({'when':'voidscape:azure_portal','model':model})
    textures['voidscape.azure_portal']={'textures':'textures/items/azure_portal'}
    mappings['items'].setdefault('minecraft:iron_helmet',[]).append({
        'type':'definition','model':'minecraft:iron_helmet',
        'predicate':{'type':'match','property':'custom_model_data','index':0,'value':'voidscape:azure_portal'},
        'bedrock_identifier':'voidscape:azure_portal','display_name':'Evergarden Azure Portal',
        'bedrock_options':{'icon':'voidscape.azure_portal','allow_offhand':False,'display_handheld':False}})
    write_json(bedrock/'models/entity/azure_portal.geo.json',{'format_version':'1.12.0','minecraft:geometry':[{
        'description':{'identifier':'geometry.voidscape.azure_portal','texture_width':32,'texture_height':32,'visible_bounds_width':2,'visible_bounds_height':4,'visible_bounds_offset':[0,1.75,0]},
        'bones':[{'name':'head','pivot':[0,24,0],'cubes':[{'origin':[-8,24,-0.1],'size':[16,16,0.2],
            'uv':{f:{'uv':[0,0],'uv_size':[32,32]} for f in ('north','south')}}]}]}]})
    write_json(bedrock/'attachables/azure_portal.json',{'format_version':'1.10.0','minecraft:attachable':{'description':{
        'identifier':'voidscape:azure_portal','materials':{'default':'evergarden_portal'},
        'textures':{'default':'textures/items/azure_portal'},'geometry':{'default':'geometry.voidscape.azure_portal'},
        'render_controllers':['controller.render.evergarden_portal']}}})
    write_json(bedrock/'render_controllers/azure_portal.json',{'format_version':'1.8.0','render_controllers':{
        'controller.render.evergarden_portal':{'geometry':'Geometry.default','materials':[{'*':'Material.default'}],
            'textures':['Texture.default'],'ignore_lighting':True,'uv_anim':{'offset':[0,'math.mod(math.floor(query.life_time * 10), 16) / 16'], 'scale':[1,0.0625]}}}})
    write_json(bedrock/'materials/evergarden_portal.material',{'materials':{'version':'1.0.0',
        'evergarden_portal:entity_alphablend':{'+defines':['USE_UV_ANIM']}}})
