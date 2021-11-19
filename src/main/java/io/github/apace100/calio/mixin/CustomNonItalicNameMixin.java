package io.github.apace100.calio.mixin;

import io.github.apace100.calio.Calio;
import io.github.apace100.calio.NbtConstants;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/***
 * These mixins allow setting a custom name for item stacks via NBT.
 */
public abstract class CustomNonItalicNameMixin {

    @Mixin(ItemStack.class)
    public abstract static class ModifyItalicDisplayItem {
        @Redirect(method = "getTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasCustomName()Z"))
        private boolean hasCustomNameWhichIsItalic(ItemStack stack) {
            return stack.hasCustomName() && !Calio.hasNonItalicName(stack);
        }
    }

    @Mixin(InGameHud.class)
    public abstract static class ModifyItalicDisplayHud {
        @Redirect(method = "renderHeldItemTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasCustomName()Z"))
        private boolean hasCustomNameWhichIsItalic(ItemStack stack) {
            return stack.hasCustomName() && !Calio.hasNonItalicName(stack);
        }
    }

    @Mixin(AnvilScreenHandler.class)
    public abstract static class RemoveNonItalicOnRename {
        @Inject(method = "updateResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;setCustomName(Lnet/minecraft/text/Text;)Lnet/minecraft/item/ItemStack;"), locals = LocalCapture.CAPTURE_FAILHARD)
        private void removeNonItalicFlag(CallbackInfo ci, ItemStack itemStack, int i, int j, int k, ItemStack itemStack2) {
            NbtCompound display = itemStack2.getSubNbt("display");
            if(display != null && display.contains(NbtConstants.NON_ITALIC_NAME)) {
                display.remove(NbtConstants.NON_ITALIC_NAME);
            }
        }
    }
}
