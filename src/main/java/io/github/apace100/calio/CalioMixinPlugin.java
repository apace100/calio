package io.github.apace100.calio;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class CalioMixinPlugin implements IMixinConfigPlugin {

    private boolean isConnectorLoaded = false;

    private List<String> preventMixins = List.of(
            ".mixin.CustomNonItalicNameMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
        isConnectorLoaded = FabricLoader.getInstance().isModLoaded("connectormod");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (preventMixins.stream().anyMatch(mixinClassName::contains)) {
            return !isConnectorLoaded;
        }
        if (mixinClassName.contains(".mixin.integration.connector")) {
            return isConnectorLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
