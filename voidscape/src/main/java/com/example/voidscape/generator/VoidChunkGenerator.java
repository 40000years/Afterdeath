package com.example.voidscape.generator;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * VoidChunkGenerator: ระบบมิติ The Void แบบ Mega-Scale (10 ระดับชั้น ซ้อนตั้งแต่ Y = 260 ลงสู่ Y = -60)
 * ใช้พื้นที่ความสูงสูงสุดของ Minecraft (320 บล็อกเต็ม) สร้างมหานครแห่งความมืด 10 ชั้น:
 * - Tier 1 (Y = 260): The Zenith Altar (แท่นบูชาจุดเกิดสูงสุด)
 * - Tier 2 (Y = 220): High Sky Ring (วงแหวนลอยฟ้า รัศมี 32)
 * - Tier 3 (Y = 180): Fractured Highlands (ที่ราบสูงแตกหัก รัศมี 48)
 * - Tier 4 (Y = 140): Upper Bridges & Spires (สะพานมิติมืด รัศมี 64)
 * - Tier 5 (Y = 100): Middle Continent (มหาทวีปชั้นกลาง รัศมี 80)
 * - Tier 6 (Y = 60):  Crying Catacombs (ชั้นสุสานคริสตัลม่วง รัศมี 96)
 * - Tier 7 (Y = 20):  Sculk Undergrowth (ป่าเนื้อเยื่อสคัลก์ รัศมี 112)
 * - Tier 8 (Y = -15): The Lower Abyss (ชั้นเหวลึก รัศมี 128)
 * - Tier 9 (Y = -40): Deep Bedrock Plates (แผ่นเปลือกโลกทมิฬ รัศมี 144)
 * - Tier 10 (Y = -58): The Foundation (ฐานรากขอบล่างสุด รัศมี 160)
 * หลุดพ้น Tier 10 (Y < -64) -> เข้าสู่ "The True Abyss" ดิ่งลงสู่ความว่างเปล่าลึก -1,000+ ไร้ก้นบึ้ง!
 */
public class VoidChunkGenerator extends ChunkGenerator {

    private final SimplexNoiseGenerator noiseTerrain = new SimplexNoiseGenerator(40000L);
    private final SimplexNoiseGenerator noiseWarp = new SimplexNoiseGenerator(55555L);
    private final SimplexNoiseGenerator noiseCavity = new SimplexNoiseGenerator(77777L);

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        int worldX = chunkX << 4;
        int worldZ = chunkZ << 4;

        // 1. กลุ่มมหาทวีป 10 ชั้นรอบจุดเกิด: ครอบคลุมรัศมี Chunk (-11, -11) ถึง (11, 11)
        if (Math.abs(chunkX) <= 11 && Math.abs(chunkZ) <= 11) {
            generateSpawnMegaComplex(worldX, worldZ, chunkData);
        }

