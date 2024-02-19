package io.github.apace100.calio.mixin;

import io.github.apace100.calio.Calio;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.DataPackContents;
import net.minecraft.server.command.CommandManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(DataPackContents.class)
public abstract class DataPackContentsMixin {

    @Inject(method = "reload", at = @At("HEAD"))
    private static void calio$cacheDynamicRegistries(ResourceManager manager, DynamicRegistryManager.Immutable dynamicRegistryManager, FeatureSet enabledFeatures, CommandManager.RegistrationEnvironment environment, int functionPermissionLevel, Executor prepareExecutor, Executor applyExecutor, CallbackInfoReturnable<CompletableFuture<DataPackContents>> cir) {
        Calio.DYNAMIC_REGISTRIES.set(dynamicRegistryManager);
    }

}
