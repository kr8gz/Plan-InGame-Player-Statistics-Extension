package io.github.kr8gz.plan_ingame_player_statistics_extension.mixin;

import io.github.kr8gz.plan_ingame_player_statistics_extension.common.PlanHook;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Shadow @Final private Map<UUID, ServerStatHandler> statisticsMap;

    @Shadow @Final private List<ServerPlayerEntity> players;

    @Inject(method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;savePlayerData(Lnet/minecraft/server/network/ServerPlayerEntity;)V"))
    private void remove(ServerPlayerEntity player, CallbackInfo ci) {
        var statHandlers = Collections.singleton(statisticsMap.get(player.getUuid()));
        updatePlayerStats(statHandlers);
    }

    @Inject(method = "saveAllPlayerData()V", at = @At("HEAD"))
    private void saveAllPlayerData(CallbackInfo ci) {
        var statHandlers = players.stream()
                .map(player -> statisticsMap.get(player.getUuid()))
                .toList();

        updatePlayerStats(statHandlers);
    }

    private static void updatePlayerStats(Collection<ServerStatHandler> statHandlers) {
        PlanHook.getDatabaseManager()
                .ifPresent(queryAPIAccessor -> queryAPIAccessor.updatePlayerStats(statHandlers));
    }
}
