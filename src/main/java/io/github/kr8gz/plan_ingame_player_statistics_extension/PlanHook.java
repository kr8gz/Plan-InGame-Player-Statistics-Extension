package io.github.kr8gz.plan_ingame_player_statistics_extension;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.delivery.web.ResourceService;
import io.github.kr8gz.plan_ingame_player_statistics_extension.database.DatabaseManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

public class PlanHook {
    private static final String[] REQUIRED_CAPABILITIES = new String[] {
            "PAGE_EXTENSION_RESOLVERS",
            "PAGE_EXTENSION_RESOLVERS_LIST",
            "PAGE_EXTENSION_RESOURCES",
            "PAGE_EXTENSION_RESOURCES_REGISTER_DIRECT_CUSTOMIZATION",
            "QUERY_API",
    };

    private static final String PAGE_EXTENSIONS_PATH = "/page_extensions/";

    private static boolean isPlanEnabled;
    private static MinecraftServer server;
    private static DatabaseManager databaseManager;

    public static void hookIntoPlan() {
        if (areRequiredCapabilitiesAvailable()) {
            CapabilityService.getInstance().registerEnableListener(isPlanEnabled -> tryRegisterPlanExtensions(isPlanEnabled, server));
            ServerLifecycleEvents.SERVER_STARTING.register(server -> tryRegisterPlanExtensions(isPlanEnabled, server));
        }
    }

    private static boolean areRequiredCapabilitiesAvailable() {
        var capabilities = CapabilityService.getInstance();
        return Arrays.stream(REQUIRED_CAPABILITIES).allMatch(capability -> {
            boolean hasCapability = capabilities.hasCapability(capability);
            if (!hasCapability) {
                PlanInGamePlayerStatisticsExtension.LOGGER.error("Plan doesn't have required capability '{}' - please try updating Plan or this extension", capability);
            }
            return hasCapability;
        });
    }

    private static void tryRegisterPlanExtensions(boolean isPlanEnabled, MinecraftServer server) {
        PlanHook.isPlanEnabled = isPlanEnabled;
        PlanHook.server = server;
        if (!isPlanEnabled || server == null) return;

        try {
            registerPageExtension("index.html", "example.js");
            databaseManager = new DatabaseManager(server); // run this last as it may take a long time to initialize
        } catch (Exception e) {
            PlanInGamePlayerStatisticsExtension.LOGGER.error("Exception occurred while initializing extension", e);
        }
    }

    private static void registerPageExtension(String target, String resource) throws IOException {
        var path = PAGE_EXTENSIONS_PATH + resource;
        try (var inputStream = PlanHook.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                PlanInGamePlayerStatisticsExtension.LOGGER.error("Exception registering page extension: Couldn't find resource {}!", path);
                return;
            }
            if (resource.endsWith(".js")) {
                ResourceService.getInstance().addJavascriptToResource(
                        PlanInGamePlayerStatisticsExtension.NAME,
                        target,
                        ResourceService.Position.PRE_MAIN_SCRIPT,
                        resource,
                        new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                );
            }
        }
    }

    public static Optional<DatabaseManager> getDatabaseManager() {
        return Optional.ofNullable(databaseManager);
    }
}
