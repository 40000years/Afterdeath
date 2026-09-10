"""Build original, tiny pixel-art relic assets and Java/Bedrock packs. Standard library only."""
"""Build original, tiny pixel-art relic assets and Java/Bedrock packs. Standard library only."""
import hashlib, json, struct, zlib, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'target' / 'resource-packs'
DIST = ROOT / 'dist'
PALETTE = {'d':(20,18,35,255),'p':(79,48,113,255),'v':(151,94,199,255),
           'c':(62,177,185,255),'g':(151,255,238,255),'w':(232,255,248,255),'s':(88,105,124,255),
           'o':(255,133,51,255),'r':(220,50,40,255),'y':(255,215,60,255)}
ITEMS = {
 'void_key':('trial_key','Void Key'),
 'rift_pickaxe':('netherite_pickaxe','Rift Excavator'),
 'smelter_pickaxe':('netherite_pickaxe',"Smelter's Pickaxe"),
 'storm_bow':('bow','Storm Verdict'),
 'nova_bow':('bow','Nova Bow'),
 'rift_blade':('netherite_sword','Rift Blade'),
 'eternal_aegis':('shield','Eternal Aegis')}

def write_json(path, value):
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def png(path, pixels, size=32):
 def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
 raw=b''.join(b'\x00'+bytes(c for pixel in row for c in pixel) for row in pixels)
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',size,size,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))

