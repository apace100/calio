package io.github.apace100.calio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.github.apace100.calio.mixin.EntityAttributeModifierAccessor;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public class SerializationHelper {

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
        buf.writeString(((EntityAttributeModifierAccessor) modifier).getName());
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
