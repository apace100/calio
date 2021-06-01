package io.github.apace100.calio;

import io.github.apace100.calio.mixin.CriteriaRegistryInvoker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.MessageType;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;

public class Calio implements ModInitializer {

	public static final Identifier PACKET_SHARE_ITEM = new Identifier("calio", "share_item");

	@Override
	public void onInitialize() {
		CriteriaRegistryInvoker.callRegister(CodeTriggerCriterion.INSTANCE);
		ServerPlayNetworking.registerGlobalReceiver(PACKET_SHARE_ITEM, ((minecraftServer, serverPlayerEntity, serverPlayNetworkHandler, packetByteBuf, packetSender) -> {
			Text text = Text.Serializer.fromJson(packetByteBuf.readString(32767));
			minecraftServer.execute(() -> {
				Text chatText = new TranslatableText("chat.type.text", serverPlayerEntity.getDisplayName(), text);
				minecraftServer.getPlayerManager().broadcastChatMessage(chatText, MessageType.CHAT, serverPlayerEntity.getUuid());
			});
		}));
	}

	public static boolean hasNonItalicName(ItemStack stack) {
		return stack.hasTag() && stack.getSubTag("display") != null && stack.getSubTag("display").getBoolean(TagConstants.NON_ITALIC_NAME);
	}

	public static void setNameNonItalic(ItemStack stack) {
		if(stack != null)
			stack.getOrCreateSubTag("display").putBoolean(TagConstants.NON_ITALIC_NAME, true);
	}

	public static boolean areEntityAttributesAdditional(ItemStack stack) {
		return stack.hasTag() && stack.getTag().contains(TagConstants.ADDITIONAL_ATTRIBUTES) && stack.getTag().getBoolean(TagConstants.ADDITIONAL_ATTRIBUTES);
	}

	/**
	 * Sets whether the item stack counts the entity attribute modifiers specified in its tag as additional,
	 * meaning they won't overwrite the equipment's inherent modifiers.
	 * @param stack
	 * @param additional
	 */
	public static void setEntityAttributesAdditional(ItemStack stack, boolean additional) {
		if(stack != null) {
			if(additional) {
				stack.getOrCreateTag().putBoolean(TagConstants.ADDITIONAL_ATTRIBUTES, true);
			} else {
				if(stack.hasTag()) {
					stack.getTag().remove(TagConstants.ADDITIONAL_ATTRIBUTES);
				}
			}
		}
	}
}
