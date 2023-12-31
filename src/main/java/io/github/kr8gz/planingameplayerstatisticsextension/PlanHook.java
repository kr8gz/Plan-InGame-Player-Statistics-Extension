package io.github.kr8gz.planingameplayerstatisticsextension;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.delivery.web.ResourceService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

// TODO make actual error handling
public class PlanHook {
    private static final String[] REQUIRED_CAPABILITIES = new String[] {
            "PAGE_EXTENSION_RESOLVERS",
            "PAGE_EXTENSION_RESOLVERS_LIST",
            "PAGE_EXTENSION_RESOURCES",
            "PAGE_EXTENSION_RESOURCES_REGISTER_DIRECT_CUSTOMIZATION",
            "QUERY_API",
    };

    private static final String PAGE_EXTENSIONS_PATH = "/page_extensions/";

    private static QueryAPIAccessor queryAPIAccessor;

    public static void hookIntoPlan() {
        // FIXME plan may be enabled before server starting event wtf??
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                if (!areRequiredCapabilitiesAvailable()) return;
                CapabilityService.getInstance().registerEnableListener(isPlanEnabled -> {
                    if (isPlanEnabled) registerPlanExtensions(server);
                });
            }
            catch (NoClassDefFoundError e) {
                System.out.println("Plan is not installed!");
            }
        });
    }

    private static boolean areRequiredCapabilitiesAvailable() {
        var capabilities = CapabilityService.getInstance();
        return Arrays.stream(REQUIRED_CAPABILITIES).allMatch(capability -> {
            boolean hasCapability = capabilities.hasCapability(capability);
            if (!hasCapability) {
                System.out.printf("Plan doesn't have capability '%s', you need to update Plan!%n", capability);
            }
            return hasCapability;
        });
    }

    private static void registerPlanExtensions(MinecraftServer server) {
        try {
            queryAPIAccessor = new QueryAPIAccessor(server);
            registerPageExtension("index.html", "example.js");
        }
        catch (IllegalStateException e) {
            System.out.println("Plan is not enabled!");
        }
    }

    private static void registerPageExtension(String target, String resource) {
        var path = PAGE_EXTENSIONS_PATH + resource;
        try (var inputStream = PlanHook.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                System.out.printf("Couldn't find resource %s!%n", path);
                return;
            }

            if (resource.endsWith(".js")) {
                ResourceService.getInstance().addJavascriptToResource(
                        "Plan In-Game Player Statistics Extension",
                        target,
                        ResourceService.Position.PRE_MAIN_SCRIPT,
                        resource,
                        new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                );
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Optional<QueryAPIAccessor> getQueryAPIAccessor() {
        return Optional.ofNullable(queryAPIAccessor);
    }
}
