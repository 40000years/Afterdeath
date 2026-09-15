package com.example.voidscape.crop;

import org.bukkit.Location;
import java.util.UUID;

public final class PlantedCrop {
    private final Location location;
    private final CropType type;
    private int stage;
    private long plantedAt;
    private UUID itemDisplayUuid;
    private UUID interactionUuid;

    public PlantedCrop(Location location, CropType type, int stage, long plantedAt, UUID itemDisplayUuid, UUID interactionUuid) {
        this.location = location;
        this.type = type;
        this.stage = stage;
        this.plantedAt = plantedAt;
        this.itemDisplayUuid = itemDisplayUuid;
        this.interactionUuid = interactionUuid;
    }

    public Location getLocation() { return location.clone(); }
    public CropType getType() { return type; }
    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = Math.max(0, Math.min(2, stage)); }
    public long getPlantedAt() { return plantedAt; }
    public void setPlantedAt(long plantedAt) { this.plantedAt = plantedAt; }
    public UUID getItemDisplayUuid() { return itemDisplayUuid; }
    public void setItemDisplayUuid(UUID uuid) { this.itemDisplayUuid = uuid; }
    public UUID getInteractionUuid() { return interactionUuid; }
    public void setInteractionUuid(UUID uuid) { this.interactionUuid = uuid; }

    public boolean isMature() {
        return stage >= 2;
    }

    public long elapsedSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - plantedAt) / 1000L);
    }

    public double growthProgress() {
        return Math.min(1.0, (double) elapsedSeconds() / type.tier.growthSeconds);
    }

    public int secondsRemaining() {
        return Math.max(0, type.tier.growthSeconds - (int) elapsedSeconds());
    }

    public void accelerate(int seconds) {
        this.plantedAt -= seconds * 1000L;
    }
}
