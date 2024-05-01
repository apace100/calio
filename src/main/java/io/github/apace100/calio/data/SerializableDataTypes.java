package io.github.apace100.calio.data;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.google.gson.internal.LazilyParsedNumber;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import io.github.apace100.calio.Calio;
import io.github.apace100.calio.ClassUtil;
import io.github.apace100.calio.SerializationHelper;
import io.github.apace100.calio.mixin.EntityAttributeModifierAccessor;
import io.github.apace100.calio.mixin.IngredientAccessor;
import io.github.apace100.calio.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.*;
import net.minecraft.sound.SoundEvent;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatType;
import net.minecraft.registry.tag.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;

import java.util.*;

@SuppressWarnings("unused")
public final class SerializableDataTypes {

    public static final SerializableDataType<Integer> INT = new SerializableDataType<>(
        Integer.class,
        PacketByteBuf::writeInt,
        PacketByteBuf::readInt,
        JsonElement::getAsInt,
        JsonPrimitive::new);

    public static final SerializableDataType<List<Integer>> INTS = SerializableDataType.list(INT);

    public static final SerializableDataType<Integer> POSITIVE_INT = SerializableDataType.boundNumber(
        INT, 1, Integer.MAX_VALUE,
        value -> (min, max) -> {

            if (value < min || value > max) {
                throw new IllegalArgumentException("Expected integer to be greater than 0! (current value: " + value + ")");
            }

            return value;

        }
    );

    public static final SerializableDataType<List<Integer>> POSITIVE_INTS = SerializableDataType.list(POSITIVE_INT);

    public static final SerializableDataType<Boolean> BOOLEAN = new SerializableDataType<>(
        Boolean.class,
        PacketByteBuf::writeBoolean,
        PacketByteBuf::readBoolean,
        JsonElement::getAsBoolean,
        JsonPrimitive::new);

    public static final SerializableDataType<Float> FLOAT = new SerializableDataType<>(
        Float.class,
        PacketByteBuf::writeFloat,
        PacketByteBuf::readFloat,
        JsonElement::getAsFloat,
        JsonPrimitive::new);

    public static final SerializableDataType<List<Float>> FLOATS = SerializableDataType.list(FLOAT);

    public static final SerializableDataType<Float> POSITIVE_FLOAT = SerializableDataType.boundNumber(
        FLOAT, 1F, Float.MAX_VALUE,
        value -> (min, max) -> {

            if (value < min || value > max) {
                throw new IllegalArgumentException("Expected float to be greater than 0! (current value: " + value + ")");
            }

            return value;

        }
    );

    public static final SerializableDataType<List<Float>> POSITIVE_FLOATS = SerializableDataType.list(POSITIVE_FLOAT);

    public static final SerializableDataType<Double> DOUBLE = new SerializableDataType<>(
        Double.class,
        PacketByteBuf::writeDouble,
        PacketByteBuf::readDouble,
        JsonElement::getAsDouble,
        JsonPrimitive::new);

    public static final SerializableDataType<List<Double>> DOUBLES = SerializableDataType.list(DOUBLE);

    public static final SerializableDataType<Double> POSITIVE_DOUBLE = SerializableDataType.boundNumber(
        DOUBLE, 1D, Double.MAX_VALUE,
        value -> (min, max) -> {

            if (value < min || value > max) {
                throw new IllegalArgumentException("Expected double to be greater than 0! (current value: " + value + ")");
            }

            return value;

        }
    );

    public static final SerializableDataType<String> STRING = new SerializableDataType<>(
        String.class,
        PacketByteBuf::writeString,
        (buf) -> buf.readString(32767),
        JsonElement::getAsString,
        JsonPrimitive::new);

    public static final SerializableDataType<List<String>> STRINGS = SerializableDataType.list(STRING);

