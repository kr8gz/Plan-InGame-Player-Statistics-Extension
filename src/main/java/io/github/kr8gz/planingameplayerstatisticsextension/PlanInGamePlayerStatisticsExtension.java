package io.github.kr8gz.planingameplayerstatisticsextension;

import net.fabricmc.api.DedicatedServerModInitializer;

public class PlanInGamePlayerStatisticsExtension implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        PlanHook.hookIntoPlan();
    }
}
