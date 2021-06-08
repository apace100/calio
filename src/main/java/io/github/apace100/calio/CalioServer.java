package io.github.apace100.calio;

import io.github.apace100.calio.util.ServerTagManagerGetter;
import net.fabricmc.api.DedicatedServerModInitializer;

public class CalioServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        Calio.tagManagerGetter = new ServerTagManagerGetter();
    }
}