    public static final SerializableDataType<Number> NUMBER = new SerializableDataType<>(
        Number.class,
        (buf, number) -> {
            if(number instanceof Double) {
                buf.writeByte(0);
                buf.writeDouble(number.doubleValue());
            } else if(number instanceof Float) {
                buf.writeByte(1);
                buf.writeFloat(number.floatValue());
            } else if(number instanceof Integer) {
                buf.writeByte(2);
                buf.writeInt(number.intValue());
            } else if(number instanceof Long) {
                buf.writeByte(3);
                buf.writeLong(number.longValue());
            } else {
                buf.writeByte(4);
                buf.writeString(number.toString());
            }
        },
        buf -> {
            byte type = buf.readByte();
            switch(type) {
                case 0:
                    return buf.readDouble();
                case 1:
                    return buf.readFloat();
                case 2:
                    return buf.readInt();
                case 3:
                    return buf.readLong();
                case 4:
                    return new LazilyParsedNumber(buf.readString());
            }
            throw new RuntimeException("Could not receive number, unexpected type id \"" + type + "\" (allowed range: [0-4])");
        },
        je -> {
            if(je.isJsonPrimitive()) {
                JsonPrimitive primitive = je.getAsJsonPrimitive();
                if(primitive.isNumber()) {
                    return primitive.getAsNumber();
                } else if(primitive.isBoolean()) {
                    return primitive.getAsBoolean() ? 1 : 0;
                }
            }
            throw new JsonParseException("Expected a primitive");
        },
        number -> {
            if(number instanceof Double) {
                return new JsonPrimitive(number.doubleValue());
            } else if(number instanceof Float) {
                return new JsonPrimitive(number.floatValue());
            } else if(number instanceof Integer) {
                return new JsonPrimitive(number.intValue());
            } else if(number instanceof Long) {
                return new JsonPrimitive(number.longValue());
            } else {
                return new JsonPrimitive(number.toString());
            }
        });

    public static final SerializableDataType<List<Number>> NUMBERS = SerializableDataType.list(NUMBER);

    public static final SerializableDataType<Vec3d> VECTOR = new SerializableDataType<>(Vec3d.class,
        (packetByteBuf, vector3d) -> {
            packetByteBuf.writeDouble(vector3d.x);
            packetByteBuf.writeDouble(vector3d.y);
            packetByteBuf.writeDouble(vector3d.z);
        },
        (packetByteBuf -> new Vec3d(
            packetByteBuf.readDouble(),
            packetByteBuf.readDouble(),
            packetByteBuf.readDouble())),
        (jsonElement -> {
            if(jsonElement.isJsonObject()) {
                JsonObject jo = jsonElement.getAsJsonObject();
                return new Vec3d(
                    JsonHelper.getDouble(jo, "x", 0),
                    JsonHelper.getDouble(jo, "y", 0),
                    JsonHelper.getDouble(jo, "z", 0)
                );
            } else {
                throw new JsonParseException("Expected an object with x, y, and z fields.");
            }
        }),
        (vec3d) -> {
            JsonObject jo = new JsonObject();
            jo.addProperty("x", vec3d.x);
            jo.addProperty("y", vec3d.y);
            jo.addProperty("z", vec3d.z);
            return jo;
        });

    public static final SerializableDataType<Identifier> IDENTIFIER = new SerializableDataType<>(
        Identifier.class,
        PacketByteBuf::writeIdentifier,
        PacketByteBuf::readIdentifier,
        DynamicIdentifier::of,
        identifier -> new JsonPrimitive(identifier.toString())
    );

    public static final SerializableDataType<List<Identifier>> IDENTIFIERS = SerializableDataType.list(IDENTIFIER);

    public static final SerializableDataType<Enchantment> ENCHANTMENT = SerializableDataType.registry(Enchantment.class, Registries.ENCHANTMENT);

    private static final Set<RegistryKey<World>> VANILLA_DIMENSIONS = Set.of(
        World.OVERWORLD,
        World.NETHER,
        World.END
    );

    public static SerializableDataType<RegistryKey<World>> DIMENSION = SerializableDataType.registryKey(RegistryKeys.WORLD, VANILLA_DIMENSIONS);

    public static final SerializableDataType<EntityAttribute> ATTRIBUTE = SerializableDataType.registry(EntityAttribute.class, Registries.ATTRIBUTE);

    public static final SerializableDataType<EntityAttributeModifier.Operation> MODIFIER_OPERATION = SerializableDataType.enumValue(EntityAttributeModifier.Operation.class);

