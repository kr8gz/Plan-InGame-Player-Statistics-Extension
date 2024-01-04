package io.github.kr8gz.plan_ingame_player_statistics_extension.web;

import com.djrapitops.plan.delivery.web.resolver.Resolver;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.exception.BadRequestException;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.WebUser;

import java.util.Optional;
import java.util.Random;

public class IngameStatsJSONResolver implements Resolver {
    @Override
    public boolean canAccess(Request request) {
        var user = request.getUser().orElse(new WebUser(""));
        return user.hasPermission("page.server");
    }

    @Override
    public Optional<Response> resolve(Request request) {
        var query = request.getQuery();

        var jsonCreator = query.get("key")
                .map(key -> ServerIngameStatsJSONCreator.getByKey(key).orElseThrow(() -> new BadRequestException("Invalid key specified")))
                .orElseGet(() -> {
                    var all = ServerIngameStatsJSONCreator.getAll();
                    var randomIndex = new Random().nextInt(all.size());
                    return all.get(randomIndex);
                });

        return Optional.of(jsonCreator.getJSONResponse());
    }
}
