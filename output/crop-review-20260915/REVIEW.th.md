# ตรวจระบบผัก Evergarden — 2026-09-15

## ขอบเขต
ตรวจซอร์สระบบผัก, JAR ที่ผู้ใช้ build ไว้, แพ็ก Java/Bedrock ที่ฝังใน JAR และทดลองบน Paper 26.2 build 121 ในเซิร์ฟเวอร์แยก ไม่มี Bedrock/Java client จริงเชื่อมต่อ ไม่ได้แก้ซอร์สหรือแทน JAR ของผู้ใช้

## 1. ประสิทธิภาพ
- หนึ่งต้นสร้าง ItemDisplay + Interaction รวม 2 entities: 1,000 ต้น = 2,000 entities ก่อนนับไอเท็มตกพื้น
- VoidscapePlugin.java:116 เรียก tick ทุก 10 server ticks (0.5 วินาทีที่ 20 TPS) ดังนั้น CropService ambient ทุก 2 รอบ = 1 วินาที และ autosave ทุก 30 รอบ = 15 วินาที ไม่ใช่ 4/60 วินาทีตาม comment
- CropService.java:507 บันทึก YAML ของทุกต้นบนเธรดหลักทุกครั้งที่ปลูก/เก็บเกี่ยว รวมทั้ง autosave แม้ข้อมูลไม่เปลี่ยน
- Benchmark รอบแรก: save 100 ต้น median 2.41 ms; 1,000 ต้น 9.96 ms; 5,000 ต้น 62.16 ms (สูงสุด 71.25 ms), 5 ครั้งต่อขนาด บนเครื่องตรวจนี้ ข้อมูลเป็นต้นสังเคราะห์สำหรับวัด serialization/write เท่านั้น ไม่ใช่ load test ของ 5,000 entities หรือผู้เล่นจริง และไม่ใช่ข้อรับประกันความจุเซิร์ฟเวอร์
- รอบทวน probe: save 100 ต้น median 1.59 ms; 1,000 ต้น 14.77 ms; 5,000 ต้น 66.45 ms สูงสุด 115.61 ms (ดู results.txt)
- การค้นหาเป้าหมายใน onDamageCrop/onInteractEntity วนทุกต้น; onDamageCrop ทำงานกับ damage event ทั่วไปด้วย ส่วน Flora Aura วนทุกต้นต่อผู้เล่นที่มีชาร์จ
- มีข้อดีคือ tick ข้าม chunk ที่ไม่ได้โหลด แต่ไม่มีเพดานจำนวนต้น/particle budget
- แนวทาง: batch save เฉพาะ dirty state, serialize snapshot และเขียนไฟล์แบบ atomic นอกเธรดหลัก, index UUID -> crop, กระจายงาน tick, จำกัด particles/จำนวนต้น และ profile ด้วยผู้เล่นจริง

## 2. ปัญหายืนยันจากการทดสอบ
1. ผู้เล่นไม่ใช่ OP เปิด `/evergarden crops` แล้วกดช่อง 45 ได้เมล็ดครบ 30 ชนิด x16 ไม่มี permission guard ใน command หรือ GUI (VoidCommand.java:25–33, CropShowcaseGui.java:148)
2. ต้นที่ปล่อยสุกไว้นาน 3 รอบ เก็บแล้ว tick ถัดไปสุกซ้ำทันที เพราะ auto-replant บวก plantedAt แค่หนึ่งระยะโต แทนการตั้งเวลาเป็นปัจจุบัน (CropService.java:465)
3. Mana Dew Berry: ทดสอบ account max=200, mana=0 กินแล้วได้ 100 แทน 50 เพราะอ่าน PDC `advance_magic:*` แต่ ManaService ใช้ `advance-magic:*` ก่อน sync ด้วย reflection (CropBuffListener.java:812–846)
4. Double Jump หมดเวลาแล้วยัง allowFlight=true และไม่ยกเลิกการสลับบินใน Survival (CropBuffListener.java:615–645); ไม่มี cleanup ตอนหมดเวลา/ออกเกม/ตายสำหรับสิทธิ์นี้
5. Flora Aura แก้ Location ตัวจริงของต้นด้วย `.add(0.5,0.5,0.5)` เพราะ PlantedCrop.getLocation() คืน object เดิม ทำให้พิกัดค่อย ๆ เลื่อนและไม่ตรง map key (CropBuffListener.java:793, PlantedCrop.java:24); มี probe แยกเพื่อเลือกต้นเดียวในผลทดสอบ

ปลูกทั้ง 30 ชนิดผ่าน, growth เปลี่ยนเป็นสุกผ่าน, เก็บแล้วมีผลผลิตผ่าน, save/load จำนวนต้นผ่าน แต่ไม่ได้หมายความว่าบัฟทั้งหมดทำงานครบ

