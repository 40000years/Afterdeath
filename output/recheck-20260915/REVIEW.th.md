# Recheck Evergarden + advance-magic — 15 กันยายน 2026

## ขอบเขตและหลักฐาน

- ตรวจซอร์สล่าสุดและ JAR สองไฟล์ที่ root ของ workspace; คอมไพล์ทั้งสองโมดูลผ่าน และ class files ที่คอมไพล์ได้ตรงกับใน JAR ทุกไฟล์ (0 differences)
- ใช้ JAR เหล่านี้กับ Paper 26.2 build 121 ในเซิร์ฟเวอร์ทิ้งได้ที่ bind เฉพาะ 127.0.0.1:25590; ใช้ผู้เล่นจำลองที่มี ServerPlayer จริง และอีเวนต์ทดสอบ ไม่มี Java/Bedrock client จริง
- ชุด AccountingChecks เดิมผ่าน 24 assertions ด้านบัญชีมานาและ collision; ดู [accounting-results.txt](accounting-results.txt) ผลนี้ไม่ได้รับรองทุกเส้นทาง GUI/dupe
- ผลรอบนี้อยู่ใน [results.txt](results.txt), harness อยู่ใน [RecheckChecks.java](RecheckChecks.java)
- การทดสอบ protection ใช้ listener ที่ยกเลิก BlockBreakEvent ของบล็อกข้างเคียง ไม่ใช่ติดตั้ง WorldGuard/ปลั๊กอิน claim จริง จึงยืนยันการข้ามอีเวนต์มาตรฐานได้ แต่ยังไม่ใช่ผล compatibility ของปลั๊กอิน claim ทุกตัว
- ไม่ได้ทำ load test ผู้เล่นจำนวนมาก จึงไม่ระบุ TPS หรือจำนวนผู้เล่นสูงสุดที่รองรับ ไม่ใช้ตัวเลข benchmark ของรายงานเก่ามาอ้างกับโค้ดใหม่
- รอบนี้เป็นการตรวจสอบ ไม่มีการแก้ production source หรือแทนที่ release JAR

## 1. จุดเสี่ยงแลค

### สูง: Tree Feller เรียกตัวเองซ้ำ และสะสมชาร์จได้

[CropBuffListener.java](../../evergarden/src/main/java/com/example/voidscape/crop/CropBuffListener.java):317, 742–790

บัฟ Lumberjack Acorn เพิ่มชาร์จสะสมทีละ 5 โดยไม่มีเพดาน `onOreBreak` เรียก `fellTree` แล้ว `fellTree` ส่ง BlockBreakEvent ของ log กลับเข้า listener เดิม ทั้งที่บล็อกนั้นยังไม่ได้ถูกทำลาย ไม่มี reentrancy guard ของบัฟนี้

**ยืนยันบน Paper:** เริ่ม 5 ชาร์จ ตัดครั้งเดียวเหลือ 0 แทน 4 เมื่อสะสมหลายชาร์จจะเพิ่มความลึกของการเรียกซ้ำและงานค้นหาต้นไม้ มีความเสี่ยง stack overflow/เซิร์ฟเวอร์สะดุด; รอบนี้ไม่ได้ทดสอบจนเซิร์ฟเวอร์ล่ม และไม่สรุปว่าเส้นทางนี้ปั๊มไม้สำเร็จ

แก้โดยกันการเรียกซ้ำต่อผู้เล่น จำกัดชาร์จ และกระจายงานตัดหลายบล็อกออกหลาย tick

### กลาง–สูง: เรดาร์แร่สแกน 17,661 บล็อกใน tick เดียว

[UniqueAbilityListener.java](../../evergarden/src/main/java/com/example/voidscape/enchant/UniqueAbilityListener.java):405–460

Bedrock Resonance วน 29 × 21 × 29 บล็อกบนเธรดหลักต่อการทำงาน มี cooldown 3.5 วินาทีต่อผู้เล่น แต่ไม่มี budget รวม และไม่ได้กรอง chunk ที่โหลดอยู่ก่อนอ่านบล็อก ผู้ใช้หลายคนพร้อมกันเพิ่มงานใน tick เดียว

แก้โดยตรวจ loaded chunk และแบ่งสแกนเป็น batch/cache ผล ไม่ควรย้ายการอ่าน Bukkit World ไป async ตรง ๆ

