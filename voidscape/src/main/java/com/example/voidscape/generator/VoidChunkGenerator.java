package com.example.voidscape.generator;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * VoidChunkGenerator: ระบบมิติแนวดิ่ง 3 ชั้นจำกัด (Finite 3-Layer Descent)
 * และภูมิประเทศสำรวจเวหาแท้จาก Mod Voidscape (Thunder Spires & Anti-Spires)
 *
 * 1. Sector 1 (X: 0, Z: 0): The Thunder Spires (ยอดเขาสายฟ้ามิติมืด)
 *    - จุดเกิด Zenith Altar (Y = 140)
 *    - The Abyssal Vortex: ปล่องเหวดำดิ่งลึกกึ่งกลาง สำหรับทิ้งตัวลงสู่ Layer 2
 *    - เสาศิลาแหลมคม Thunder Spires พุ่งเสียดฟ้า พร้อมประภาคารเรืองแสงนำทาง
 *
 * 2. Sector 2 (X: 4000, Z: 0): The Null Catacombs (สุสานเศษซากมิติไร้รูป)
 *    - จุดรับการดิ่งลงมา (Y = 150)
 *    - The Null Chasm: รอยแยกมิติมืดกึ่งกลาง สำหรับทิ้งตัวลงสู่ Layer 3
 *    - เสาหินห้อยหัวลงมาจากความมืด (Anti-Spires) และสุสานซากหินโบราณ
 *
 * 3. Sector 3 (X: 8000, Z: 0): The Abyssal Foundation (ก้นบึ้งรังราชันย์ไททัน)
 *    - จุดสิ้นสุดความลึก: ลานประลอง Abyssal Colosseum (รัศมี 150 บล็อก ที่ Y = -51)
 *    - ที่สถิตของ Abyssal Warden Boss เลือด 5,000 HP
 *
 * 4. Outer World: เสาศิลา Thunder Spires และ Anti-Spires กว้างใหญ่ไร้ที่สิ้นสุดสำหรับบินสำรวจ
 */
public class VoidChunkGenerator extends ChunkGenerator {

    public static final int SECTOR_1_CENTER_X = 0;
    public static final int SECTOR_2_CENTER_X = 4000;
    public static final int SECTOR_3_CENTER_X = 8000;

    private final SimplexNoiseGenerator noiseSpire = new SimplexNoiseGenerator(40000L);
    private final SimplexNoiseGenerator noiseWarp = new SimplexNoiseGenerator(55555L);
    private final SimplexNoiseGenerator noiseHeight = new SimplexNoiseGenerator(77777L);

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        int worldX = chunkX << 4;
        int worldZ = chunkZ << 4;

        // 1. Sector 1: The Thunder Spires (ศูนย์กลาง X: 0, Z: 0 | Chunk -12 ถึง 12)
        if (Math.abs(chunkX) <= 12 && Math.abs(chunkZ) <= 12) {
            generateSector1ThunderSpires(chunkX, chunkZ, worldX, worldZ, chunkData);
            return;
        }

        // 2. Sector 2: The Null Catacombs (ศูนย์กลาง X: 4000, Z: 0 | Chunk 238 ถึง 262)
        int chunkXFromSec2 = chunkX - 250; // 4000 >> 4 = 250
        if (Math.abs(chunkXFromSec2) <= 12 && Math.abs(chunkZ) <= 12) {
            generateSector2NullCatacombs(chunkX, chunkZ, worldX, worldZ, chunkData);
            return;
        }

        // 3. Sector 3: The Abyssal Foundation (ศูนย์กลาง X: 8000, Z: 0 | Chunk 488 ถึง 512)
        int chunkXFromSec3 = chunkX - 500; // 8000 >> 4 = 500
        if (Math.abs(chunkXFromSec3) <= 12 && Math.abs(chunkZ) <= 12) {
            generateSector3AbyssalFoundation(worldX, worldZ, chunkData);
            return;
        }

