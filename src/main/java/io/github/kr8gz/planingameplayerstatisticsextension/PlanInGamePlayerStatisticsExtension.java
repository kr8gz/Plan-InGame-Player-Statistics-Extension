package io.github.kr8gz.planingameplayerstatisticsextension;

import com.djrapitops.plan.capability.CapabilityService;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class PlanInGamePlayerStatisticsExtension implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        PlanHook.hookIntoPlan();

        // FIXME why queryAPIAccessor sometimes null
        ServerLifecycleEvents.SERVER_STARTING.register(server -> System.out.println("Server starting event"));
        CapabilityService.getInstance().registerEnableListener(isPlanEnabled -> {
            if (isPlanEnabled) System.out.println("Plan starting event");
        });
    }
}