### กลาง: ฟาร์มใหญ่เพิ่มทั้งงาน tick และ entities โดยไม่มีเพดาน

[CropService.java](../../evergarden/src/main/java/com/example/voidscape/crop/CropService.java):82–130, 302–323

ทุก 10 server ticks วนพืชทั้งหมด แม้จะข้ามขั้นตอนหนักของ chunk ที่ไม่โหลดแล้วก็ตาม พืชแต่ละต้นมี ItemDisplay + Interaction; 1,000 ต้นเท่ากับ 2,000 entities ก่อนนับของตกพื้น และมี particles ตามรอบ ไม่มีเพดานจำนวนต้นหรือ budget งานต่อ tick

**ดีขึ้นจากรอบก่อน:** มี UUID index, ambient ทุก 4 วินาที, dirty autosave ทุก 60 วินาที และเขียน async แล้ว จึงไม่ควรกล่าวว่าบันทึกไฟล์ sync ทุกการเก็บเกี่ยวเหมือนเวอร์ชันก่อน

### กลาง: Vault บันทึก ledger ทั้งก้อนบนเธรดหลัก

[DungeonManager.java](../../evergarden/src/main/java/com/example/voidscape/dungeon/DungeonManager.java):64–71, 197–199

ทุกการเปิด Vault serialize YAML ทั้ง ledger แล้วเขียนดิสก์แบบ synchronous ข้อมูลสะสมตามวิหารและผู้เล่น จึงเสี่ยงสะดุดเมื่อ ledger โตหรือดิสก์ช้า การแก้ต้องรักษาความเป็นธุรกรรมของรางวัลด้วย เช่น durable journal/ฐานข้อมูล ไม่เพียงย้าย save หลังแจกไป async

### advance-magic: มีเพดานงาน แต่ยังต้อง profile เอฟเฟกต์พร้อมกัน

[EffectEngine.java](../../advance-magic/src/main/java/com/example/advancemagic/effect/EffectEngine.java):44–54, [StatusService.java](../../advance-magic/src/main/java/com/example/advancemagic/effect/StatusService.java):98–119

มี global effect limit ค่าเริ่มต้น 128, target limit ค่าเริ่มต้น 32 และ cleanup เมื่อออกเกม/ตาย/เปลี่ยนโลก เป็นข้อดี แต่ shroud ยังสแกน entities ในกล่องรัศมี 48 ทุกครึ่งวินาทีต่อผู้ใช้ และหลายเวทส่ง particles ทุกไม่กี่ ticks ไม่มีหลักฐานจากรอบนี้ว่าโหลดจริงทำ TPS ตกเท่าใด

## 2. บัค/ช่องโหว่เกี่ยวกับของและความได้เปรียบ

### สูง — ยืนยันบน Paper: Vein Smelter และ Demeter's Scythe ข้าม protection ของบล็อกข้างเคียง

[UniqueAbilityListener.java](../../evergarden/src/main/java/com/example/voidscape/enchant/UniqueAbilityListener.java):513–534, 576–618

สกิลขุดแร่ใช้ `setType(AIR)` และแจกของเอง; เคียวเก็บเกี่ยว 9×9 แจกของแล้ว reset อายุพืช โดยไม่ส่ง BlockBreakEvent แยกให้แต่ละบล็อก

**ผล:** บล็อกข้างเคียงที่ listener ทดสอบตั้งใจห้ามถูกขุด/เก็บเกี่ยว และจำนวน protection events เท่ากับ 0 ทั้งสองกรณี จึงขโมยทรัพยากรในพื้นที่ข้างเคียงได้หากระบบ claim พึ่งอีเวนต์ดังกล่าว

แก้โดยตรวจสิทธิ์ทุกบล็อกก่อนเปลี่ยน state/แจกของ และเคารพผลยกเลิกอีเวนต์จนถึงจุด commit

### สูง — จากโค้ด: Ore Resonance กันแร่ที่วางเองด้วย metadata ที่ไม่ถาวร

[CropBuffListener.java](../../evergarden/src/main/java/com/example/voidscape/crop/CropBuffListener.java):705–735

เครื่องหมาย `evergarden_placed` อยู่ใน Bukkit metadata และไม่มีระบบ serialize/load ตำแหน่ง เมื่อรีสตาร์ต เครื่องหมายที่ใช้กันการเพิ่มดรอป 35% ไม่อยู่ต่อ โดยเฉพาะ Ancient Debris ที่ขุดแล้วได้ตัวบล็อกกลับ สามารถนำกลับมาวางและรอให้เครื่องหมายหายก่อนขุดเพิ่มจำนวนได้ ไม่ต้องใช้ Silk Touch