    public static final SerializableDataType<EntityAttributeModifier> ATTRIBUTE_MODIFIER = SerializableDataType.compound(EntityAttributeModifier.class, new SerializableData()
            .add("name", STRING, "Unnamed attribute modifier")
            .add("operation", MODIFIER_OPERATION)
            .add("value", DOUBLE),
        data -> new EntityAttributeModifier(
            data.getString("name"),
            data.getDouble("value"),
            data.get("operation")
        ),
        (serializableData, modifier) -> {
            SerializableData.Instance inst = serializableData.new Instance();
            inst.set("name", ((EntityAttributeModifierAccessor) modifier).getName());
            inst.set("value", modifier.getValue());
            inst.set("operation", modifier.getOperation());
            return inst;
        });

    public static final SerializableDataType<List<EntityAttributeModifier>> ATTRIBUTE_MODIFIERS =
        SerializableDataType.list(ATTRIBUTE_MODIFIER);

    public static final SerializableDataType<Item> ITEM = SerializableDataType.registry(Item.class, Registries.ITEM);

    public static final SerializableDataType<StatusEffect> STATUS_EFFECT = SerializableDataType.registry(StatusEffect.class, Registries.STATUS_EFFECT);

    public static final SerializableDataType<List<StatusEffect>> STATUS_EFFECTS =
        SerializableDataType.list(STATUS_EFFECT);

    public static final SerializableDataType<StatusEffectInstance> STATUS_EFFECT_INSTANCE = new SerializableDataType<>(
        StatusEffectInstance.class,
        SerializationHelper::writeStatusEffect,
        SerializationHelper::readStatusEffect,
        SerializationHelper::readStatusEffect,
        SerializationHelper::writeStatusEffect);

    public static final SerializableDataType<List<StatusEffectInstance>> STATUS_EFFECT_INSTANCES =
        SerializableDataType.list(STATUS_EFFECT_INSTANCE);

    public static final SerializableDataType<TagKey<Item>> ITEM_TAG = SerializableDataType.tag(RegistryKeys.ITEM);

    public static final SerializableDataType<TagKey<Fluid>> FLUID_TAG = SerializableDataType.tag(RegistryKeys.FLUID);

    public static final SerializableDataType<TagKey<Block>> BLOCK_TAG = SerializableDataType.tag(RegistryKeys.BLOCK);

    public static final SerializableDataType<TagKey<EntityType<?>>> ENTITY_TAG = SerializableDataType.tag(RegistryKeys.ENTITY_TYPE);

    public static final SerializableDataType<Ingredient.Entry> INGREDIENT_ENTRY = SerializableDataType.compound(
        ClassUtil.castClass(Ingredient.Entry.class),
        new SerializableData()
            .add("tag", ITEM_TAG, null)
            .add("item", ITEM, null),
        data -> {

            boolean isTagPresent = data.isPresent("tag");
            boolean isItemPresent = data.isPresent("item");

            if (isTagPresent == isItemPresent) {
                throw new JsonParseException("An ingredient entry is either a tag or an item, " + (isTagPresent ? "not both." : "one has to be provided."));
            }

            if (isTagPresent) {
                TagKey<Item> tag = data.get("tag");
                return new Ingredient.TagEntry(tag);
            } else {

                Item item = data.get("item");
                ItemStack stack = new ItemStack(item);

                return new Ingredient.StackEntry(stack);

            }

        },
        (serializableData, entry) -> {

            SerializableData.Instance data = serializableData.new Instance();

            if (entry instanceof Ingredient.TagEntry tagEntry) {
                data.set("tag", tagEntry.tag());
                data.set("item", null);
            } else if (entry instanceof Ingredient.StackEntry stackEntry) {
                data.set("tag", null);
                data.set("item", stackEntry.stack().getItem());
            } else {
                throw new RuntimeException("Tried to write an ingredient that was not a tag or an item!");
            }

            return data;

        }
    );

    public static final SerializableDataType<List<Ingredient.Entry>> INGREDIENT_ENTRIES = SerializableDataType.list(INGREDIENT_ENTRY);

    // An alternative version of an ingredient deserializer which allows `minecraft:air`
    public static final SerializableDataType<Ingredient> INGREDIENT = new SerializableDataType<>(
        Ingredient.class,
        (buffer, ingredient) -> ingredient.write(buffer),
        Ingredient::fromPacket,
        jsonElement -> {
            List<Ingredient.Entry> entries = INGREDIENT_ENTRIES.read(jsonElement);
            return Ingredient.ofEntries(entries.stream());
        },
        ingredient -> {
            List<Ingredient.Entry> entries = Arrays.asList(((IngredientAccessor) ingredient).getEntries());
            return INGREDIENT_ENTRIES.write(entries);
        });

