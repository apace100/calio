package io.github.apace100.calio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.apace100.calio.access.ExtraShapedRecipeData;
import io.github.apace100.calio.mixin.ShapedRecipeAccessor;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.collection.DefaultedList;

import java.util.*;
import java.util.function.Function;

public class SerializationHelper {

    public static Codec<ShapedRecipe> SHAPED_RECIPE_CODEC = ShapedRecipe.Serializer.RawShapedRecipe.CODEC.flatXmap(
        rawShapedRecipe -> {

            String[] unpaddedPattern = ShapedRecipeAccessor.callRemovePadding(rawShapedRecipe.pattern());

            int width = unpaddedPattern[0].length();
            int height = unpaddedPattern.length;

            DefaultedList<Ingredient> ingredients = DefaultedList.ofSize(width * height, Ingredient.EMPTY);
            Set<String> patternKeys = new HashSet<>(rawShapedRecipe.key().keySet());

            for (int sliceIndex = 0; sliceIndex < unpaddedPattern.length; ++sliceIndex) {

                String patternSlice = unpaddedPattern[sliceIndex];

                for (int keyIndex = 0; keyIndex < patternSlice.length(); ++keyIndex) {

                    String patternKey = patternSlice.substring(keyIndex, keyIndex + 1);
                    Ingredient ingredient = patternKey.equals(" ") ? Ingredient.EMPTY : rawShapedRecipe.key().get(patternKey);

                    if (ingredient == null) {
                        return DataResult.error(() -> "Pattern references symbol '" + patternKey + "' but it's not defined in the key!");
                    }

                    patternKeys.remove(patternKey);
                    ingredients.set(keyIndex + width * sliceIndex, ingredient);

                }

            }

            if (!patternKeys.isEmpty()) {
                return DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + patternKeys);
            }

            ShapedRecipe shapedRecipe = new ShapedRecipe(
                rawShapedRecipe.group(),
                rawShapedRecipe.category(),
                width,
                height,
                ingredients,
                rawShapedRecipe.result(),
                rawShapedRecipe.showNotification()
            );

            if (shapedRecipe instanceof ExtraShapedRecipeData extraShapedRecipeData) {

                extraShapedRecipeData.calio$setKeyMapping(rawShapedRecipe.key());
                extraShapedRecipeData.calio$setPattern(rawShapedRecipe.pattern());

                extraShapedRecipeData.calio$setResult(rawShapedRecipe.result());

            }

            return DataResult.success(shapedRecipe);

        },
        shapedRecipe -> {

            if (!(shapedRecipe instanceof ExtraShapedRecipeData extraShapedRecipeData)) {
                return DataResult.error(() -> "Cannot serialize ShapedRecipe with missing key, pattern and result data.");
            }

            ShapedRecipe.Serializer.RawShapedRecipe rawShapedRecipe = new ShapedRecipe.Serializer.RawShapedRecipe(
                shapedRecipe.getGroup(),
                shapedRecipe.getCategory(),
                extraShapedRecipeData.calio$getKeyMapping(),
                extraShapedRecipeData.calio$getPattern(),
                extraShapedRecipeData.calio$getResult(),
                shapedRecipe.showNotification()
            );

            return DataResult.success(rawShapedRecipe);

        }
    );

    // Use SerializableDataTypes.ATTRIBUTE_MODIFIER instead
    @Deprecated
    public static EntityAttributeModifier readAttributeModifier(JsonElement jsonElement) {
        if(jsonElement.isJsonObject()) {
            JsonObject json = jsonElement.getAsJsonObject();
            String name = JsonHelper.getString(json, "name", "Unnamed attribute modifier");
            String operation = JsonHelper.getString(json, "operation").toUpperCase(Locale.ROOT);
            double value = JsonHelper.getFloat(json, "value");
            return new EntityAttributeModifier(name, value, EntityAttributeModifier.Operation.valueOf(operation));
        }
        throw new JsonSyntaxException("Attribute modifier needs to be a JSON object.");
    }

    // Use SerializableDataTypes.ATTRIBUTE_MODIFIER instead
    @Deprecated
    public static EntityAttributeModifier readAttributeModifier(PacketByteBuf buf) {
        String modName = buf.readString(32767);
        double modValue = buf.readDouble();
        int operation = buf.readInt();
        return new EntityAttributeModifier(modName, modValue, EntityAttributeModifier.Operation.fromId(operation));
    }

    // Use SerializableDataTypes.ATTRIBUTE_MODIFIER instead
    @Deprecated
    public static void writeAttributeModifier(PacketByteBuf buf, EntityAttributeModifier modifier) {
        buf.writeString(modifier.getName());
        buf.writeDouble(modifier.getValue());
        buf.writeInt(modifier.getOperation().getId());
    }

    public static StatusEffectInstance readStatusEffect(JsonElement jsonElement) {
        if(jsonElement.isJsonObject()) {
            JsonObject json = jsonElement.getAsJsonObject();
            String effect = JsonHelper.getString(json, "effect");
            Optional<StatusEffect> effectOptional = Registries.STATUS_EFFECT.getOrEmpty(Identifier.tryParse(effect));
            if(!effectOptional.isPresent()) {
                throw new JsonSyntaxException("Error reading status effect: could not find status effect with id: " + effect);
            }
            int duration = JsonHelper.getInt(json, "duration", 100);
            int amplifier = JsonHelper.getInt(json, "amplifier", 0);
            boolean ambient = JsonHelper.getBoolean(json, "is_ambient", false);
            boolean showParticles = JsonHelper.getBoolean(json, "show_particles", true);
            boolean showIcon = JsonHelper.getBoolean(json, "show_icon", true);
            return new StatusEffectInstance(effectOptional.get(), duration, amplifier, ambient, showParticles, showIcon);
        } else {
            throw new JsonSyntaxException("Expected status effect to be a json object.");
        }
    }

    public static StatusEffectInstance readStatusEffect(PacketByteBuf buf) {
        Identifier effect = buf.readIdentifier();
        int duration = buf.readInt();
        int amplifier = buf.readInt();
        boolean ambient = buf.readBoolean();
        boolean showParticles = buf.readBoolean();
        boolean showIcon = buf.readBoolean();
        return new StatusEffectInstance(Registries.STATUS_EFFECT.get(effect), duration, amplifier, ambient, showParticles, showIcon);
    }

    public static void writeStatusEffect(PacketByteBuf buf, StatusEffectInstance statusEffectInstance) {
        buf.writeIdentifier(Registries.STATUS_EFFECT.getId(statusEffectInstance.getEffectType()));
        buf.writeInt(statusEffectInstance.getDuration());
        buf.writeInt(statusEffectInstance.getAmplifier());
        buf.writeBoolean(statusEffectInstance.isAmbient());
        buf.writeBoolean(statusEffectInstance.shouldShowParticles());
        buf.writeBoolean(statusEffectInstance.shouldShowIcon());
    }

    public static JsonElement writeStatusEffect(StatusEffectInstance statusEffectInstance) {
        JsonObject jo = new JsonObject();
        jo.addProperty("effect", Registries.STATUS_EFFECT.getId(statusEffectInstance.getEffectType()).toString());
        jo.addProperty("duration", statusEffectInstance.getDuration());
        jo.addProperty("amplifier", statusEffectInstance.getAmplifier());
        jo.addProperty("is_ambient", statusEffectInstance.isAmbient());
        jo.addProperty("show_particles", statusEffectInstance.shouldShowParticles());
        jo.addProperty("show_icon", statusEffectInstance.shouldShowIcon());
        return jo;
    }

    public static <T extends Enum<T>> HashMap<String, T> buildEnumMap(Class<T> enumClass, Function<T, String> enumToString) {
        HashMap<String, T> map = new HashMap<>();
        for (T enumConstant : enumClass.getEnumConstants()) {
            map.put(enumToString.apply(enumConstant), enumConstant);
        }
        return map;
    }
}