        // 2. โซนเกาะยักษ์ทั่วโลก (Global Mega-Archipelago) 5 ระดับชั้น
        for (int x = 0; x < 16; x++) {
            int rx = worldX + x;
            for (int z = 0; z < 16; z++) {
                int rz = worldZ + z;
                double distFromSpawn = Math.sqrt(rx * rx + rz * rz);

                // เว้นโซนทวีปเกิดรอบใน (160 บล็อก)
                if (distFromSpawn < 165.0) continue;

                // คลื่นสร้างเกาะขนาดใหญ่โต (กว้าง 60-120 บล็อก)
                double islandWave = Math.sin(rx * 0.009) * Math.cos(rz * 0.009)
                        + 0.5 * noiseTerrain.noise(rx * 0.008, rz * 0.008);

                if (islandWave > -0.2) {
                    // กระจายตัว 5 ชั้นความสูงทั่วโลก
                    generateIslandSlab(chunkData, x, z, rx, rz, -38, 16, islandWave); // ชั้นใต้พิภพ (-54 ถึง -22)
                    if (islandWave > -0.05) generateIslandSlab(chunkData, x, z, rx, rz, 25, 18, islandWave);  // ชั้นล่าง (7 ถึง 43)
                    if (islandWave > 0.1)  generateIslandSlab(chunkData, x, z, rx, rz, 105, 18, islandWave); // ชั้นกลาง (87 ถึง 123)
                    if (islandWave > 0.25) generateIslandSlab(chunkData, x, z, rx, rz, 185, 18, islandWave); // ชั้นสูง (167 ถึง 203)
                    if (islandWave > 0.4)  generateIslandSlab(chunkData, x, z, rx, rz, 255, 16, islandWave); // ชั้นเมฆา (239 ถึง 271)
                }

                // เสาหินเชื่อมฟ้าดินพุ่งจาก Y = -55 ถึง Y = 260
                double pillarNoise = noiseWarp.noise(rx * 0.03, rz * 0.03);
                if (pillarNoise > 0.70) {
                    for (int y = -55; y <= 260; y++) {
                        if ((y + rx + rz) % 6 == 0) {
                            chunkData.setBlock(x, y, z, Material.CRYING_OBSIDIAN);
                        } else {
                            chunkData.setBlock(x, y, z, Material.BEDROCK);
                        }
                    }
                }
            }
        }
    }

    private void generateIslandSlab(ChunkData chunkData, int x, int z, int rx, int rz, int centerY, int halfThickness, double wave) {
        int topY = Math.min(315, (int) (centerY + halfThickness * (0.6 + 0.4 * wave)));
        int botY = Math.max(-60, (int) (centerY - halfThickness * (0.6 + 0.4 * wave)));

        for (int y = botY; y <= topY; y++) {
            double cavity = noiseCavity.noise(rx * 0.03, y * 0.03, rz * 0.03);
            if (cavity > 0.46) continue;

            setVoidBlock(chunkData, x, y, z, rx, rz, y);
        }
    }

    /**
     * สร้างกลุ่มทวีป 10 ระดับชั้นซ้อนกัน (Spawn Mega-Complex) ตั้งแต่ Y = 260 ลงสู่ Y = -58
     */
    private void generateSpawnMegaComplex(int worldX, int worldZ, ChunkData chunkData) {
        for (int x = 0; x < 16; x++) {
            int rx = worldX + x;
            for (int z = 0; z < 16; z++) {
                int rz = worldZ + z;
                double dist = Math.sqrt(rx * rx + rz * rz);

                // --- Tier 1 (Y = 260): The Zenith Altar (แท่นบูชาจุดเกิดสูงสุด รัศมี 16) ---
                if (dist <= 16.0) {
                    int topY = 260;
                    int botY = (int) (252 + (dist / 2.0));
                    for (int y = botY; y <= topY; y++) {
                        if (y == topY) {
                            if (dist < 2.5) chunkData.setBlock(x, y, z, Material.SCULK_CATALYST);
                            else if (Math.abs(dist - 6.0) < 1.0 || Math.abs(dist - 12.0) < 1.0) chunkData.setBlock(x, y, z, Material.CRYING_OBSIDIAN);
                            else if ((rx + rz) % 4 == 0) chunkData.setBlock(x, y, z, Material.OBSIDIAN);
                            else chunkData.setBlock(x, y, z, Material.BEDROCK);
                        } else {
                            chunkData.setBlock(x, y, z, Material.BEDROCK);
                        }
                    }
                    if (Math.abs(rx) == 9 && Math.abs(rz) == 9) {
                        for (int py = 261; py <= 285; py++) {
                            chunkData.setBlock(x, py, z, (py % 4 == 0) ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                        }
                        chunkData.setBlock(x, 286, z, Material.SCULK_CATALYST);
                    }
                }

                // --- Tier 2 (Y = 220): High Sky Ring (รัศมี 14 ถึง 32) ---
                if (dist >= 12.0 && dist <= 32.0) {
                    fillPlate(chunkData, x, z, rx, rz, 220, 8, dist, 30.0);
                }

                // --- Tier 3 (Y = 180): Fractured Highlands (รัศมี 24 ถึง 48) ---
                if (dist >= 20.0 && dist <= 48.0) {
                    fillPlate(chunkData, x, z, rx, rz, 180, 10, dist, 45.0);
                }

                // --- Tier 4 (Y = 140): Upper Bridges & Spires (รัศมี 32 ถึง 64) ---
                if (dist >= 28.0 && dist <= 64.0) {
                    fillPlate(chunkData, x, z, rx, rz, 140, 10, dist, 60.0);
                }

                // --- Tier 5 (Y = 100): Middle Continent (มหาทวีปชั้นกลาง รัศมี 80) ---
                if (dist <= 80.0) {
                    fillPlate(chunkData, x, z, rx, rz, 100, 12, dist, 76.0);
                }

                // --- Tier 6 (Y = 60): Crying Catacombs (รัศมี 40 ถึง 96) ---
                if (dist >= 35.0 && dist <= 96.0) {
                    fillPlate(chunkData, x, z, rx, rz, 60, 12, dist, 92.0);
                }

                // --- Tier 7 (Y = 20): Sculk Undergrowth (รัศมี 50 ถึง 112) ---
                if (dist >= 45.0 && dist <= 112.0) {
                    fillPlate(chunkData, x, z, rx, rz, 20, 12, dist, 108.0);
                }

                // --- Tier 8 (Y = -15): The Lower Abyss (รัศมี 60 ถึง 128) ---
                if (dist <= 128.0) {
                    fillPlate(chunkData, x, z, rx, rz, -15, 14, dist, 124.0);
                }

                // --- Tier 9 (Y = -40): Deep Bedrock Plates (รัศมี 70 ถึง 144) ---
                if (dist >= 55.0 && dist <= 144.0) {
                    fillPlate(chunkData, x, z, rx, rz, -40, 12, dist, 140.0);
                }

                // --- Tier 10 (Y = -58): The Foundation Threshold (ฐานรากสุดขอบ รัศมี 160) ---
                if (dist <= 160.0) {
                    fillPlate(chunkData, x, z, rx, rz, -56, 8, dist, 155.0);
                }
            }
        }
    }

    private void fillPlate(ChunkData chunkData, int x, int z, int rx, int rz, int centerY, int thickness, double dist, double maxDist) {
        int topY = centerY + thickness / 2;
        int botY = centerY - thickness / 2;

        for (int y = botY; y <= topY; y++) {
            if (y == topY) {
                if (dist > maxDist || (rx + rz) % 5 == 0) {
                    chunkData.setBlock(x, y, z, Material.CRYING_OBSIDIAN);
                } else if ((rx * rz) % 7 == 0) {
                    chunkData.setBlock(x, y, z, Material.SCULK_CATALYST);
                } else {
                    chunkData.setBlock(x, y, z, Material.BEDROCK);
                }
            } else {
                chunkData.setBlock(x, y, z, Material.BEDROCK);
            }
        }
    }

    private void setVoidBlock(ChunkData chunkData, int x, int y, int z, int rx, int rz, int worldY) {
        double blockRnd = (noiseTerrain.noise(rx * 0.08, worldY * 0.08, rz * 0.08) + 1.0) * 0.5;
        if (blockRnd > 0.83) {
            chunkData.setBlock(x, y, z, Material.CRYING_OBSIDIAN);
        } else if (blockRnd > 0.73) {
            chunkData.setBlock(x, y, z, Material.SCULK);
        } else if (blockRnd > 0.67) {
            chunkData.setBlock(x, y, z, Material.SCULK_CATALYST);
        } else if (blockRnd > 0.56) {
            chunkData.setBlock(x, y, z, Material.OBSIDIAN);
        } else {
            chunkData.setBlock(x, y, z, Material.BEDROCK);
        }
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