นี่เป็นช่องทางปั๊มแบบมีเงื่อนไขรีสตาร์ต/สูญเสีย marker; รอบนี้ยังไม่ได้ทดสอบวน restart จริง แก้ด้วยข้อมูลตำแหน่งถาวรที่ติดตามการย้ายบล็อก หรือไม่เพิ่มดรอปของบล็อกที่วางคืนได้

### สูง — จากโค้ด: Vault แจกของต่อแม้บันทึกสิทธิ์ล้มเหลว

[DungeonManager.java](../../evergarden/src/main/java/com/example/voidscape/dungeon/DungeonManager.java):178–203

หักกุญแจ ตั้ง opened แล้วเรียก `save()` แต่ไม่ตรวจค่าที่คืน ก่อนแจก reward และ `openVault` ไม่เช็ก `storageHealthy` แบบแท่นเริ่มวิหาร หากดิสก์เขียนไม่ได้ ของยังถูกแจก แต่สิทธิ์เปิดอาจไม่อยู่หลัง restart

เสี่ยง claim replay เมื่อมี key อีกและข้อมูลผู้เล่น/ของอยู่รอด ไม่ใช่ช่องกดซ้ำทันทีในหน่วยความจำปกติ ยังไม่ได้จำลอง disk failure รอบนี้ ควรหยุดให้รางวัลเมื่อ commit ไม่สำเร็จและออกแบบการคืนกุญแจ/กู้ธุรกรรม

### กลาง–สูง — จากโค้ด: การบันทึกพืชยังเสี่ยงข้อมูลหาย/ย้อน

[CropService.java](../../evergarden/src/main/java/com/example/voidscape/crop/CropService.java):674–729, 760

snapshot เป็น shallow copy ของ PlantedCrop ที่ยังแก้ไขได้ และ async save กับ synchronous close ใช้ `crops.yml.tmp` ชื่อเดียวกันโดยไม่มี lock/join ป้องกัน นอกจากนี้ลบไฟล์เดิมก่อน `renameTo` และไม่ตรวจผล rename; เมื่อเขียนล้มเหลวไม่ได้คืน dirty เพื่อ retry

เสี่ยงสูญข้อมูลหรือได้ snapshot เก่าเมื่อ shutdown ชน save อีกทั้ง autosave 60 วินาทีอาจคืนต้นที่เพิ่งเก็บหลัง crash หากของที่แจกบันทึกทันแล้ว ยังไม่ยืนยัน dupe จาก crash จริง แก้ด้วย immutable snapshot, writer เดียว, atomic replace, retry และการประสาน shutdown

### กลาง — ยืนยันบน Paper: Dragon's Breath ข้ามวันละครั้งด้วยการสลับโลก

[ManaService.java](../../advance-magic/src/main/java/com/example/advancemagic/mana/ManaService.java):54–60, 115

ใช้ day จาก `p.getWorld().getFullTime()` แต่เก็บ last day ค่าเดียวต่อผู้เล่น โลกที่อายุวันไม่เท่ากันทำให้ A → B → A ผ่านทุกครั้ง

**ผล:** ทั้ง 3 ครั้งสำเร็จในช่วงทดสอบเดียว เพิ่ม Max Mana/regen ได้โดยไม่รอวันใหม่ แต่ยังเสีย Dragon's Breath จริง ไม่ใช่ปั๊มไอเท็ม แก้โดยใช้นาฬิกาโลกอ้างอิงเดียวหรือ cooldown แบบเวลาที่ตกลงไว้

### สูง — ยืนยันบน Paper: Omni Rebound ยิงได้แม้มานาไม่พอ

[CropBuffListener.java](../../evergarden/src/main/java/com/example/voidscape/crop/CropBuffListener.java):86–138, [CastListener.java](../../advance-magic/src/main/java/com/example/advancemagic/CastListener.java):73–100

MagicCastEvent ถูกส่งก่อนตรวจ cooldown/mana/เป้าหมาย แต่ listener ใช้จังหวะนี้ระเบิดทำดาเมจทันที ผู้เล่นที่มีบัฟจึงเรียกผลโจมตีได้จาก cast ที่ล้มเหลว โดยเหลือเพียง input throttle 150 ms ไม่ได้ผ่านค่าใช้จ่ายเวทปกติ

