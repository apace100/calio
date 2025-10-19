package io.github.apace100.calio.mixin.integration.connector;

import io.github.apace100.calio.Calio;
import io.github.apace100.calio.NbtConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
        @Shadow @Final private MinecraftClient client;

        @Shadow private int heldItemTooltipFade;

        @Shadow private ItemStack currentStack;

        @Shadow public abstract TextRenderer getTextRenderer();

        @Shadow private int scaledWidth;

        @Shadow private int scaledHeight;

        @Inject(method = "renderHeldItemTooltip", at = @At(value = "HEAD"), cancellable = true)
        private void hasCustomNameWhichIsItalic(DrawContext context, CallbackInfo ci) {
            this.client.getProfiler().push("selectedItemName");
            if (this.heldItemTooltipFade > 0 && !this.currentStack.isEmpty()) {
                int l;
                MutableText mutableText = Text.empty().append(this.currentStack.getName()).formatted(this.currentStack.getRarity().formatting);
                if (this.currentStack.hasCustomName() && !Calio.hasNonItalicName(this.currentStack)) {
                    mutableText.formatted(Formatting.ITALIC);
                }
                int i = this.getTextRenderer().getWidth(mutableText);
                int j = (this.scaledWidth - i) / 2;
                int k = this.scaledHeight - 59;
                if (!this.client.interactionManager.hasStatusBars()) {
                    k += 14;
                }
                if ((l = (int)((float)this.heldItemTooltipFade * 256.0f / 10.0f)) > 255) {
                    l = 255;
                }
                if (l > 0) {
                    context.fill(j - 2, k - 2, j + i + 2, k + this.getTextRenderer().fontHeight + 2, this.client.options.getTextBackgroundColor(0));
                    context.drawTextWithShadow(this.getTextRenderer(), mutableText, j, k, 0xFFFFFF + (l << 24));
                }
            }
            this.client.getProfiler().pop();
            ci.cancel();
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
