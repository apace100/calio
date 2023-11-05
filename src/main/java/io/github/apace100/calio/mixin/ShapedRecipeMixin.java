package io.github.apace100.calio.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;
import io.github.apace100.calio.SerializationHelper;
import io.github.apace100.calio.access.ExtraShapedRecipeData;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Mixin(ShapedRecipe.class)
public abstract class ShapedRecipeMixin implements ExtraShapedRecipeData {

    @Unique
    private Map<String, Ingredient> calio$keyMapping = new HashMap<>();

    @Unique
    private List<String> calio$pattern = new LinkedList<>();

    @Unique
    private ItemStack calio$result = ItemStack.EMPTY;

    @Override
    public Map<String, Ingredient> calio$getKeyMapping() {
        return calio$keyMapping;
    }

    @Override
    public List<String> calio$getPattern() {
        return calio$pattern;
    }

    @Override
    public ItemStack calio$getResult() {
        return calio$result;
    }

    @Override
    public void calio$setKeyMapping(Map<String, Ingredient> keyMapping) {
        this.calio$keyMapping = keyMapping;
    }

    @Override
    public void calio$setPattern(List<String> calio$pattern) {
        this.calio$pattern = calio$pattern;
    }

    @Override
    public void calio$setResult(ItemStack calio$result) {
        this.calio$result = calio$result;
    }

    @Mixin(ShapedRecipe.Serializer.class)
    public abstract static class SerializerMixin {

        @ModifyReturnValue(method = "codec", at = @At("RETURN"))
        private Codec<ShapedRecipe> calio$overrideCodec(Codec<ShapedRecipe> original) {
            return SerializationHelper.SHAPED_RECIPE_CODEC;
        }

    }

}
