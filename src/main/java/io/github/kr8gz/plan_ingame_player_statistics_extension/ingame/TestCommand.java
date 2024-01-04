package io.github.kr8gz.plan_ingame_player_statistics_extension.ingame;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.kr8gz.plan_ingame_player_statistics_extension.PlanHook;
import io.github.kr8gz.plan_ingame_player_statistics_extension.DatabaseManager;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// remove or deactivate this class before releasing
public class TestCommand {
    private static final String STAT_ARGUMENT_NAME = "stat";
    private static final String UUID_ARGUMENT_NAME = "uuid";

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("test")
                .then(CommandManager.literal("simple")
                        .then(CommandManager.argument(STAT_ARGUMENT_NAME, IdentifierArgumentType.identifier())
                                .suggests(TestCommand::getStatSuggestions)
                                .executes(TestCommand::executeSimple)))
                .then(CommandManager.literal("ranked")
                        .then(CommandManager.argument(UUID_ARGUMENT_NAME, UuidArgumentType.uuid())
                                .executes(TestCommand::executeRanked)))
        );
    }

    private static CompletableFuture<Suggestions> getStatSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        return CommandSource.suggestIdentifiers(Stats.CUSTOM.getRegistry().getIds(), builder);
    }

    private static int executeSimple(CommandContext<ServerCommandSource> context) {
        var optionalStat = Optional.ofNullable(Registries.CUSTOM_STAT.get(IdentifierArgumentType.getIdentifier(context, STAT_ARGUMENT_NAME)));
        var stat = Stats.CUSTOM.getOrCreateStat(optionalStat.orElseThrow(() -> new CommandException(Text.literal("Unknown stat"))));

        var statMap = getDatabaseManager().getStatForAllPlayers(stat);
        if (statMap.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No data"), false);
        } else {
            statMap.forEach((uuid, value) -> {
                var message = Text.literal("%s | %s".formatted(uuid, value));
                context.getSource().sendFeedback(() -> message, false);
            });
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeRanked(CommandContext<ServerCommandSource> context) {
        var playerTopStats = getDatabaseManager().getPlayerTopStats(UuidArgumentType.getUuid(context, UUID_ARGUMENT_NAME));
        if (playerTopStats.isEmpty()) throw new CommandException(Text.literal("No data for given UUID"));
        playerTopStats.forEach(stat -> {
            var message = Text.literal("%s = %s (#%s)".formatted(stat.statName(), stat.statValue(), stat.rank()));
            context.getSource().sendFeedback(() -> message, false);
        });
        return Command.SINGLE_SUCCESS;
    }

    private static DatabaseManager getDatabaseManager() {
        return PlanHook.getDatabaseManager().orElseThrow(() -> new CommandException(Text.literal("Plan is not enabled!")));
    }
}
