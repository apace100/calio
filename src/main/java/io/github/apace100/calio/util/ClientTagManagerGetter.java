package io.github.apace100.calio.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.tag.ServerTagManagerHolder;
import net.minecraft.tag.TagManager;

@Environment(EnvType.CLIENT)
public class ClientTagManagerGetter implements TagManagerGetter {
    @Override
    public TagManager get() {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if(networkHandler != null) {
            TagManager tagManager = networkHandler.getTagManager();
            if(tagManager != null) {
                return tagManager;
            }
        }
        return ServerTagManagerHolder.getTagManager();
    }
}
