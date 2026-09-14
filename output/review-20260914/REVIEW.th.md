# ผลตรวจ Evergarden 3.0.0 และ Advance Magic 1.0.0

ตรวจวันที่ 14 กันยายน 2026 บน Paper 26.2 build 121 / Java 25 โดยอ่านซอร์ส รันชุดทดสอบเดิม และสร้าง diagnostic plugin สำหรับเซิร์ฟเวอร์ทิ้งได้ที่ `.audit-plugins/review-20260914` เท่านั้น

เริ่มตรวจที่ commit `534f2b6` ระหว่างงานมี commit `f98b2cb` แก้ Vein Smelter เข้ามา จึงคัดลอก JAR ใหม่และรัน diagnostic ซ้ำ ผลที่แนบเป็นรอบหลัง พบปัญหาการข้าม protection เช่นเดิม แม้การคำนวณดรอปเปลี่ยนแล้ว

งานนี้เป็นรายงานตรวจบัคและคู่มือ ยังไม่ได้แก้ implementation ของปลั๊กอินหรือ deploy ไปเซิร์ฟเวอร์ใช้งานจริง ไฟล์ JAR ที่ rebuild แล้วต่างเฉพาะเวลาใน ZIP ถูกคืนเป็นต้นฉบับหลังตรวจเทียบเนื้อหาภายในทุก entry แล้ว

## ปัญหาที่ควรแก้ก่อน

### 1. [P1] Vein Smelter และ Demeter's Scythe ข้ามการป้องกันบล็อกข้างเคียง