def icon(name,draw=0):
 p=[[(0,0,0,0) for _ in range(32)] for _ in range(32)]
 def rect(x1,y1,x2,y2,c):
  for y in range(max(0,y1),min(32,y2+1)):
   for x in range(max(0,x1),min(32,x2+1)):p[y][x]=PALETTE[c]
 def line(x1,y1,x2,y2,c,width=1):
  length=max(abs(x2-x1),abs(y2-y1),1)
  for n in range(length+1):
   x=round(x1+(x2-x1)*n/length);y=round(y1+(y2-y1)*n/length)
   rect(x,y,x+width-1,y+width-1,c)
 if name=='void_key':
  line(11,21,22,10,'d',3);line(12,21,22,11,'p',2);line(13,20,22,11,'c')
  for y in range(5,14):rect(18,y,26,y,'d')
  for y in range(6,13):rect(19,y,25,y,'v')
  for y in range(8,11):rect(21,y,23,y,'d')
  rect(22,9,22,9,'g')
  rect(7,24,11,26,'d');rect(8,24,10,25,'c')
  rect(12,27,14,29,'d');rect(12,27,13,28,'g')
 elif name=='smelter_pickaxe':
  line(5,27,23,9,'d',4);line(6,27,23,10,'r',2);line(7,26,23,10,'o')
  line(6,6,22,5,'d',5);line(21,6,25,18,'d',5)
  line(7,7,21,6,'r',3);line(22,8,26,19,'o',2);line(8,6,21,5,'y');rect(18,8,21,11,'y')
 elif name=='rift_pickaxe':
  line(5,27,23,9,'d',4);line(6,27,23,10,'p',2);line(7,26,23,10,'c')
  line(6,6,22,5,'d',5);line(21,6,25,18,'d',5)
  line(7,7,21,6,'v',3);line(22,8,26,19,'c',2);line(8,6,21,5,'g');rect(18,8,21,11,'g')
 elif name in ('nova_bow','storm_bow'):
  # Vanilla bow transforms expect the arrow to point to the upper left of
  # the texture, with the string behind the grip (towards the lower right).
  # Transform the drawing coordinates before rasterizing to keep crisp lines.
  def bow_line(x1,y1,x2,y2,c,width=1):
   def point(x,y):
    return round(16+0.5657*(y-x)),round(16-0.5657*(x+y-32))
   line(*point(x1,y1),*point(x2,y2),c,width)
  color='v' if name=='nova_bow' else 'g'
  points=[(9,3),(17,5),(23,12),(23,19),(17,26),(9,28)]
  for a,b in zip(points,points[1:]):bow_line(*a,*b,'d',3)
  for a,b in zip(points,points[1:]):bow_line(*a,*b,color,2)
  nock=9-draw*2
  bow_line(10,4,nock,16,'s');bow_line(nock,16,10,29,'s')
  bow_line(23,13,23,18,'c',3);bow_line(23,14,23,17,'w')
  if draw:
   # The arrow follows the string instead of floating at a fixed position.
   tip=34-draw*2
   bow_line(nock,16,tip,16,'g')
   bow_line(tip-4,13,tip,16,'w');bow_line(tip-4,19,tip,16,'w')
 elif name=='rift_blade':
  line(4,28,10,22,'d',4);line(5,28,11,22,'p',2)
  line(6,18,15,27,'d',3);line(6,19,15,28,'v')
  for n in range(17):
   x=11+n;y=20-n;rect(x,y,x+3,y+3,'d');rect(x+1,y,x+2,y+2,'c')
  line(13,19,29,3,'g');rect(11,20,13,22,'w')
 elif name=='eternal_aegis':
  for y in range(3,29):
   width=11 if y<18 else max(1,11-(y-18))
   rect(16-width,y,16+width,y,'d');rect(17-width,y,15+width,y,'p')
  rect(7,5,25,7,'v');line(16,7,16,25,'c',2);line(9,14,23,14,'c',2)
  for d in range(5):rect(16-d,13+abs(d-2),16+d,15+abs(d-2),'g')
  rect(15,13,17,16,'w')
 elif name=='void_shard':
  for y in range(3,29):
   width=max(1,8-abs(y-16)//2);rect(16-width,y,16+width,y,'d');rect(17-width,y,15+width,y,'p')
  line(16,5,11,18,'g',2);line(11,18,17,27,'c');line(17,7,22,17,'v',2)
 else:
  rect(5,6,26,26,'d');rect(7,8,24,24,'s');rect(9,10,22,24,'p')
  rect(8,13,13,15,'g');rect(18,13,23,15,'g');rect(14,18,17,24,'d')
  line(5,7,2,2,'v',3);line(25,7,28,2,'v',3)
  if name=='captain_mask':rect(3,4,28,8,'d');rect(10,2,21,5,'v');rect(14,3,17,7,'g')
 return p

def archive(folder,path):
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for f in sorted(folder.rglob('*')):
   if f.is_file():
    info=zipfile.ZipInfo(f.relative_to(folder).as_posix(),(2026,9,8,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
    z.writestr(info,f.read_bytes())

def main():
 java=BUILD/'java';bedrock=BUILD/'bedrock';DIST.mkdir(parents=True,exist_ok=True)
 write_json(java/'pack.mcmeta',{'pack':{'description':'Voidscape 2.0 | Ancient Relics','min_format':[88,0],'max_format':[88,0]}})
 write_json(bedrock/'manifest.json',{'format_version':2,'header':{'name':'Voidscape 2.0','description':'Ancient relic textures for Geyser','uuid':'df4aee6d-9e8e-4ec8-9df1-7974c6bea203','version':[2,0,0],'min_engine_version':[1,21,80]},'modules':[{'type':'resources','uuid':'ef4aee6d-9e8e-4ec8-9df1-7974c6bea204','version':[2,0,0]}]})
 textures={};mappings={'format_version':2,'items':{}}
 for name,(base,title) in ITEMS.items():
  pixels=icon(name);png(java/f'assets/voidscape/textures/item/{name}.png',pixels);png(bedrock/f'textures/items/{name}.png',pixels)
  textures['voidscape.'+name]={'textures':'textures/items/'+name}
  parent='handheld' if base in ('netherite_pickaxe','netherite_sword') else 'generated'
  model={'parent':'minecraft:item/'+parent,'textures':{'layer0':'voidscape:item/'+name}}
  if base=='bow':model['parent']='minecraft:item/bow'
  if base=='shield':
   # This is a flat sprite, so entity-shield transforms do not fit its origin.
   model['display']={
    'firstperson_righthand':{'rotation':[0,-30,0],'translation':[0,-2,0],'scale':[0.8,0.8,0.8]},
    'firstperson_lefthand':{'rotation':[0,30,0],'translation':[0,-2,0],'scale':[0.8,0.8,0.8]},
    'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,3,1],'scale':[0.8,0.8,0.8]},
    'thirdperson_lefthand':{'rotation':[0,-90,0],'translation':[0,3,1],'scale':[0.8,0.8,0.8]}}
  if name.endswith('mask'):
   face={direction:{'uv':[0,0,16,16],'texture':'#mask'} for direction in ['north','south','east','west','up','down']}
   model={'textures':{'mask':'voidscape:item/'+name},'elements':[{'from':[3,3,3],'to':[13,13,13],'faces':face}],
          'display':{'head':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1.4,1.4,1.4]},'gui':{'rotation':[20,35,0],'scale':[0.8,0.8,0.8]}}}
  write_json(java/f'assets/voidscape/models/item/{name}.json',model)
  definition={'model':{'type':'minecraft:model','model':'voidscape:item/'+name}}
  if base=='bow':
   stages=[]
   for n in range(3):
    stage=f'{name}_pulling_{n}';png(java/f'assets/voidscape/textures/item/{stage}.png',icon(name,n+1))
    write_json(java/f'assets/voidscape/models/item/{stage}.json',{'parent':'minecraft:item/bow','textures':{'layer0':'voidscape:item/'+stage}})
    stages.append({'threshold':[0,0.65,0.9][n],'model':{'type':'minecraft:model','model':'voidscape:item/'+stage}})
   definition={'model':{'type':'minecraft:condition','property':'minecraft:using_item','on_false':definition['model'],'on_true':{'type':'minecraft:range_dispatch','property':'minecraft:use_duration','scale':0.05,'fallback':stages[0]['model'],'entries':stages}}}
  write_json(java/f'assets/voidscape/items/{name}.json',definition)
  mappings['items'].setdefault('minecraft:'+base,[]).append({'type':'definition','model':'voidscape:'+name,'bedrock_identifier':'voidscape:'+name,'display_name':title,
    'bedrock_options':{'icon':'voidscape.'+name,'allow_offhand':True,'display_handheld':base in ('netherite_pickaxe','netherite_sword','bow')}})
 core_cases=[]
 core_definitions=[]
 core_items={
  'lightning_strike':'Core of Lightning','frost_nova':'Core of Frost',
  'shadow_step':'Core of Shadows','natures_bloom':'Core of Nature',
  'earth_wall':'Core of Earth','dragons_breath':'Core of Dragon',
  'void_pull':'Core of the Void','invisibility_shroud':'Core of Invisibility',
  'poison_spores':'Core of Poison','wither_ray':'Core of Wither',
  'shulker_levitation':'Core of Levitation','meteor_strike':'Core of Meteor',
  'iron_armor':'Core of Iron','time_dilation':'Core of Time','soul_drain':'Core of Souls'
 }
 for c_id,c_title in core_items.items():
  source=ROOT/f'art/cores/core_{c_id}.png'
  if source.is_file():
   data=source.read_bytes()
   for dest in (java/f'assets/advance_magic/textures/item/core_{c_id}.png',bedrock/f'textures/items/core_{c_id}.png'):
    dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
   write_json(java/f'assets/advance_magic/models/item/core_{c_id}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'advance_magic:item/core_{c_id}'}})
   write_json(java/f'assets/advance_magic/items/core_{c_id}.json',{'model':{'type':'minecraft:model','model':f'advance_magic:item/core_{c_id}'}})
   textures[f'advance_magic.core_{c_id}']={'textures':f'textures/items/core_{c_id}'}
   core_cases.append({'when':f'advance_magic:core_{c_id}','model':{'type':'minecraft:model','model':f'advance_magic:item/core_{c_id}'}})
   core_definitions.append({'type':'definition','model':'minecraft:heart_of_the_sea',
                            'predicate':{'type':'match','property':'custom_model_data','index':0,'value':f'advance_magic:core_{c_id}'},
                            'bedrock_identifier':f'advance_magic:core_{c_id}','display_name':c_title,
                            'bedrock_options':{'icon':f'advance_magic.core_{c_id}','allow_offhand':True,'display_handheld':False,'creative_category':'items'}})
 if core_cases:
  write_json(java/'assets/minecraft/items/heart_of_the_sea.json',{'model':{'type':'minecraft:select','property':'minecraft:custom_model_data','index':0,'cases':core_cases,'fallback':{'type':'minecraft:model','model':'minecraft:item/heart_of_the_sea'}}})
  mappings['items']['minecraft:heart_of_the_sea']=core_definitions
 write_json(bedrock/'textures/item_texture.json',{'resource_pack_name':'voidscape','texture_name':'atlas.items','texture_data':textures})
 write_json(DIST/'geyser-mappings.json',mappings)
 png(java/'pack.png',icon('void_key'));png(bedrock/'pack_icon.png',icon('void_key'))
 archive(java,DIST/'voidscape-java.zip');archive(bedrock,DIST/'voidscape-bedrock.mcpack')
 hashes={f.name:hashlib.sha1(f.read_bytes()).hexdigest() for f in [DIST/'voidscape-java.zip',DIST/'voidscape-bedrock.mcpack']}
 write_json(DIST/'pack-hashes.json',hashes)
 print(json.dumps(hashes,indent=2))

if __name__=='__main__':main()
