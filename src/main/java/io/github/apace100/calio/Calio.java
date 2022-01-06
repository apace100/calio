package io.github.apace100.calio;

import io.github.apace100.calio.mixin.CriteriaRegistryInvoker;
import io.github.apace100.calio.util.TagManagerGetter;
import net.fabricmc.api.ModInitializer;
import net.minecraft.item.ItemStack;
import net.minecraft.tag.Tag;
import net.minecraft.tag.TagManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;

public class Calio implements ModInitializer {

	static TagManagerGetter tagManagerGetter;

	@Override
	public void onInitialize() {
		CriteriaRegistryInvoker.callRegister(CodeTriggerCriterion.INSTANCE);
	}

	public static boolean hasNonItalicName(ItemStack stack) {
		return stack.hasNbt() && stack.getSubNbt("display") != null && stack.getSubNbt("display").getBoolean(NbtConstants.NON_ITALIC_NAME);
	}

	public static void setNameNonItalic(ItemStack stack) {
		if(stack != null)
			stack.getOrCreateSubNbt("display").putBoolean(NbtConstants.NON_ITALIC_NAME, true);
	}

	public static boolean areEntityAttributesAdditional(ItemStack stack) {
		return stack.hasNbt() && stack.getNbt().contains(NbtConstants.ADDITIONAL_ATTRIBUTES) && stack.getNbt().getBoolean(NbtConstants.ADDITIONAL_ATTRIBUTES);
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
				stack.getOrCreateNbt().putBoolean(NbtConstants.ADDITIONAL_ATTRIBUTES, true);
			} else {
				if(stack.hasNbt()) {
					stack.getNbt().remove(NbtConstants.ADDITIONAL_ATTRIBUTES);
				}
			}
		}
	}

	public static TagManager getTagManager() {
		return tagManagerGetter.get();
	}

	public static <T> boolean areTagsEqual(RegistryKey<? extends Registry<T>> registryKey, Tag<T> tag1, Tag<T> tag2) {
		if(tag1 == tag2) {
			return true;
		}
		if(tag1 == null || tag2 == null) {
			return false;
		}
		TagManager tagManager = Calio.getTagManager();
		try {
			Identifier id1;
			if(tag1 instanceof Tag.Identified) {
				id1 = ((Tag.Identified)tag1).getId();
			} else {
				id1 = tagManager.getTagId(registryKey, tag1, RuntimeException::new);
			}
			Identifier id2;
			if(tag2 instanceof Tag.Identified) {
				id2 = ((Tag.Identified)tag2).getId();
			} else {
				id2 = tagManager.getTagId(registryKey, tag2, RuntimeException::new);
			}
			return id1.equals(id2);
		} catch (Exception e) {
			return false;
		}
	}
}
