package io.github.apace100.calio.network;

import io.github.apace100.calio.registry.DataObjectRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class CalioNetworkingClient {

    public static void registerReceivers() {
        ClientPlayConnectionEvents.INIT.register(((clientPlayNetworkHandler, minecraftClient) -> {
            ClientPlayNetworking.registerReceiver(
                CalioNetworking.SYNC_DATA_OBJECT_REGISTRY,
                CalioNetworkingClient::onDataObjectRegistrySync
            );
        }));
    }

    private static void onDataObjectRegistrySync(
        MinecraftClient minecraftClient,
        ClientPlayNetworkHandler clientPlayNetworkHandler,
        PacketByteBuf packetByteBuf,
        PacketSender packetSender) {
        Identifier registryId = packetByteBuf.readIdentifier();
        minecraftClient.execute(() -> {
            DataObjectRegistry.getRegistry(registryId).receive(packetByteBuf);
        });
    }
}
