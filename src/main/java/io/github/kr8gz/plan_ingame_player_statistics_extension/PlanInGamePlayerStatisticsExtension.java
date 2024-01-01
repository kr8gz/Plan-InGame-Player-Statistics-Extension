package io.github.kr8gz.plan_ingame_player_statistics_extension;

import net.fabricmc.api.DedicatedServerModInitializer;

public class PlanInGamePlayerStatisticsExtension implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        PlanHook.hookIntoPlan();
    }
}
