package io.github.apace100.calio.util;

import net.minecraft.tag.ServerTagManagerHolder;
import net.minecraft.tag.TagManager;

public class ServerTagManagerGetter implements TagManagerGetter {
    @Override
    public TagManager get() {
        return ServerTagManagerHolder.getTagManager();
    }
}