- ตำแหน่ง: [UniqueAbilityListener.java:406](../../evergarden/src/main/java/com/example/voidscape/enchant/UniqueAbilityListener.java#L406), [UniqueAbilityListener.java:466](../../evergarden/src/main/java/com/example/voidscape/enchant/UniqueAbilityListener.java#L466)
- เริ่มขุดบล็อกที่อนุญาตติดกับพื้นที่ห้ามขุด: helper เปลี่ยนบล็อกข้างเคียงด้วย `setType(AIR)` / `setBlockData()` และปล่อยของโดยตรง โดยไม่ผ่าน `BlockBreakEvent` ของแต่ละบล็อก
- หลักฐานบน Paper: เปิด listener ที่ cancel BlockBreakEvent ทุกครั้ง แล้วเรียก helper ผ่าน reflection; แร่กลายเป็น AIR และข้าวโตเต็มที่ถูกรีเซ็ตเป็นอายุ 0 แต่ protection ได้รับ event **0 ครั้ง**
- การทดสอบนี้ยืนยันเส้นทาง helper ที่ listener ใช้อยู่ ไม่ใช่การติดตั้ง WorldGuard แล้วเล่นผ่าน client
- ควรให้การขุดแต่ละบล็อกผ่าน pipeline ของผู้เล่น หรือส่ง event ตรวจสิทธิ์ก่อนเปลี่ยนโลก/ให้รางวัล และหยุดเมื่อถูกยกเลิก ต้องระวัง event ของบล็อกต้นทางซ้ำด้วย

### 2. [P1] Soulbound เสี่ยงสูญหายถ้า restart ก่อนเกิดใหม่

- ตำแหน่ง: [UniqueAbilityListener.java:727](../../evergarden/src/main/java/com/example/voidscape/enchant/UniqueAbilityListener.java#L727)
- ตอนตายลบไอเทมออกจาก `getDrops()` แล้วฝากไว้ใน `HashMap soulboundStash` เท่านั้น ไม่มีการบันทึกลงดิสก์หรือ `getItemsToKeep()`
- หลักฐาน: หลัง death handler ได้ `drops=0; itemsToKeep=0; stash=1`; listener ที่สร้างใหม่มี `stash=0` การสูญหายหลัง restart เป็นผลจาก state นี้ ยังไม่ได้จำลองการตายผ่าน client แล้ว restart เต็มรอบ
- ตอน respawn ยังไม่จัดการ leftovers จาก `Inventory.addItem()` หากกระเป๋าเต็มก็มีทางสูญหายอีก
- ควรใช้ระบบเก็บไอเทมตอนตายของ Paper ตามสัญญา API หรือบันทึก pending items แบบถาวร และคืนของโดยไม่ทิ้ง leftovers

### 3. [P1] สลับโลกเพื่อดื่ม Dragon's Breath ซ้ำและเพิ่มมานาได้ทันที

- ตำแหน่ง: [ManaService.java:54](../../advance-magic/src/main/java/com/example/advancemagic/mana/ManaService.java#L54)
- ระบบอ่าน `getWorld().getFullTime()/24000` แล้วเทียบว่าเท่ากับวันล่าสุดหรือไม่ การสลับโลกที่มีเลขวันต่างกันจึงถูกนับเป็นวันใหม่ทุกครั้ง แม้ย้อนกลับไปวันเดิม
- หลักฐานบน Paper: ใน tick เดียวสลับโลกวัน `1 → 2 → 1`, ดื่มสำเร็จ `true,true,true`, max mana เพิ่ม **100 → 115**
- โลก Evergarden มีการหยุดวงจรกลางวัน จึงไม่ควรใช้นาฬิกาของโลกปัจจุบันเป็นฐานร่วม
- ควรกำหนดนาฬิกาอ้างอิงเดียวสำหรับทั้งเซิร์ฟเวอร์ และบล็อกเลขวันที่ไม่มากกว่าวันที่เคยรับแล้ว หากต้องการนโยบายตามวันในเกม

### 4. [P2] คัมภีร์ทำงานแม้ inventory click ถูกยกเลิก

- ตำแหน่ง: [EnchantApplyListener.java:34](../../evergarden/src/main/java/com/example/voidscape/enchant/EnchantApplyListener.java#L34)
- Handler ไม่มี `ignoreCancelled=true` และไม่จำกัดเป้าหมายเป็นช่องอุปกรณ์ในกระเป๋าของผู้เล่น จึงอาจใช้คัมภีร์กับช่อง GUI ที่ปลั๊กอินอื่นห้ามแก้ได้
- หลักฐาน: สร้าง click ที่ `cancelled=true` ก่อนเรียก handler; ดาบยังเปลี่ยนเป็น unbreakable และคัมภีร์ถูกใช้
- ควรข้าม event ที่ถูกยกเลิก จำกัด inventory/slot/click ที่รองรับ และอัปเดต cursor ตามสัญญา inventory event ของ Paper

### 5. [P2] Titan Stance ไม่ลดแรงระเบิดเมื่อไม่ได้ใส่เสื้อเกราะที่มี metadata

- ตำแหน่ง: [UniqueAbilityListener.java:629](../../evergarden/src/main/java/com/example/voidscape/enchant/UniqueAbilityListener.java#L629)
- `onFatalDamage()` return เมื่อไม่มี chestplate หรือ chestplate ไม่มี metadata ก่อนจะไปตรวจ leggings ที่มี Titan Stance
- หลักฐาน: ใส่กางเกง Titan Stance อย่างเดียว รับค่าดาเมจระเบิดจำลอง 10 แล้วยังเป็น **10** แทน **6**; ใส่เสื้อหนังธรรมดาก็ยังเป็น 10
- ควรแยกเงื่อนไข Phoenix Rebirth ของเสื้อออกจาก Titan Stance ของกางเกง และจัดลำดับลดดาเมจก่อนตรวจความตาย

### 6. [P2] ธนูรับคัมภีร์หลายสกิลได้ แต่ใช้งานได้เพียงสกิลแรกในรายการ

- ตำแหน่ง: [UniqueAbilityListener.java:50](../../evergarden/src/main/java/com/example/voidscape/enchant/UniqueAbilityListener.java#L50), [EnchantApplyListener.java:125](../../evergarden/src/main/java/com/example/voidscape/enchant/EnchantApplyListener.java#L125)
- ยืนยันจากซอร์ส: การติดคัมภีร์ปฏิเสธแค่ enchant เดิมซ้ำ แต่ตอนยิงเก็บชื่อสกิลลง PDC เพียงชื่อเดียวแล้ว `break`
- ตัวอย่าง: ติด Colossus Slayer และ Ricochet บนธนูเดียวกัน คัมภีร์ทั้งสองถูกใช้ แต่ลำดับในโค้ดเลือก Colossus Slayer เสมอ; Ricochet ไม่ทำงาน
- ควรให้รองรับรายการสกิลบนลูกธนู หรือกำหนดเป็นสกิลที่ใช้ร่วมกันไม่ได้พร้อมปฏิเสธก่อนกินคัมภีร์ ยังไม่ได้ยิงผ่าน client เพื่อทดสอบกรณีนี้

### 7. [P2] Core ที่แท็ก legacy กับ Evergarden ขัดกันยังผ่านการตรวจ

- ตำแหน่ง: [WandService.java:78](../../advance-magic/src/main/java/com/example/advancemagic/item/WandService.java#L78)
- ถ้าไม่มีแท็ก `core` ของ Advance Magic แต่ `voidscape:magic_core` กับ `evergarden:magic_core` ต่างกัน โค้ดเลือก Evergarden โดยไม่ปฏิเสธ
- หลักฐานบน Paper: แท็ก legacy เป็น `shadow_step` และ Evergarden เป็น `frost_nova` ได้ผล **FROST_NOVA** แทน null
- กระทบความถูกต้องของไอเทมจากการย้ายข้อมูลหรือปลั๊กอินอื่น ไม่ได้หมายความว่าผู้เล่นทั่วไปแก้ PDC เองได้
- ควรเทียบแท็กทุกคู่ที่มีอยู่ให้ตรงกันก่อน parse spell

### 8. [P2] เทสต์ Evergarden ยังยึดโครงสร้างเวอร์ชันเก่า จึงล้มก่อนตรวจครบ

- [LootChecks.java:6](../../evergarden/tests/LootChecks.java#L6) ใช้อาร์เรย์ 6 ช่อง แต่ Reward มี 7 หมวด: รันแล้ว `ArrayIndexOutOfBoundsException: Index 6 out of bounds for length 6`
- expected odds ยังเป็น `{3500,3500,1000,1500,490,10}`; ปัจจุบันควรตรวจแต่ละชื่อ Reward: consumable 3000, equipment 1000, core 1400, unique 2000, limit break 2500, mythic 50, eternity 50
- [check_packs.py:14](../../evergarden/tests/check_packs.py#L14) คาด 13 mappings แต่แพ็กมี **20 identifiers ที่ไม่ซ้ำ** จึง AssertionError
- ลองเปลี่ยนเฉพาะจำนวน 13 เป็น 20 ในโค้ดที่รันในหน่วยความจำโดยไม่แก้ไฟล์ต้นฉบับ: การตรวจ selector, wearable 6 แบบ, fallback, hash, embedded assets และการชน ID ระหว่างปลั๊กอินที่เหลือผ่านทั้งหมด จึงแยกได้ว่าความล้มเหลวจุดแรกมาจาก expectation เก่า

### 9. [P2] คู่มือและเรตในคำอธิบายไม่ตรงกับเกมปัจจุบัน

- [Evergarden README](../../evergarden/README.md), [Advance Magic README](../../advance-magic/README.md) และ comment rewards ใน config ยังระบุรางวัลเพชร/เนเธอไรต์/trim และ Mythic 0.1% ที่ไม่อยู่ในตารางใหม่
- [RelicService.java:352](../../evergarden/src/main/java/com/example/voidscape/item/RelicService.java#L352) หนังสือในเกมบอก **3 เวฟ** แต่ `combat.waves` เริ่มต้น **5**; หน้ารางวัลในหนังสือยังขาดอุปกรณ์พิเศษ 10%
- [WandService.java:53](../../advance-magic/src/main/java/com/example/advancemagic/item/WandService.java#L53) Core ที่สร้างผ่าน Advance Magic ยังเขียน Mythic 0.1% ต่างจาก Core ของ Evergarden ที่เขียน 0.5%
- ควรใช้ค่าจาก config/แหล่งข้อมูลเดียวสร้างข้อความ คู่มือ และ assertions เพื่อลดโอกาสคลาดเคลื่อน อินโฟกราฟิกใหม่ที่แนบใช้ค่าปัจจุบันในซอร์ส

## ผลตรวจและขอบเขต

| การตรวจ | ผล |
|---|---|
| Build Evergarden และ Advance Magic | ผ่าน มี deprecated API warnings |
| Advance Magic accounting / collision | ผ่าน 24 assertions |
| Advance Magic HTTP resource pack | ผ่าน 19 assertions |
| Advance Magic pack validation | ผ่าน |
| Evergarden geometry / placement / core definitions | ผ่านส่วน geometry ก่อนเข้า loot test |
| Evergarden loot suite เดิม | ไม่ผ่าน: อาร์เรย์ 6 ช่อง |
| Evergarden pack suite เดิม | ไม่ผ่าน: คาด 13 แทน 20 mappings |
| Pack suite เมื่อแก้ expectation 20 ในหน่วยความจำ | ผ่านเงื่อนไขที่เหลือ |
| Paper diagnostic plugin / เปิดปลั๊กอินทั้งคู่ | ผ่านการบูต ยืนยันผลใน review-result.txt |

Diagnostic บางข้อเรียก listener/helper โดยตรงเพื่อแยกสาเหตุ ใช้ ItemStack/PDC/โลก/ผู้เล่นจาก Paper จริง แต่ไม่ใช่การควบคุมเกมจาก client ไม่ได้ทดสอบ Bedrock touch controls, ภาพ pack บน client, สู้บอสครบทุกเฟส, ทุก spell sequence หรือ restart กู้ Soulbound ครบวงจร จึงไม่สรุปว่า gameplay ทั้งหมดผ่าน

JAR ที่ใช้ใน diagnostic รอบท้าย (SHA-256):

```text
evergarden.jar     512679AEF643DA2B85FC68B33776B9848172723D6A68DF435AC4BE9979742D5C
advance-magic.jar  C6CB2A01FA6A09D737C5AF117CA317848D902EFE92A167E566FC4F8F42A6102A
```

หลักฐาน: [ผล diagnostic](review-result.txt), [ซอร์ส probe](ReviewChecks.java), [สคริปต์ build probe](build-probe.ps1)

ไฟล์ส่งมอบ: [อินโฟกราฟิกภาษาไทย](evergarden-advance-magic-guide-th.png), [คู่มือข้อความ](PLAY-GUIDE.th.md), [prompt ที่ใช้สร้างภาพ](image-prompt.txt)

ภาพสร้างด้วย built-in imagegen แล้วตรวจข้อความและสูตร 3×3 ด้วยสายตา ขนาดไฟล์ 1024×1536 เป็นภาพประกอบ ไม่ใช่ภาพจากเกม
