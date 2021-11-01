package io.github.apace100.calio;

import io.github.apace100.calio.data.SerializableDataTypes;
import io.github.apace100.calio.mixin.HandledScreenFocusedSlotAccessor;
import io.github.apace100.calio.network.CalioNetworkingClient;
import io.github.apace100.calio.util.ClientTagManagerGetter;
import io.github.apace100.calio.util.ServerTagManagerGetter;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class CalioClient implements ClientModInitializer {

    boolean sharedStack = false;

    @Override
    @Environment(EnvType.CLIENT)
    public void onInitializeClient() {
        Calio.tagManagerGetter = new ClientTagManagerGetter();
        ClientTickEvents.START_CLIENT_TICK.register(tick -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if(client.player != null && client.currentScreen instanceof HandledScreen) {
                HandledScreenFocusedSlotAccessor focusedSlotAccessor = (HandledScreenFocusedSlotAccessor)client.currentScreen;
                Slot focusedSlot = focusedSlotAccessor.getFocusedSlot();
                boolean isCtrlPressed = InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL);
                InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(client.options.keyChat);
                boolean isChatPressed = InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), key.getCode());
                if(isCtrlPressed && isChatPressed && !sharedStack) {
                    sharedStack = true;
                    if (client.player.currentScreenHandler.getCursorStack().isEmpty() && focusedSlot != null && focusedSlot.hasStack()) {
                        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                        SerializableDataTypes.ITEM_STACK.send(buf, focusedSlot.getStack());
                        ClientPlayNetworking.send(Calio.PACKET_SHARE_ITEM, buf);
                    }
                }
                if(sharedStack && (!isCtrlPressed || !isChatPressed)) {
                    sharedStack = false;
                }
            }
        });
        CalioNetworkingClient.registerReceivers();
    }
}
