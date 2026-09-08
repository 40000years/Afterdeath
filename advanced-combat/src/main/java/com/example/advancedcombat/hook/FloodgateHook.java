package com.example.advancedcombat.hook;

import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

public final class FloodgateHook {

    private FloodgateHook() {}

    public static boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
