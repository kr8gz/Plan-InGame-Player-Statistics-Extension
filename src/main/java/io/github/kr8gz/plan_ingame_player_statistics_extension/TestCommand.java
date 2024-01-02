package io.github.kr8gz.plan_ingame_player_statistics_extension;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// remove this class before releasing
public class TestCommand {
    private static final String STAT_ARGUMENT_NAME = "stat";

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("test")
                .then(CommandManager.argument(STAT_ARGUMENT_NAME, IdentifierArgumentType.identifier())
                        .suggests(TestCommand::getSuggestions)
                        .executes(TestCommand::execute)));
    }

    private static CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        return CommandSource.suggestIdentifiers(Stats.CUSTOM.getRegistry().getIds(), builder);
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        var optionalStat = Optional.ofNullable(Registries.CUSTOM_STAT.get(IdentifierArgumentType.getIdentifier(context, STAT_ARGUMENT_NAME)));
        var stat = Stats.CUSTOM.getOrCreateStat(optionalStat.orElseThrow(() -> new CommandException(Text.literal("Unknown stat"))));

        var statMap = getQueryAPIAccessor().getStatForAllPlayers(stat);
        if (statMap.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No data"), false);
        } else {
            statMap.forEach((uuid, value) -> {
                var message = Text.literal("%s's value for %s: %s".formatted(uuid, stat.getName(), value));
                context.getSource().sendFeedback(() -> message, false);
            });
        }
        return Command.SINGLE_SUCCESS;
    }

    private static QueryAPIAccessor getQueryAPIAccessor() {
        return PlanHook.getQueryAPIAccessor().orElseThrow(() -> new CommandException(Text.literal("Plan is not enabled!")));
    }
}
