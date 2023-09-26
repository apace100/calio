package io.github.apace100.calio.mixin;

import io.github.apace100.calio.registry.DataObjectRegistry;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class RegistrySyncMixin {

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;onSpawn()V"), method = "onPlayerConnect")
    private void autoSyncDataObjectRegistries(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) {
        DataObjectRegistry.performAutoSync(player);
    }
}