**ผล:** mana=0, cast คืน false แต่มอนเสียเลือดจริง Arcane Echo ก็หักชาร์จในอีเวนต์ก่อนรู้ผลสำเร็จ ทำให้เสียชาร์จจากการลองร่ายที่ล้มเหลวได้ แก้โดยให้อีเวนต์ก่อนร่ายปรับค่าอย่างเดียว และทำ side effects/ใช้ชาร์จหลังยืนยันสำเร็จ

## 3. ฟีเจอร์ผู้เล่นและ permission

| ฟีเจอร์ | ผลตรวจ |
|---|---|
| ร่ายคทา / คราฟคทา | `advance-magic.cast` และ `.craft` เป็น default true; non-OP ผ่านทั้งเช็กสิทธิ์และคราฟผ่านเมนูจริง |
| เข้า Evergarden / guide / leave | enter default true; guide/leave ไม่ล็อก admin ใน command |
| ดูรายการพืช `/evergarden crops` และ aliases | ถูก guard admin ที่ command ทั้งที่ GUI มีโหมดดูอย่างเดียวและกันแจกของแยกแล้ว; ทดสอบ non-OP เปิดไม่ได้ |
| `/magic pack resend` และ `/evergarden pack resend` | ล็อก admin จึงให้ผู้เล่นส่ง pack ให้ตัวเองใหม่ไม่ได้ ควรแยกจากคำสั่งดูข้อมูลโฮสต์ |
| `/magic items`, give/givecore, Evergarden test/give | เป็นช่องแจกของฟรี ควรคง admin; `/magic` help ปัจจุบันชี้ผู้เล่นไป `items` ทั้งที่ควรชี้ไป `craft` |
| Crop GUI แจกไอเท็ม | มีเช็ก admin ตอนคลิกแล้ว; ทดสอบเปิด GUI โดยตรงให้ non-OP ก็ไม่ได้เมล็ดฟรี |

ตำแหน่ง: [VoidCommand.java](../../evergarden/src/main/java/com/example/voidscape/command/VoidCommand.java):25–38, [CropShowcaseGui.java](../../evergarden/src/main/java/com/example/voidscape/crop/CropShowcaseGui.java):169, [MagicCommand.java](../../advance-magic/src/main/java/com/example/advancemagic/MagicCommand.java):19, 47–50 และ plugin.yml ของทั้งสองโมดูล

### ฟีเจอร์เสีย แต่ไม่ได้เกิดจาก permission

- **Time Dilation บัฟฝ่ายเดียวกัน / Soul Drain ฮีลเพื่อน:** `MagicContext.affect` บรรทัด 68 ปฏิเสธ Player ที่ไม่ใช่ enemy ก่อนส่ง event ขณะที่ลูปฝ่ายเดียวกันใน AreaSpells:169–185 และ ChannelSpells:38 เรียกฟังก์ชันนี้ด้วย ทำให้ฝ่ายเดียวกัน รวมถึงตัวเอง ถูกกรองทิ้ง เป็น logic bug; ให้ OP ก็ไม่แก้
- **Sniper Cast:** CropBuffListener:105 ตั้ง velocity multiplier แต่ค้นทั้งสองโมดูลพบ getter อยู่แค่ประกาศใน MagicCastEvent:29 ไม่มีส่วนร่ายเวทอ่านไปใช้ ความสามารถเพิ่มความเร็ว/ระยะตามบัฟจึงยังไม่เชื่อมเข้าการทำงาน

## ลำดับแก้ที่แนะนำ

1. ปิดการข้าม protection ของ Vein Smelter/Scythe, แก้ Tree Feller recursion และ Omni Rebound ก่อนตรวจค่าร่าย
2. ทำ marker แร่และธุรกรรม Vault/พืชให้ทนต่อ restart และเขียนไฟล์ล้มเหลว
3. ใช้วันอ้างอิงเดียวสำหรับ Dragon's Breath
4. เปิดเมนูดูพืชและ resend pack ให้ผู้เล่น พร้อมคงสิทธิ์แจกของเป็น admin; แก้ friendly effects/Sniper Cast
5. ลดงาน sonar/farm แล้ว profile ด้วยจำนวนผู้เล่นและขนาดฟาร์มที่ใช้จริง