    // The regular vanilla Minecraft ingredient.
    public static final SerializableDataType<Ingredient> VANILLA_INGREDIENT = new SerializableDataType<>(
        Ingredient.class,
        (buf, ingredient) ->
            ingredient.write(buf),
        Ingredient::fromPacket,
        jsonElement -> Ingredient.DISALLOW_EMPTY_CODEC
            .decode(JsonOps.INSTANCE, jsonElement)
            .mapError(err -> "Couldn't deserialize ingredient from JSON: " + err)
            .getOrThrow(false, err -> {})
            .getFirst(),
        ingredient -> Ingredient.DISALLOW_EMPTY_CODEC
            .encodeStart(JsonOps.INSTANCE, ingredient)
            .mapError(err -> "Couldn't serialize ingredient to JSON: " + err)
            .getOrThrow(false, err -> {})
    );

    public static final SerializableDataType<Block> BLOCK = SerializableDataType.registry(Block.class, Registries.BLOCK);

    public static final SerializableDataType<BlockState> BLOCK_STATE = SerializableDataType.wrap(BlockState.class, STRING,
        BlockArgumentParser::stringifyBlockState,
        string -> {
            try {
                return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), string, false).blockState();
            } catch (CommandSyntaxException e) {
                throw new JsonParseException(e);
            }
        });

    public static final SerializableDataType<RegistryKey<DamageType>> DAMAGE_TYPE = SerializableDataType.registryKey(RegistryKeys.DAMAGE_TYPE);

    public static final SerializableDataType<EntityGroup> ENTITY_GROUP =
        SerializableDataType.mapped(EntityGroup.class, HashBiMap.create(ImmutableMap.of(
            "default", EntityGroup.DEFAULT,
            "undead", EntityGroup.UNDEAD,
            "arthropod", EntityGroup.ARTHROPOD,
            "illager", EntityGroup.ILLAGER,
            "aquatic", EntityGroup.AQUATIC
        )));

    public static final SerializableDataType<EquipmentSlot> EQUIPMENT_SLOT = SerializableDataType.enumValue(EquipmentSlot.class);

    public static final SerializableDataType<SoundEvent> SOUND_EVENT = SerializableDataType.wrap(
        SoundEvent.class,
        IDENTIFIER,
        SoundEvent::getId,
        SoundEvent::of
    );

    public static final SerializableDataType<EntityType<?>> ENTITY_TYPE = SerializableDataType.registry(ClassUtil.castClass(EntityType.class), Registries.ENTITY_TYPE);

    public static final SerializableDataType<ParticleType<?>> PARTICLE_TYPE = SerializableDataType.registry(ClassUtil.castClass(ParticleType.class), Registries.PARTICLE_TYPE);

    public static final SerializableDataType<ParticleEffect> PARTICLE_EFFECT = SerializableDataType.compound(ParticleEffect.class,
        new SerializableData()
            .add("type", PARTICLE_TYPE)
            .add("params", STRING, ""),
        dataInstance -> {
            ParticleType<? extends ParticleEffect> particleType = dataInstance.get("type");
            ParticleEffect.Factory factory = particleType.getParametersFactory();
            ParticleEffect effect = null;
            try {
                effect = factory.read(particleType, new StringReader(" " + dataInstance.getString("params")));
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
            return effect;
        },
        ((serializableData, particleEffect) -> {
            SerializableData.Instance data = serializableData.new Instance();
            data.set("type", particleEffect.getType());
            String params = particleEffect.asString();
            int spaceIndex = params.indexOf(' ');
            if(spaceIndex > -1) {
                params = params.substring(spaceIndex + 1);
            } else {
                params = "";
            }
            data.set("params", params);
            return data;
        }));

    public static final SerializableDataType<ParticleEffect> PARTICLE_EFFECT_OR_TYPE = new SerializableDataType<>(ParticleEffect.class,
        PARTICLE_EFFECT::send,
        PARTICLE_EFFECT::receive,
        jsonElement -> {
            if(jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString()) {
                ParticleType<?> type = PARTICLE_TYPE.read(jsonElement);
                if(type instanceof ParticleEffect) {
                    return (ParticleEffect) type;
                }
                throw new RuntimeException("Expected either a string with a parameter-less particle effect, or an object.");
            } else if(jsonElement.isJsonObject()) {
                return PARTICLE_EFFECT.read(jsonElement);
            }
            throw new RuntimeException("Expected either a string with a parameter-less particle effect, or an object.");
        },
        PARTICLE_EFFECT::write);

    public static final SerializableDataType<NbtCompound> NBT = new SerializableDataType<>(
        NbtCompound.class,
        PacketByteBuf::writeNbt,
        PacketByteBuf::readNbt,
        jsonElement -> {

            if (!(jsonElement.isJsonObject()|| jsonElement.isJsonPrimitive() && jsonElement.getAsJsonPrimitive().isString()))
                throw new JsonSyntaxException("Expected either a string or an object.");

            try {
                String stringifiedJsonElement = jsonElement.isJsonObject() ? jsonElement.getAsJsonObject().toString() : jsonElement.getAsJsonPrimitive().getAsString();
                return new StringNbtReader(new StringReader(stringifiedJsonElement)).parseCompound();
            }
            catch (CommandSyntaxException e) {
                throw new JsonSyntaxException("Could not parse NBT: " + e.getMessage());
            }

        },
        nbtCompound -> NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, nbtCompound)
    );

    public static final SerializableDataType<ItemStack> ITEM_STACK = SerializableDataType.compound(
        ItemStack.class,
        new SerializableData()
            .add("item", SerializableDataTypes.ITEM)
            .add("amount", SerializableDataTypes.INT, 1)
            .add("tag", SerializableDataTypes.NBT, null),
        data -> {

            ItemStack stack = data.<Item>get("item").getDefaultStack();

            stack.setCount(data.get("amount"));
            data.ifPresent("tag", stack::setNbt);

            return stack;

        },
        (serializableData, stack) -> {

            SerializableData.Instance data = serializableData.new Instance();

            data.set("item", stack.getItem());
            data.set("amount", stack.getCount());
            data.set("tag", stack.getNbt());

            return data;

        }
    );

    public static final SerializableDataType<List<ItemStack>> ITEM_STACKS = SerializableDataType.list(ITEM_STACK);

    public static final SerializableDataType<Text> TEXT = new SerializableDataType<>(
        Text.class,
        PacketByteBuf::writeText,
        PacketByteBuf::readUnlimitedText,
        Text.Serialization::fromJsonTree,
        Text.Serialization::toJsonTree
    );

    public static final SerializableDataType<List<Text>> TEXTS = SerializableDataType.list(TEXT);

    public static final SerializableDataType<RecipeEntry> RECIPE = new SerializableDataType<>(RecipeEntry.class,
        (buffer, recipe) -> {
            buffer.writeIdentifier(Registries.RECIPE_SERIALIZER.getId(recipe.value().getSerializer()));
            buffer.writeIdentifier(recipe.id());
            recipe.value().getSerializer().write(buffer, recipe.value());
        },
        (buffer) -> {
            Identifier recipeSerializerId = buffer.readIdentifier();
            Identifier recipeId = buffer.readIdentifier();
            RecipeSerializer<?> serializer = Registries.RECIPE_SERIALIZER.get(recipeSerializerId);
            return new RecipeEntry<>(recipeId, serializer.read(buffer));
        },
        (jsonElement) -> {
            if(!jsonElement.isJsonObject()) {
                throw new RuntimeException("Expected recipe to be a JSON object.");
            }
            JsonObject json = jsonElement.getAsJsonObject();
            Identifier recipeSerializerId = Identifier.tryParse(JsonHelper.getString(json, "type"));
            Identifier recipeId = Identifier.tryParse(JsonHelper.getString(json, "id"));
            RecipeSerializer<?> serializer = Registries.RECIPE_SERIALIZER.get(recipeSerializerId);
            return new RecipeEntry<>(recipeId, serializer.codec().parse(JsonOps.INSTANCE, json).resultOrPartial(Calio.LOGGER::error).orElseThrow(() -> new RuntimeException("Failed to read recipe json.")));
        },
        recipe -> {
            JsonObject json = new JsonObject();
            json.addProperty("type", Registries.RECIPE_SERIALIZER.getId(recipe.value().getSerializer()).toString());
            json.addProperty("id", recipe.id().toString());
            recipe.value().getSerializer().codec().encodeStart(JsonOps.INSTANCE, recipe.value()).resultOrPartial(Calio.LOGGER::error).ifPresent(o -> {
                for (Map.Entry<String, JsonElement> j : ((JsonObject) o).entrySet()) {
                    json.add(j.getKey(), j.getValue());
                }
            });
            return json;
        });

    public static final SerializableDataType<GameEvent> GAME_EVENT = SerializableDataType.registry(GameEvent.class, Registries.GAME_EVENT);

    public static final SerializableDataType<List<GameEvent>> GAME_EVENTS =
        SerializableDataType.list(GAME_EVENT);

    public static final SerializableDataType<TagKey<GameEvent>> GAME_EVENT_TAG = SerializableDataType.tag(RegistryKeys.GAME_EVENT);

    public static final SerializableDataType<Fluid> FLUID = SerializableDataType.registry(Fluid.class, Registries.FLUID);

    public static final SerializableDataType<CameraSubmersionType> CAMERA_SUBMERSION_TYPE = SerializableDataType.enumValue(CameraSubmersionType.class);

    public static final SerializableDataType<Hand> HAND = SerializableDataType.enumValue(Hand.class);

    public static final SerializableDataType<EnumSet<Hand>> HAND_SET = SerializableDataType.enumSet(Hand.class, HAND);

    public static final SerializableDataType<EnumSet<EquipmentSlot>> EQUIPMENT_SLOT_SET = SerializableDataType.enumSet(EquipmentSlot.class, EQUIPMENT_SLOT);

    public static final SerializableDataType<ActionResult> ACTION_RESULT = SerializableDataType.enumValue(ActionResult.class);

    public static final SerializableDataType<UseAction> USE_ACTION = SerializableDataType.enumValue(UseAction.class);

    public static final SerializableDataType<StatusEffectChance> STATUS_EFFECT_CHANCE =
        SerializableDataType.compound(StatusEffectChance.class, new SerializableData()
            .add("effect", STATUS_EFFECT_INSTANCE)
            .add("chance", FLOAT, 1.0F),
            (data) -> {
                StatusEffectChance sec = new StatusEffectChance();
                sec.statusEffectInstance = data.get("effect");
                sec.chance = data.getFloat("chance");
                return sec;
            },
            (data, csei) -> {
                SerializableData.Instance inst = data.new Instance();
                inst.set("effect", csei.statusEffectInstance);
                inst.set("chance", csei.chance);
                return inst;
            });

    public static final SerializableDataType<List<StatusEffectChance>> STATUS_EFFECT_CHANCES = SerializableDataType.list(STATUS_EFFECT_CHANCE);

    public static final SerializableDataType<FoodComponent> FOOD_COMPONENT = SerializableDataType.compound(FoodComponent.class, new SerializableData()
            .add("hunger", INT)
            .add("saturation", FLOAT)
            .add("meat", BOOLEAN, false)
            .add("always_edible", BOOLEAN, false)
            .add("snack", BOOLEAN, false)
            .add("effect", STATUS_EFFECT_CHANCE, null)
            .add("effects", STATUS_EFFECT_CHANCES, null),
        (data) -> {
            FoodComponent.Builder builder = new FoodComponent.Builder().hunger(data.getInt("hunger")).saturationModifier(data.getFloat("saturation"));
            if (data.getBoolean("meat")) {
                builder.meat();
            }
            if (data.getBoolean("always_edible")) {
                builder.alwaysEdible();
            }
            if (data.getBoolean("snack")) {
                builder.snack();
            }
            data.<StatusEffectChance>ifPresent("effect", sec -> {
                builder.statusEffect(sec.statusEffectInstance, sec.chance);
            });
            data.<List<StatusEffectChance>>ifPresent("effects", secs -> secs.forEach(sec -> {
                builder.statusEffect(sec.statusEffectInstance, sec.chance);
            }));
            return builder.build();
        },
        (data, fc) -> {
            SerializableData.Instance inst = data.new Instance();
            inst.set("hunger", fc.getHunger());
            inst.set("saturation", fc.getSaturationModifier());
            inst.set("meat", fc.isMeat());
            inst.set("always_edible", fc.isAlwaysEdible());
            inst.set("snack", fc.isSnack());
            inst.set("effect", null);
            List<StatusEffectChance> statusEffectChances = new LinkedList<>();
            fc.getStatusEffects().forEach(pair -> {
                StatusEffectChance sec = new StatusEffectChance();
                sec.statusEffectInstance = pair.getFirst();
                sec.chance = pair.getSecond();
                statusEffectChances.add(sec);
            });
            if(statusEffectChances.size() > 0) {
                inst.set("effects", statusEffectChances);
            } else {
                inst.set("effects", null);
            }
            return inst;
        });

    public static final SerializableDataType<Direction> DIRECTION = SerializableDataType.enumValue(Direction.class);

    public static final SerializableDataType<EnumSet<Direction>> DIRECTION_SET = SerializableDataType.enumSet(Direction.class, DIRECTION);

    public static final SerializableDataType<Class<?>> CLASS = SerializableDataType.wrap(ClassUtil.castClass(Class.class), SerializableDataTypes.STRING,
        Class::getName,
        str -> {
            try {
                return Class.forName(str);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Specified class does not exist: \"" + str + "\".");
            }
        });

    public static final SerializableDataType<RaycastContext.ShapeType> SHAPE_TYPE = SerializableDataType.enumValue(RaycastContext.ShapeType.class);

    public static final SerializableDataType<RaycastContext.FluidHandling> FLUID_HANDLING = SerializableDataType.enumValue(RaycastContext.FluidHandling.class);

    public static final SerializableDataType<Explosion.DestructionType> DESTRUCTION_TYPE = SerializableDataType.enumValue(Explosion.DestructionType.class);

    public static final SerializableDataType<Direction.Axis> AXIS = SerializableDataType.enumValue(Direction.Axis.class);

    public static final SerializableDataType<EnumSet<Direction.Axis>> AXIS_SET = SerializableDataType.enumSet(Direction.Axis.class, AXIS);

    public static final SerializableDataType<ArgumentWrapper<NbtPathArgumentType.NbtPath>> NBT_PATH =
        SerializableDataType.argumentType(NbtPathArgumentType.nbtPath());

    public static final SerializableDataType<RaycastContext.ShapeType> RAYCAST_SHAPE_TYPE = SerializableDataType.enumValue(RaycastContext.ShapeType.class);

    public static final SerializableDataType<RaycastContext.FluidHandling> RAYCAST_FLUID_HANDLING = SerializableDataType.enumValue(RaycastContext.FluidHandling.class);

    public static final SerializableDataType<Stat<?>> STAT = SerializableDataType.compound(ClassUtil.castClass(Stat.class),
        new SerializableData()
            .add("type", SerializableDataType.registry(ClassUtil.castClass(StatType.class), Registries.STAT_TYPE))
            .add("id", SerializableDataTypes.IDENTIFIER),
        data -> {
            StatType statType = data.get("type");
            Registry<?> statRegistry = statType.getRegistry();
            Identifier statId = data.get("id");
            if(statRegistry.containsId(statId)) {
                Object statObject = statRegistry.get(statId);
                return statType.getOrCreateStat(statObject);
            }
            throw new IllegalArgumentException("Desired stat \"" + statId + "\" does not exist in stat type ");
        },
        (data, stat) -> {
            SerializableData.Instance inst = data.new Instance();
            inst.set("type", stat.getType());
            Registry reg = stat.getType().getRegistry();
            Identifier statId = reg.getId(stat.getValue());
            inst.set("id", statId);
            return inst;
        });

    public static final SerializableDataType<TagKey<Biome>> BIOME_TAG = SerializableDataType.tag(RegistryKeys.BIOME);

    public static final SerializableDataType<TagLike<Item>> ITEM_TAG_LIKE = SerializableDataType.tagLike(Registries.ITEM);

    public static final SerializableDataType<TagLike<Block>> BLOCK_TAG_LIKE = SerializableDataType.tagLike(Registries.BLOCK);

    public static final SerializableDataType<TagLike<EntityType<?>>> ENTITY_TYPE_TAG_LIKE = SerializableDataType.tagLike(Registries.ENTITY_TYPE);
}
