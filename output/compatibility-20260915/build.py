from pathlib import Path
import subprocess,shutil,os
root=Path(__file__).resolve().parents[2];out=Path(__file__).resolve().parent
jars=[p for p in (Path.home()/'.m2/repository').rglob('*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar'))];jars.sort(key=lambda p:('gson' in str(p),str(p)),reverse=True)
cp=os.pathsep.join(map(str,jars))
for module in ['advance-magic','evergarden']:
 classes=out/module/'classes';classes.mkdir(parents=True,exist_ok=True)
 modulecp=str(out/'advance-magic/classes')+os.pathsep+cp
 subprocess.run(['javac','--release','21','-proc:none','-encoding','UTF-8','-cp',modulecp,'-d',str(classes),*map(str,(root/module/'src/main/java').rglob('*.java'))],check=True)
 shutil.copytree(root/module/'src/main/resources',classes,dirs_exist_ok=True)
 packs=classes/'resource-packs';packs.mkdir(exist_ok=True)
 names=[f'{module}-java.zip',f'{module}-bedrock.mcpack','geyser-mappings.json','pack-hashes.json']
 if module=='advance-magic':names+=['wand-preview.html','advance-magic-guide-th.png']
 for name in names:
  src=root/module/'dist'/name
  if src.exists():shutil.copy2(src,packs/name)
 if module=='evergarden':
  (classes/'geyser').mkdir(exist_ok=True)
  for name in ['evergarden-bedrock.mcpack','geyser-mappings.json']:shutil.copy2(packs/name,classes/'geyser'/name)
 subprocess.run(['jar','--create','--file',str(out/f'{module}.jar'),'-C',str(classes),'.'],check=True)
 print('BUILT',module,flush=True)
