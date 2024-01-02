package io.github.kr8gz.plan_ingame_player_statistics_extension;

import io.github.kr8gz.plan_ingame_player_statistics_extension.ingame.TestCommand;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlanInGamePlayerStatisticsExtension implements DedicatedServerModInitializer {
    public static final String NAME = "Plan In-Game Player Statistics Extension";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    @Override
    public void onInitializeServer() {
        PlanHook.hookIntoPlan();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TestCommand.register(dispatcher));
    }
}