## 3. ปัญหาจากโค้ดที่ยังไม่ได้ทดสอบครบ
- Chrono Pepper, Twilight Grape, Ethereal Mint, Bloodburn Chili, Omni Pomegranate แค่ใส่ map/แสดงข้อความ ไม่พบส่วนอ่านค่าไปแก้การร่ายเวท; Prism Shard Carrot มี hasVaultFortune แต่ไม่พบ caller ในระบบ Vault
- Overcharge กินซ้ำทับ originalMax ด้วยค่าที่บัฟแล้ว และถ้าหมดเวลาขณะ offline จะลบค่าก่อนคืน max; close ไม่คืน Overcharge (CropBuffListener.java:295,772)
- Chain Lightning เรียก m.damage(...,attacker) ภายใน damage handler โดยไม่มี reentrancy guard เสี่ยง trigger สายฟ้าซ้อนกลับไปมา (CropBuffListener.java:497)
- onInteractFarmland ไม่ ignoreCancelled จึงยังเก็บได้แม้ event ถูกยกเลิกโดย protection; Tree Feller ทำลายบล็อกข้างเคียงตรง ๆ โดยไม่ส่ง BlockBreakEvent รายบล็อก
- removeEntities ลบ ItemDisplay/Interaction ทุกตัวในรัศมีโดยไม่มี owner tag; recovery ก็หยิบ entity ใกล้เคียงโดยไม่ตรวจเจ้าของ (CropService.java:185,218,490)
- Void Bounce ใช้ Y < -50 ทุกโลก: อาจวาร์ปคนที่กำลังขุดในชั้นลึก Overworld ตามปกติ
- Ancient Astral Root/Yggdrasil Sprout แจ้งปฏิเสธเมื่อใช้ซ้ำ แต่ไม่ cancel consume event จึงยังเสียอาหาร
- GUI บอกว่าใช้ Bone Meal ได้ แต่ CropService ปิด Bone Meal; Component.text ที่ใส่ § สีตรง ๆ ทำให้ข้อความไม่ได้ถูก parse แบบ legacy

## 4. Bedrock / Java
- Java ฝั่ง Paper ยืนยัน server-side ปลูกได้ 30 แบบ แต่ยังไม่มีภาพใน client จริง
- เมนูแยกเมล็ด/อาหารด้วยคลิกซ้าย/ขวา ใช้เหมือนกันไม่ได้บน Bedrock ตามข้อจำกัด Geyser; ปุ่มแจกอาหารเป็นชุดช่วยได้บางส่วน
- Abyssal Kelp ใช้ GLOWING ซึ่ง Bedrock ไม่รองรับตาม Geyser
- ต้นใช้ ItemDisplay แต่แพ็กผัก Bedrock มีแค่ไอคอน ไม่มี crop geometry/attachables หรือ mapping สำหรับ display extension จึงยังยืนยันไม่ได้ว่าต้นในโลกมองเห็นเหมือน Java; แพ็กไอเท็มอย่างเดียวไม่ได้รับรองส่วนนี้
- Creative bridge ของ Advance Magic ยังไม่ resolve `voidscape:seed_*` / `voidscape:crop_*`; ยังควรใช้การให้ไอเท็มจาก server แทนการเชื่อว่า Creative entry ทุกตัวใช้ได้
- อ้างอิง: https://geysermc.org/wiki/geyser/current-limitations/ และ https://github.com/GeyserExtensionists/GeyserDisplayEntity

## 5. Resource pack
- JAR ฝังแพ็กตรงกับ dist ทั้ง ZIP, MCPACK, mappings และ hashes
- ครบ 150 แบบ: เมล็ด 30, ผลผลิต 30, ต้น 3 ระยะ x30; textures ทุกภาพ 32x32 และ Java/Bedrock ใช้ไฟล์ภาพตรงกัน
- รวม mapping 170 รายการ ไม่ซ้ำ ไม่ชน Advance Magic; Java selector และ Bedrock atlas อ้างไฟล์ครบ; ZIP/JSON/hash ตรวจผ่าน
- ขนาดแพ็ก Java 429,609 bytes, Bedrock 259,041 bytes: ตัวแพ็กไม่ใหญ่ ปัญหาประสิทธิภาพหลักอยู่ที่ entities/tick/save
- ภาพไอเท็มแยกสี/ทรงพอใช้ แต่ต้นทุกชนิดใช้ทรงคล้ายกันมาก เน้นเปลี่ยนสีผล; ยังไม่สื่อเห็ด ไผ่ ข้าวโพด ฯลฯ แตกต่างชัดเจน
- พบปื้นดำจริงใน texture จาก outline pass ที่เขียนลง array เดียวกับที่กำลังอ่าน ทำให้ pixel ขอบใหม่แพร่ต่อในลูป (crop_assets.py:374 และ453; เมล็ดมีลักษณะเดียวกัน)
- Java model ของต้นเป็น cross สองแผ่น ไม่ใช่โมเดล volumetric; 32x32 เพียงพอสำหรับแนว pixel art แต่ควรแก้ alpha/outline และรูปทรงก่อนเพิ่ม resolution
- Java pack metadata ระบุ min/max format 88 เท่านั้น ไม่ได้ประกาศรองรับ Java รุ่นเก่าทุกเวอร์ชัน
- `evergarden/tests/check_packs.py` เดิมล้มที่ expected identifiers เพราะยังคาดรายการก่อนเพิ่มผัก ต้องขยาย expected/test coverage; การล้มนี้ไม่ใช่หลักฐานว่า mapping ใหม่เสีย

## ลำดับที่ควรแก้
1. ปิดแจกฟรีแก่ผู้เล่นทั่วไป, แก้เวลาเก็บเกี่ยว/พิกัด Aura/สิทธิ์บินค้าง
2. แก้ namespace มานา, Overcharge และเชื่อมบัฟที่ยังไม่มีผลจริง
3. ลด synchronous full-save และ linear scans, ป้องกัน damage recursion/การข้าม protection
4. รองรับต้นและปุ่มใช้งาน Bedrock โดยตรง
5. แก้ขอบดำและออกแบบต้นให้แยกชนิด แล้วทดสอบภาพใน client ทั้งสองฝั่ง