        // 4. Outer World: ทะเลเวหาเสาศิลา Thunder Spires & Anti-Spires สำหรับการบินสำรวจ
        generateOuterVoidArchipelago(chunkX, chunkZ, worldX, worldZ, chunkData);
    }

    /**
     * Layer 1: The Thunder Spires (ยอดเขาสายฟ้ามิติมืด)
     */
    private void generateSector1ThunderSpires(int chunkX, int chunkZ, int worldX, int worldZ, ChunkData chunkData) {
        // กึ่งกลางจุดเกิด: แท่นบูชา Zenith Altar และ ปล่องเหวดำดิ่ง Abyssal Vortex
        if (Math.abs(chunkX) <= 2 && Math.abs(chunkZ) <= 2) {
            generateLayer1CentralVortex(worldX, worldZ, chunkData);
        }

        // เสาศิลา Thunder Spires ประจำชั้นที่ 1
        generateSpireField(chunkX, chunkZ, worldX, worldZ, chunkData, 100, 140, false);
    }

    /**
     * ใจกลาง Layer 1: แท่นบูชา Zenith Altar ลอยฟ้า และปล่องเหว Abyssal Vortex ทะลุลงสู่ชั้น 2
     */
    private void generateLayer1CentralVortex(int worldX, int worldZ, ChunkData chunkData) {
        for (int x = 0; x < 16; x++) {
            int rx = worldX + x;
            for (int z = 0; z < 16; z++) {
                int rz = worldZ + z;
                double dist = Math.sqrt(rx * rx + rz * rz);

                // ปล่องเหว Abyssal Vortex (หลุมกลวงโบ๋กลางแท่นบูชา รัศมี 8 บล็อก)
                if (dist < 8.0) {
                    // ปล่อยโล่งเป็นอากาศ ให้ผู้เล่นกระโดดดิ่งลงไป
                    continue;
                }

                // ขอบปล่องเหวเรืองแสง (รัศมี 8 ถึง 12 บล็อก)
                if (dist <= 12.0) {
                    for (int y = 135; y <= 140; y++) {
                        if (y == 140) {
                            chunkData.setBlock(x, y, z, (rx + rz) % 2 == 0 ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE);
                        } else {
                            chunkData.setBlock(x, y, z, Material.BEDROCK);
                        }
                    }
                    continue;
                }

                // แท่นบูชาหลักรอบนอก (รัศมี 12 ถึง 24 บล็อก)
                if (dist <= 24.0) {
                    int thickness = (int) (6 * (1.0 - (dist - 12.0) / 12.0));
                    for (int y = 140 - thickness; y <= 140; y++) {
                        if (y == 140) {
                            if ((rx + rz) % 6 == 0) chunkData.setBlock(x, y, z, Material.CRYING_OBSIDIAN);
                            else if ((rx + rz) % 3 == 0) chunkData.setBlock(x, y, z, Material.POLISHED_BLACKSTONE);
                            else chunkData.setBlock(x, y, z, Material.BEDROCK);
                        } else {
                            chunkData.setBlock(x, y, z, Material.BEDROCK);
                        }
                    }

                    // 4 เสาประภาคารสี่ทิศ
                    if ((Math.abs(rx) == 18 && Math.abs(rz) <= 2) || (Math.abs(rz) == 18 && Math.abs(rx) <= 2)) {
                        for (int py = 141; py <= 152; py++) {
                            chunkData.setBlock(x, py, z, Material.BEDROCK);
                        }
                        chunkData.setBlock(x, 153, z, Material.CRYING_OBSIDIAN);
                    }
                }
            }
        }
    }

    /**
     * Layer 2: The Null Catacombs (สุสานเศษซากมิติไร้รูป - เสาหินห้อยหัว Anti-Spires)
     */
    private void generateSector2NullCatacombs(int chunkX, int chunkZ, int worldX, int worldZ, ChunkData chunkData) {
        int centerX = SECTOR_2_CENTER_X;

        // กึ่งกลางชั้น 2: แท่นรับการดิ่งลงมา และปล่องเหว The Null Chasm ทะลุลงสู่ชั้น 3
        int localChunkX = chunkX - 250;
        if (Math.abs(localChunkX) <= 2 && Math.abs(chunkZ) <= 2) {
            generateLayer2CentralChasm(worldX, worldZ, chunkData, centerX);
        }

        // เสาหินห้อยหัว Anti-Spires ประจำชั้นที่ 2
        generateSpireField(chunkX, chunkZ, worldX, worldZ, chunkData, 90, 130, true);
    }

    /**
     * ใจกลาง Layer 2: แท่นสุสานและปล่องเหว The Null Chasm
     */
    private void generateLayer2CentralChasm(int worldX, int worldZ, ChunkData chunkData, int centerX) {
        for (int x = 0; x < 16; x++) {
            int rx = worldX + x;
            int relX = rx - centerX;
            for (int z = 0; z < 16; z++) {
                int rz = worldZ + z;
                double dist = Math.sqrt(relX * relX + rz * rz);

                // ปล่องเหว Null Chasm กึ่งกลาง (รัศมี 8 บล็อก)
                if (dist < 8.0) {
                    continue; // อากาศโล่งสำหรับดิ่งสู่ชั้น 3
                }

                // ลานสุสานรอบปล่อง (รัศมี 8 ถึง 26 บล็อก)
                if (dist <= 26.0) {
                    int thickness = (int) (7 * (1.0 - (dist - 8.0) / 18.0));
                    for (int y = 100 - thickness; y <= 100; y++) {
                        if (y == 100) {
                            if (dist < 12.0) {
                                chunkData.setBlock(x, y, z, Material.CRYING_OBSIDIAN);
                            } else if ((relX + rz) % 4 == 0) {
                                chunkData.setBlock(x, y, z, Material.POLISHED_BLACKSTONE);
                            } else {
                                chunkData.setBlock(x, y, z, Material.BEDROCK);
                            }
                        } else {
                            chunkData.setBlock(x, y, z, Material.BEDROCK);
                        }
                    }
                }
            }
        }
    }

    /**
     * Layer 3: The Abyssal Foundation (ลานประลอง Abyssal Colosseum ที่สถิตของราชันย์ก้นบึ้งทมิฬ)
     */
    private void generateSector3AbyssalFoundation(int worldX, int worldZ, ChunkData chunkData) {
        int centerX = SECTOR_3_CENTER_X;

        for (int x = 0; x < 16; x++) {
            int rx = worldX + x;
            int relX = rx - centerX;
            for (int z = 0; z < 16; z++) {
                int rz = worldZ + z;
                double dist = Math.sqrt(relX * relX + rz * rz);

                // ลานประลองหลัก (รัศมี 150 บล็อก ที่ Y = -51)
                if (dist <= 150.0) {
                    // ปูพื้นลานประลองหนา 6 บล็อก (Y = -56 ถึง Y = -51)
                    for (int y = -56; y <= -51; y++) {
                        if (y == -51) {
                            if (dist <= 6.0) {
                                // แท่นบูชาจุดเกิดของบอส
                                chunkData.setBlock(x, y, z, (dist <= 2.5) ? Material.REINFORCED_DEEPSLATE : Material.SCULK_CATALYST);
                            } else if ((relX + rz) % 29 == 0) {
                                chunkData.setBlock(x, y, z, Material.CRYING_OBSIDIAN);
                            } else if ((relX + rz) % 5 == 0) {
                                chunkData.setBlock(x, y, z, Material.POLISHED_BLACKSTONE);
                            } else {
                                chunkData.setBlock(x, y, z, Material.BEDROCK);
                            }
                        } else {
                            chunkData.setBlock(x, y, z, Material.BEDROCK);
                        }
                    }

                    // กำแพงทมิฬ Colosseum Rim สูง 14 บล็อกรอบขอบลาน (รัศมี 140 ถึง 150 บล็อก) ป้องกันบอสและคนตกเหว
                    if (dist >= 140.0 && dist <= 150.0) {
                        int wallHeight = (int) (4 + (dist - 140.0) * 1.0);
                        for (int wy = -50; wy <= -51 + wallHeight; wy++) {
                            if ((wy + relX + rz) % 7 == 0) {
                                chunkData.setBlock(x, wy, z, Material.CRYING_OBSIDIAN);
                            } else if ((wy + relX) % 3 == 0) {
                                chunkData.setBlock(x, wy, z, Material.POLISHED_BLACKSTONE);
                            } else {
                                chunkData.setBlock(x, wy, z, Material.BEDROCK);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * สร้างทุ่งเสาศิลา Thunder Spires (พุ่งขึ้นฟ้า) หรือ Anti-Spires (ห้อยหัวลงมา)
     */
    private void generateSpireField(int chunkX, int chunkZ, int worldX, int worldZ, ChunkData chunkData, int minAlt, int maxAlt, boolean isInverted) {
        long seed = ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L) ^ 0x5DEECE66DL;
        Random rand = new Random(seed);

        // โอกาสเกิดเสาหลักใน Chunk นี้ (25% โอกาสเกิดเสาศิลาขนาดใหญ่โต 1-2 ต้น)
        if (rand.nextInt(100) < 25) {
            int spireX = 4 + rand.nextInt(8);
            int spireZ = 4 + rand.nextInt(8);
            int spireHeight = 35 + rand.nextInt(45); // เสาสูง 35-80 บล็อก
            int baseRadius = 4 + rand.nextInt(3);

            int startY = isInverted ? maxAlt : -45;

            // วาดตัวเสาศิลาทรงกรวยเรียวแหลม
            for (int h = 0; h < spireHeight; h++) {
                int y = isInverted ? (startY - h) : (startY + h);
                if (y < -60 || y > 310) break;

                double radiusRatio = 1.0 - ((double) h / (double) spireHeight);
                double currentRadius = Math.max(0.8, baseRadius * Math.pow(radiusRatio, 1.4));

                for (int dx = (int) -currentRadius; dx <= (int) currentRadius; dx++) {
                    for (int dz = (int) -currentRadius; dz <= (int) currentRadius; dz++) {
                        if (dx * dx + dz * dz <= currentRadius * currentRadius) {
                            int px = spireX + dx;
                            int pz = spireZ + dz;
                            if (px >= 0 && px < 16 && pz >= 0 && pz < 16) {
                                if (h == spireHeight - 1 || (h + dx + dz) % 19 == 0) {
                                    chunkData.setBlock(px, y, pz, Material.CRYING_OBSIDIAN);
                                } else if ((h + dx) % 7 == 0) {
                                    chunkData.setBlock(px, y, pz, Material.POLISHED_BLACKSTONE);
                                } else {
                                    chunkData.setBlock(px, y, pz, Material.BEDROCK);
                                }
                            }
                        }
                    }
                }
            }

            // ประภาคารเรืองแสงและหีบสมบัติบนยอดเสา (ลดความถี่ลงเหลือ 10% ตามที่ผู้เล่นต้องการ)
            int topY = isInverted ? (startY - spireHeight + 1) : (startY + spireHeight - 1);
            if (topY >= -55 && topY <= 310) {
                chunkData.setBlock(spireX, topY, spireZ, Material.CRYING_OBSIDIAN);
                if (rand.nextInt(100) < 10 && topY + 1 <= 310 && !isInverted) {
                    chunkData.setBlock(spireX, topY + 1, spireZ, Material.CHEST);
                }
            }

            // เศษหินอุกกาบาตลอยเคว้งหมุนวนรอบเสา (Orbiting Floating Shards)
            int shardCount = 3 + rand.nextInt(4);
            for (int s = 0; s < shardCount; s++) {
                int sx = spireX + rand.nextInt(11) - 5;
                int sz = spireZ + rand.nextInt(11) - 5;
                int sy = startY + rand.nextInt(spireHeight);
                if (sx >= 0 && sx < 16 && sz >= 0 && sz < 16 && sy >= -55 && sy <= 310) {
                    chunkData.setBlock(sx, sy, sz, Material.BEDROCK);
                    if (rand.nextBoolean()) {
                        chunkData.setBlock(sx, sy + 1, sz, Material.CRYING_OBSIDIAN);
                    }
                }
            }
        }
    }

    /**
     * พื้นที่นอกเหนือจาก 3 Sectors: สุ่มสร้างเสาศิลา Thunder Spires & Anti-Spires ให้บินสำรวจได้ไม่มีที่สิ้นสุด
     */
    private void generateOuterVoidArchipelago(int chunkX, int chunkZ, int worldX, int worldZ, ChunkData chunkData) {
        long seed = ((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L) ^ 0x5DEECE66DL;
        Random rand = new Random(seed);

        boolean inverted = (rand.nextInt(100) < 45); // สลับระหว่างเสาพุ่งขึ้นฟ้า กับเสาห้อยหัวลงมา
        generateSpireField(chunkX, chunkZ, worldX, worldZ, chunkData, 90, 140, inverted);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
