package io.github.kr8gz.plan_ingame_player_statistics_extension.ingame;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.kr8gz.plan_ingame_player_statistics_extension.common.PlanHook;
import io.github.kr8gz.plan_ingame_player_statistics_extension.database.DatabaseManager;
import net.minecraft.command.CommandException;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.stat.Stat;
import net.minecraft.text.Text;

import java.util.Optional;

// remove or deactivate this class before releasing
public class TestCommand {
    private static final String STAT_ARGUMENT_NAME = "stat";
    private static final String UUID_ARGUMENT_NAME = "uuid";

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("test")
                .then(CommandManager.literal("simple")
                        .then(CommandManager.argument(STAT_ARGUMENT_NAME, StringArgumentType.string())
                                .executes(TestCommand::executeSimple)))
                .then(CommandManager.literal("ranked")
                        .then(CommandManager.argument(UUID_ARGUMENT_NAME, UuidArgumentType.uuid())
                                .executes(TestCommand::executeRanked)))
        );
    }

    private static int executeSimple(CommandContext<ServerCommandSource> context) {
        var statMap = Stat.getOrCreateStatCriterion(StringArgumentType.getString(context, STAT_ARGUMENT_NAME))
                .flatMap(scoreboardCriterion -> {
                    if (scoreboardCriterion instanceof Stat<?> stat) {
                        return Optional.of(getDatabaseManager().getStatForAllPlayers(stat));
                    } else {
                        return Optional.empty();
                    }
                })
                .orElseThrow(() -> new CommandException(Text.literal("Unknown stat")));

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
