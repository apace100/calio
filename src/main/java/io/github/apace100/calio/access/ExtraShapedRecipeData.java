package io.github.apace100.calio.access;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;

import java.util.List;
import java.util.Map;

public interface ExtraShapedRecipeData {

    Map<String, Ingredient> calio$getKeyMapping();
    List<String> calio$getPattern();
    ItemStack calio$getResult();

    void calio$setKeyMapping(Map<String, Ingredient> keyMapping);
    void calio$setPattern(List<String> pattern);
    void calio$setResult(ItemStack result);

}
