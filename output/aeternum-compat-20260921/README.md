ตรวจและติดตั้งตัวแก้ Evergarden / AeternumSeasons 4.7 เมื่อ 2026-09-21

พืชสีม่วงดำ: TestServer ไม่มี Aeternum Foods resource pack ทั้งฝั่ง Java และ Geyser ขณะที่ Aeternum เรียกโมเดล `aeternum:crop_display/onion_stage_*` และโมเดลพืชอื่น ๆ ตัวปลั๊กอิน Aeternum 4.7 ไม่ส่ง Java pack เอง และ server.properties ไม่มีแพ็กหลักตั้งไว้

ติดตั้งให้ Evergarden ส่ง Aeternum Foods Java 26.x เวอร์ชัน 1.2 จาก CDN ทางการหลังแพ็ก Evergarden โดยใช้ UUID และ SHA-1 แยกกัน เปิด/ปิดได้ที่ `compatibility.aeternum-seasons.resource-pack.enabled` และดูสถานะผ่าน `/evergarden pack` ส่วน Void Elixir ใช้โมเดล `voidscape:void_elixir` โดยตรง เพื่อไม่ชนไฟล์ `minecraft/items/honey_bottle.json` ของ Aeternum

ติดตั้งไฟล์ทางการของ Bedrock ใน `TestServer/plugins/Geyser-Spigot`:

- `packs/Aeternum-Foods-Bedrock.mcpack`
- `custom_mappings/aeternum_food_items.json`
- `custom_mappings/aeternum_food_blocks.json`

Geyser ตั้ง `gameplay.enable-custom-content: true` อยู่แล้ว จึงไม่ต้องเปลี่ยนการตั้งค่านี้

อุปกรณ์: การทดสอบ JAR เดิมไม่พบว่า Efficiency VII–VIII ที่ไม่มีแท็ก Evergarden ถูกเพิ่ม Advance Tool / Eternity เอง จึงยังยืนยันเหตุการณ์กับอุปกรณ์เก่าที่ผู้ใช้รายงานไม่ได้ แต่ทำซ้ำและแก้บั๊กที่เกี่ยวข้องได้:

- ชื่อไอเทมที่มีคำว่า Smelter ถูกยึดเป็น relic ของ Evergarden ทั้งที่ไม่มีแท็กของปลั๊กอิน แก้ให้ระบุเจ้าของด้วย PDC หรือ CustomModelData ที่มี namespace เท่านั้น และเลิกใช้ lore เพื่อเดาว่าได้รับ Eternity
- คัมภีร์อ่านระดับ Efficiency VII–VIII เป็น V แล้วอัปเกรดผิดเป็น VI แก้ให้อัปเกรดจากระดับจริงในเมนู Java/Bedrock และการใช้คัมภีร์
- การ migrate ไอเทมที่เคยลงคัมภีร์อาจล้าง tool component ของปลั๊กอินอื่น แก้ให้รักษาข้อมูลที่ไม่ได้เป็นความสามารถของ Evergarden รวมทั้งสถานะ unbreakable เดิม
- ไอเทมที่มีเฉพาะแท็ก Limit Break/Unique ของ Evergarden ถูกข้ามการซ่อม tool component แก้การตรวจแท็กให้ครบ

ไม่ได้แก้ไฟล์ inventory หรือหีบในโลก และไม่ได้เดาค่าเดิมเพื่อย้อนข้อมูลอาวุธเก่า การตรวจ playerdata ปัจจุบันแบบอ่านอย่างเดียวไม่พบคีย์ `ue_advance_tool` หรือ `relic_eternity`; ข้อนี้ไม่ครอบคลุมของที่เก็บในหีบ

ทดสอบบน Paper 26.2 build 121 แยกจาก TestServer โดยโหลด Evergarden, AeternumSeasons, Advance Magic, Geyser และ Floodgate: ผ่าน 125 ข้อ ดู `checks.txt` ครอบคลุมความคงเดิมของไอเทม, อัปเกรด, แพ็กที่ส่งให้ผู้เล่น, การปลูก onion/tomato/rice และพืช Evergarden ครบ 30 ชนิดผ่าน event ของทั้งสองปลั๊กอินพร้อมการใช้เมล็ดหนึ่งชิ้น

ของที่สร้างก่อนอัปเดตยังใช้ต่อได้: ประตู Evergarden โหลดตำแหน่งและแกนจาก `plugins/Evergarden/portals.yml` แล้วสร้างตัวแสดงผลรุ่นใหม่ให้เองโดยไม่เปลี่ยนกรอบประตู ส่วนพืช Evergarden โหลดชนิด ระยะ และเวลาปลูกเดิมจาก `plugins/Evergarden/crops.yml`; เวลาที่ผ่านไประหว่างปิดเซิร์ฟเวอร์จะถูกนำมาคำนวณระยะเติบโตต่อ พืช Aeternum และพืช vanilla อยู่ภายใต้ข้อมูลโลก/Aeternum ซึ่งชุดแก้นี้ไม่ได้แก้ไข

Geyser เริ่มทำงานพร้อม 213 custom items และไม่มีข้อผิดพลาดในการโหลด mappings ของ Aeternum ตรวจไฟล์ภาพครบ 12 Java crop models, 11 Bedrock items และ 16 crop block states; SHA-1 ของแพ็ก Java ตรงกับ CDN ทั้ง Evergarden และ Aeternum ชุดทดสอบ geometry/loot และ pack ของ Evergarden ผ่านด้วย โดยปรับตัวตรวจแพ็กเก่าที่เคยคาดว่าพืชทุกชนิดต้องมี vanilla model override ให้รองรับ direct item models ที่ใช้อยู่แล้ว

การทดสอบนี้ยังไม่ได้ยืนยันภาพบนหน้าจอ Minecraft หรือการแตะปลูกจากอุปกรณ์ Bedrock จริง ให้เปิด TestServer ใหม่เต็มรอบ แล้วเข้าเกมใหม่และยอมรับ resource packs เพื่อทดสอบด้วย client จริง

ไฟล์สำรองก่อนแก้: `C:\Users\User\Desktop\TestServer\backups\aeternum-compat-20260921` รายการและ SHA-256 ของไฟล์ที่ติดตั้งอยู่ใน `installed-sha256.json` ตัว JAR ที่ติดตั้งตรงกับ `evergarden.jar` และ `evergarden/dist/evergarden-3.0.0.jar` ใน workspace

คำสั่งตรวจซ้ำจากราก workspace:

```powershell
./evergarden/build.ps1 -SkipPacks -SkipDeploy
./evergarden/test.ps1
python evergarden/tests/run_aeternum_checks.py --source-server C:\Users\User\Desktop\TestServer --with-geyser
python evergarden/tests/check_aeternum_packs.py --downloads output/aeternum-compat-20260921/downloads
```

แพ็กต้นทาง: [Aeternum Foods บน Modrinth](https://modrinth.com/resourcepack/aeternum-foods) และ [Geyser bundle ที่ผู้พัฒนาเผยแพร่](https://drive.google.com/drive/folders/13zaMg2LGSFvsSGiyUlpsjpyLH2spcdIN) ตาม [คู่มือติดตั้งของผู้พัฒนา](https://www.spigotmc.org/resources/aeternum-seasons.130164/)
