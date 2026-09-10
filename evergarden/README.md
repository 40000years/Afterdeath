# Evergarden — Twilight Gardens

สวนลอยฟ้าที่ยังมีกลิ่นอายของ Void: ท้องฟ้ายามโพล้เพล้ ป่าซากุระและดอกไม้ บ่อน้ำเรืองแสง หน้าผา Calcite เหนือฐานหินลึก คริสตัล ซุ้มประตูร้าง และรูนลอยกลางอากาศ

- ลานเกิดอยู่ `(0, 97, 0)` มีคู่มือและประตูกลับ ทางแสงเชื่อมวิหารเริ่มต้นทั้งสาม เดินได้โดยไม่ต้องมี Elytra
- พื้นที่สร้างบ้านราบอย่างน้อย 21×21 บล็อกใกล้ `(55, 95, -33)` และ `(-48, 95, 36)` อยู่นอกเขตป้องกันวิหาร
- วิหาร Dark / Astral / Time เป็นอาคารโปร่ง ใช้สีและวัสดุต่างกัน มีวงแหวนขาดลอยอยู่เหนือสนามต่อสู้ ไม่มีเสาตันสูงถึง Y=210
- เวลาเริ่มต้น `dimension.time: 13000` ปรับได้ใน config แล้ว restart
- สร้างจากบล็อก vanilla ทั้งหมด ไม่ต้องใช้ shader หรือ client mod; ภาพท้องฟ้าและแสงจริงขึ้นอยู่กับการตั้งค่าของ client

## Core และการคราฟต์

คลิก Lodestone → ผ่านมอนสเตอร์ 5 เวฟและบอส → รับ Evergarden Key → เปิด Evergarden Vault ได้คนละ 1 ครั้งต่อวิหาร

| รางวัล | โอกาส |
|---|---:|
| Diamond Block | 30% |
| Netherite Ingot | 30% |
| Armor Trim สุ่ม | 20% |
| อุปกรณ์พิเศษ | 10% |
| Magic Core สุ่ม 1 ใน 15 แบบเท่ากัน | 10% |

วาง Core ของเวทที่ต้องการตรงกลาง แล้วล้อมด้วย Netherite Ingot หรือ Nether Star รวม 8 ชิ้น ผสมกันได้ ต้องใช้ Core ที่มีรหัสจากปลั๊กอิน ไม่ใช่ Heart of the Sea ธรรมดา รองรับ Core เก่าและ Core ที่เปลี่ยนชื่อ

[อินโฟกราฟิกภาษาไทย](../advance-magic/dist/advance-magic-guide-th.png) เป็นภาพคอนเซ็ปต์ประกอบคู่มือ ไม่ใช่ภาพหน้าจอจากเกม

## Build และตรวจสอบ

```powershell
./evergarden/build.ps1 -SkipPacks
./advance-magic/build.ps1 -SkipPacks
./evergarden/test.ps1
./advance-magic/test.ps1
./evergarden/tests/build_gardens.ps1
```

`GardensChecks.java` ใช้ Paper 26.2 ในเซิร์ฟเวอร์ทดสอบแยกเท่านั้น ทดสอบการสร้างโลกจริง ทางเดิน จุดเกิด วิหาร และการคราฟต์/ใช้วัตถุดิบของ Core ทั้ง 15 แบบ ผ่าน Bukkit crafting API รวมถึง Core เปลี่ยนชื่อ ไอเทมธรรมดา รหัสขัดกัน วัตถุดิบไม่ครบ และสิทธิ์คราฟต์ เขียน `gardens-result.txt` กับ `gardens-map.png` แล้วปิดเซิร์ฟเวอร์เอง

## เปลี่ยนชื่อจาก Voidscape

โปรเจกต์/ไฟล์หลักเป็น `evergarden/` และ `evergarden.jar` ชื่อปลั๊กอินคือ `Evergarden` ใช้ `/evergarden` หรือ `/garden` และยังรองรับ `/void` กับ `/voidscape`

ตอนเปิดครั้งแรกจะคัดลอก config, world-layout และ dungeons จากโฟลเดอร์ `plugins/Voidscape` มาที่ `plugins/Evergarden` เฉพาะไฟล์ที่ยังไม่มี โดยเก็บไฟล์เดิมไว้ รหัสไอเทม `voidscape:*`, สิทธิ์เดิม และชื่อโลกที่บันทึกไว้ยังเหมือนเดิม จึงใช้กุญแจ Core บ้าน และความคืบหน้าเดิมต่อได้ ไม่มีการสร้างภูมิประเทศใหม่หรือรีเซ็ตโลกในอัปเดตนี้

ปิดเซิร์ฟเวอร์ก่อนนำ `voidscape.jar` ออกจากโฟลเดอร์ plugins แล้วแทนด้วย `evergarden.jar` อย่าลงทั้งสองไฟล์พร้อมกัน

## Resource และมอนสเตอร์

- Java: ส่งแพ็ก Evergarden เพิ่มจากแพ็ก Advance Magic อัตโนมัติ ใช้ HTTP port **8188**; `/evergarden pack` แสดง URL/สถานะ และ `/evergarden pack resend` ส่งใหม่ รองรับ URL ภายนอกและ reverse proxy ใน config
- Bedrock: ลงแพ็กและ mapping ก่อน Geyser โหลด คงชื่อไฟล์ปลายทาง `voidscape-bedrock.mcpack` และ `voidscape.json` เพื่อแทนของเก่า ไม่เพิ่มแพ็ก UUID หรือ mapping ซ้ำ
- Core ลงทะเบียนใน Geyser โดย Advance Magic เท่านั้น กุญแจและ relic ใช้ตัวเลือก Custom Model Data พร้อมภาพ vanilla สำรอง ไอเทมเก่าในกระเป๋า กล่อง กรอบ และไอเทมตกถูกปรับรหัสภาพโดยรักษาชื่อ/ข้อมูลเดิม
- มีโมเดลสวมศีรษะ 6 แบบ: Thorn/Astral/Chrono แบบ Mask สำหรับลูกน้อง และ Crown สำหรับบอส พร้อมชุดสีเฉพาะมอนในวิหาร ใช้ native entity AI; ไม่ต้องใช้ OptiFine หรือม็อดโมเดล ภาพจาก Bedrock client ยังต้องตรวจรับด้วยการเล่นจริง
- `combat.waves: 5` ตั้งได้ 2–12 เวฟ แล้วตามด้วยบอส 1 ตัว จำนวนลูกน้องเพิ่มตามเวฟแต่ไม่เกิน 8 ต่อเวฟและเพดานมอนรวมเดิม การ reload มีผลกับการต่อสู้ครั้งใหม่ จำนวนเวฟของรอบที่เริ่มแล้วคงเดิม
- `combat.custom-appearance: true` เปิดโมเดลและชุด ส่วนบอส Chrono ป้องกันการแปลงเป็นซอมบี้ที่ทำให้รอบการต่อสู้ค้าง

ข้อมูลอ้างอิงรูปแบบ: [Geyser custom items](https://geysermc.org/wiki/geyser/custom-items/), [Bedrock attachables](https://learn.microsoft.com/en-us/minecraft/creator/documents/attachables?view=minecraft-bedrock-stable), [Paper plugin.yml](https://docs.papermc.io/paper/dev/plugin-yml/).
