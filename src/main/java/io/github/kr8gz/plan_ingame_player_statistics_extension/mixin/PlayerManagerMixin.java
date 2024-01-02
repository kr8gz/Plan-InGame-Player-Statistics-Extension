package io.github.kr8gz.plan_ingame_player_statistics_extension.mixin;

import io.github.kr8gz.plan_ingame_player_statistics_extension.PlanHook;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Shadow @Final private Map<UUID, ServerStatHandler> statisticsMap;

    @Shadow @Final private List<ServerPlayerEntity> players;

    @Inject(method = "remove(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;savePlayerData(Lnet/minecraft/server/network/ServerPlayerEntity;)V"))
    private void remove(ServerPlayerEntity player, CallbackInfo ci) {
        updatePlayerStats(statisticsMap.get(player.getUuid()));
    }

    @Inject(method = "saveAllPlayerData()V", at = @At("HEAD"))
    private void saveAllPlayerData(CallbackInfo ci) {
        players.stream()
                .map(player -> statisticsMap.get(player.getUuid()))
                .forEach(PlayerManagerMixin::updatePlayerStats);
    }

    private static void updatePlayerStats(ServerStatHandler statHandler) {
        PlanHook.getQueryAPIAccessor()
                .ifPresent(queryAPIAccessor -> queryAPIAccessor.updatePlayerStats(statHandler));
    }
}
