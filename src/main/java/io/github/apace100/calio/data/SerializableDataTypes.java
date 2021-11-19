package io.github.apace100.calio.data;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.google.gson.internal.LazilyParsedNumber;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.apace100.calio.Calio;
import io.github.apace100.calio.ClassUtil;
import io.github.apace100.calio.SerializationHelper;
import io.github.apace100.calio.mixin.DamageSourceAccessor;
import io.github.apace100.calio.util.IdentifiedTag;
import io.github.apace100.calio.util.StatusEffectChance;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.util.math.Vector3d;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.sound.SoundEvent;
import net.minecraft.tag.Tag;
import net.minecraft.tag.TagGroup;
import net.minecraft.tag.TagManager;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;

import java.util.*;

public final class SerializableDataTypes {

    public static final SerializableDataType<Integer> INT = new SerializableDataType<>(
        Integer.class,
        PacketByteBuf::writeInt,
        PacketByteBuf::readInt,
        JsonElement::getAsInt);

    public static final SerializableDataType<List<Integer>> INTS = SerializableDataType.list(INT);

    public static final SerializableDataType<Boolean> BOOLEAN = new SerializableDataType<>(
        Boolean.class,
        PacketByteBuf::writeBoolean,
        PacketByteBuf::readBoolean,
        JsonElement::getAsBoolean);

    public static final SerializableDataType<Float> FLOAT = new SerializableDataType<>(
        Float.class,
        PacketByteBuf::writeFloat,
        PacketByteBuf::readFloat,
        JsonElement::getAsFloat);

    public static final SerializableDataType<List<Float>> FLOATS = SerializableDataType.list(FLOAT);

    public static final SerializableDataType<Double> DOUBLE = new SerializableDataType<>(
        Double.class,
        PacketByteBuf::writeDouble,
        PacketByteBuf::readDouble,
        JsonElement::getAsDouble);

    public static final SerializableDataType<List<Double>> DOUBLES = SerializableDataType.list(DOUBLE);

    public static final SerializableDataType<String> STRING = new SerializableDataType<>(
        String.class,
        PacketByteBuf::writeString,
        (buf) -> buf.readString(32767),
        JsonElement::getAsString);

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
        });

    public static final SerializableDataType<List<Number>> NUMBERS = SerializableDataType.list(NUMBER);

    public static final SerializableDataType<Vector3d> VECTOR = new SerializableDataType<>(Vector3d.class,
        (packetByteBuf, vector3d) -> {
            packetByteBuf.writeDouble(vector3d.x);
            packetByteBuf.writeDouble(vector3d.y);
            packetByteBuf.writeDouble(vector3d.z);
        },
        (packetByteBuf -> new Vector3d(
            packetByteBuf.readDouble(),
            packetByteBuf.readDouble(),
            packetByteBuf.readDouble())),
        (jsonElement -> {
            if(jsonElement.isJsonObject()) {
                JsonObject jo = jsonElement.getAsJsonObject();
                return new Vector3d(
                    JsonHelper.getDouble(jo, "x", 0),
                    JsonHelper.getDouble(jo, "y", 0),
                    JsonHelper.getDouble(jo, "z", 0)
                );
            } else {
                throw new JsonParseException("Expected an object with x, y, and z fields.");
            }
        }));

    public static final SerializableDataType<Identifier> IDENTIFIER = new SerializableDataType<>(
        Identifier.class,
        PacketByteBuf::writeIdentifier,
        PacketByteBuf::readIdentifier,
        (json) -> {
            String idString = json.getAsString();
            if(idString.contains(":")) {
                String[] idSplit = idString.split(":");
                if(idSplit.length != 2) {
                    throw new InvalidIdentifierException("Incorrect number of `:` in identifier: \"" + idString + "\".");
                }
                if(idSplit[0].contains("*")) {
                    if(SerializableData.CURRENT_NAMESPACE != null) {
                        idSplit[0] = idSplit[0].replace("*", SerializableData.CURRENT_NAMESPACE);
                    } else {
                        throw new InvalidIdentifierException("Identifier may not contain a `*` in the namespace when read here.");
                    }
                }
                if(idSplit[1].contains("*")) {
                    if(SerializableData.CURRENT_PATH != null) {
                        idSplit[1] = idSplit[1].replace("*", SerializableData.CURRENT_PATH);
                    } else {
                        throw new InvalidIdentifierException("Identifier may only contain a `*` in the path inside of powers.");
                    }
                }
                idString = idSplit[0] + ":" + idSplit[1];
            } else {
                if(idString.contains("*")) {
                    if(SerializableData.CURRENT_PATH != null) {
                        idString = idString.replace("*", SerializableData.CURRENT_PATH);
                    } else {
                        throw new InvalidIdentifierException("Identifier may only contain a `*` in the path inside of powers.");
                    }
                }
            }
            return new Identifier(idString);
        });

    public static final SerializableDataType<List<Identifier>> IDENTIFIERS = SerializableDataType.list(IDENTIFIER);

    public static final SerializableDataType<Enchantment> ENCHANTMENT = SerializableDataType.registry(Enchantment.class, Registry.ENCHANTMENT);

    public static final SerializableDataType<DamageSource> DAMAGE_SOURCE = SerializableDataType.compound(DamageSource.class, new SerializableData()
            .add("name", STRING)
            .add("bypasses_armor", BOOLEAN, false)
            .add("fire", BOOLEAN, false)
            .add("unblockable", BOOLEAN, false)
            .add("magic", BOOLEAN, false)
            .add("out_of_world", BOOLEAN, false)
            .add("projectile", BOOLEAN, false)
            .add("explosive", BOOLEAN, false),
        (data) -> {
            DamageSource damageSource = DamageSourceAccessor.createDamageSource(data.getString("name"));
            if(data.getBoolean("bypasses_armor")) {
                ((DamageSourceAccessor)damageSource).callSetBypassesArmor();
            }
            if(data.getBoolean("fire")) {
                ((DamageSourceAccessor)damageSource).callSetFire();
            }
            if(data.getBoolean("unblockable")) {
                ((DamageSourceAccessor)damageSource).callSetUnblockable();
            }
            if(data.getBoolean("magic")) {
                ((DamageSourceAccessor)damageSource).callSetUsesMagic();
            }
            if(data.getBoolean("out_of_world")) {
                ((DamageSourceAccessor)damageSource).callSetOutOfWorld();
            }
            if(data.getBoolean("projectile")) {
                ((DamageSourceAccessor)damageSource).callSetProjectile();
            }
            if(data.getBoolean("explosive")) {
                ((DamageSourceAccessor)damageSource).callSetExplosive();
            }
            return damageSource;
        },
        (data, ds) -> {
            SerializableData.Instance inst = data.new Instance();
            inst.set("name", ds.name);
            inst.set("fire", ds.isFire());
            inst.set("unblockable", ds.isUnblockable());
            inst.set("bypasses_armor", ds.bypassesArmor());
            inst.set("out_of_world", ds.isOutOfWorld());
            inst.set("magic", ds.isMagic());
            inst.set("projectile", ds.isProjectile());
            inst.set("explosive", ds.isExplosive());
            return inst;
        });

    public static final SerializableDataType<EntityAttribute> ATTRIBUTE = SerializableDataType.registry(EntityAttribute.class, Registry.ATTRIBUTE);

    public static final SerializableDataType<EntityAttributeModifier.Operation> MODIFIER_OPERATION = SerializableDataType.enumValue(EntityAttributeModifier.Operation.class);

    public static final SerializableDataType<EntityAttributeModifier> ATTRIBUTE_MODIFIER = SerializableDataType.compound(EntityAttributeModifier.class, new SerializableData()
            .add("name", STRING, "Unnamed attribute modifier")
            .add("operation", MODIFIER_OPERATION)
            .add("value", DOUBLE),
        data -> new EntityAttributeModifier(
            data.getString("name"),
            data.getDouble("value"),
            (EntityAttributeModifier.Operation)data.get("operation")
        ),
        (serializableData, modifier) -> {
            SerializableData.Instance inst = serializableData.new Instance();
            inst.set("name", modifier.getName());
            inst.set("value", modifier.getValue());
            inst.set("operation", modifier.getOperation());
            return inst;
        });

    public static final SerializableDataType<List<EntityAttributeModifier>> ATTRIBUTE_MODIFIERS =
        SerializableDataType.list(ATTRIBUTE_MODIFIER);

    public static final SerializableDataType<Item> ITEM = SerializableDataType.registry(Item.class, Registry.ITEM);

    public static final SerializableDataType<StatusEffect> STATUS_EFFECT = SerializableDataType.registry(StatusEffect.class, Registry.STATUS_EFFECT);

    public static final SerializableDataType<List<StatusEffect>> STATUS_EFFECTS =
        SerializableDataType.list(STATUS_EFFECT);

    public static final SerializableDataType<StatusEffectInstance> STATUS_EFFECT_INSTANCE = new SerializableDataType<>(
        StatusEffectInstance.class,
        SerializationHelper::writeStatusEffect,
        SerializationHelper::readStatusEffect,
        SerializationHelper::readStatusEffect);

    public static final SerializableDataType<List<StatusEffectInstance>> STATUS_EFFECT_INSTANCES =
        SerializableDataType.list(STATUS_EFFECT_INSTANCE);

    public static final SerializableDataType<Tag<Item>> ITEM_TAG = SerializableDataType.tag(Registry.ITEM_KEY);

    public static final SerializableDataType<Tag<Fluid>> FLUID_TAG = SerializableDataType.tag(Registry.FLUID_KEY);

    public static final SerializableDataType<Tag<Block>> BLOCK_TAG = SerializableDataType.tag(Registry.BLOCK_KEY);

    public static final SerializableDataType<Tag<EntityType<?>>> ENTITY_TAG = SerializableDataType.tag(Registry.ENTITY_TYPE_KEY);

    public static final SerializableDataType<List<Item>> INGREDIENT_ENTRY = SerializableDataType.compound(ClassUtil.castClass(List.class),
        new SerializableData()
            .add("item", ITEM, null)
            .add("tag", ITEM_TAG, null),
        dataInstance -> {
            boolean tagPresent = dataInstance.isPresent("tag");
            boolean itemPresent = dataInstance.isPresent("item");
            if(tagPresent == itemPresent) {
                throw new JsonParseException("An ingredient entry is either a tag or an item, " + (tagPresent ? "not both" : "one has to be provided."));
            }
            if(tagPresent) {
                Tag<Item> tag = (Tag<Item>)dataInstance.get("tag");
                return List.copyOf(tag.values());
            } else {
                return List.of((Item)dataInstance.get("item"));
            }
        }, (data, items) -> {
            SerializableData.Instance inst = data.new Instance();
            if(items.size() == 1) {
                inst.set("item", items.get(0));
            } else {
                TagManager tagManager = Calio.getTagManager();
                TagGroup<Item> tagGroup = tagManager.getOrCreateTagGroup(Registry.ITEM_KEY);
                Collection<Identifier> possibleTags = tagGroup.getTagsFor(items.get(0));
                for(int i = 1; i < items.size() && possibleTags.size() > 1; i++) {
                    possibleTags.removeAll(tagGroup.getTagsFor(items.get(i)));
                }
                if(possibleTags.size() != 1) {
                    throw new IllegalStateException("Couldn't transform item list to a single tag");
                }
                inst.set("tag", tagGroup.getTag(possibleTags.stream().findFirst().get()));
            }
            return inst;
        });

    public static final SerializableDataType<List<List<Item>>> INGREDIENT_ENTRIES = SerializableDataType.list(INGREDIENT_ENTRY);

    // An alternative version of an ingredient deserializer which allows `minecraft:air`
    public static final SerializableDataType<Ingredient> INGREDIENT = new SerializableDataType<>(
        Ingredient.class,
        (buffer, ingredient) -> ingredient.write(buffer),
        Ingredient::fromPacket,
        jsonElement -> {
            List<List<Item>> itemLists = INGREDIENT_ENTRIES.read(jsonElement);
            List<ItemStack> items = new LinkedList<>();
            itemLists.forEach(itemList -> itemList.forEach(item -> items.add(new ItemStack(item))));
            return Ingredient.ofStacks(items.stream());
        });

    // The regular vanilla Minecraft ingredient.
    public static final SerializableDataType<Ingredient> VANILLA_INGREDIENT = new SerializableDataType<>(
        Ingredient.class,
        (buffer, ingredient) -> ingredient.write(buffer),
        Ingredient::fromPacket,
        Ingredient::fromJson);

    public static final SerializableDataType<Block> BLOCK = SerializableDataType.registry(Block.class, Registry.BLOCK);

    public static final SerializableDataType<BlockState> BLOCK_STATE = SerializableDataType.wrap(BlockState.class, STRING,
        BlockArgumentParser::stringifyBlockState,
        string -> {
            try {
                return (new BlockArgumentParser(new StringReader(string), false)).parse(false).getBlockState();
            } catch (CommandSyntaxException e) {
                throw new JsonParseException(e);
            }
        });

    public static final SerializableDataType<EntityGroup> ENTITY_GROUP =
        SerializableDataType.mapped(EntityGroup.class, HashBiMap.create(ImmutableMap.of(
            "default", EntityGroup.DEFAULT,
            "undead", EntityGroup.UNDEAD,
            "arthropod", EntityGroup.ARTHROPOD,
            "illager", EntityGroup.ILLAGER,
            "aquatic", EntityGroup.AQUATIC
        )));

    public static final SerializableDataType<EquipmentSlot> EQUIPMENT_SLOT = SerializableDataType.enumValue(EquipmentSlot.class);

    public static final SerializableDataType<SoundEvent> SOUND_EVENT = SerializableDataType.registry(SoundEvent.class, Registry.SOUND_EVENT);

    public static final SerializableDataType<EntityType<?>> ENTITY_TYPE = SerializableDataType.registry(ClassUtil.castClass(EntityType.class), Registry.ENTITY_TYPE);

    public static final SerializableDataType<ParticleType<?>> PARTICLE_TYPE = SerializableDataType.registry(ClassUtil.castClass(ParticleType.class), Registry.PARTICLE_TYPE);

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
        });

    public static final SerializableDataType<NbtCompound> NBT = SerializableDataType.wrap(NbtCompound.class, SerializableDataTypes.STRING,
        NbtCompound::toString,
        (str) -> {
            try {
                return new StringNbtReader(new StringReader(str)).parseCompound();
            } catch (CommandSyntaxException e) {
                throw new JsonSyntaxException("Could not parse NBT tag, exception: " + e.getMessage());
            }
        });

    public static final SerializableDataType<ItemStack> ITEM_STACK = SerializableDataType.compound(ItemStack.class,
        new SerializableData()
            .add("item", SerializableDataTypes.ITEM)
            .add("amount", SerializableDataTypes.INT, 1)
            .add("tag", NBT, null),
        (data) ->  {
            ItemStack stack = new ItemStack((Item)data.get("item"), data.getInt("amount"));
            if(data.isPresent("tag")) {
                stack.setTag((NbtCompound)data.get("tag"));
            }
            return stack;
        },
        ((serializableData, itemStack) -> {
            SerializableData.Instance data = serializableData.new Instance();
            data.set("item", itemStack.getItem());
            data.set("amount", itemStack.getCount());
            data.set("tag", itemStack.hasTag() ? itemStack.getTag() : null);
            return data;
        }));

    public static final SerializableDataType<List<ItemStack>> ITEM_STACKS = SerializableDataType.list(ITEM_STACK);

    public static final SerializableDataType<Text> TEXT = new SerializableDataType<>(Text.class,
        (buffer, text) -> buffer.writeString(Text.Serializer.toJson(text)),
        (buffer) -> Text.Serializer.fromJson(buffer.readString(32767)),
        Text.Serializer::fromJson);

    public static final SerializableDataType<List<Text>> TEXTS = SerializableDataType.list(TEXT);

    public static SerializableDataType<RegistryKey<World>> DIMENSION = SerializableDataType.wrap(
        ClassUtil.castClass(RegistryKey.class),
        SerializableDataTypes.IDENTIFIER,
        RegistryKey::getValue, identifier -> RegistryKey.of(Registry.WORLD_KEY, identifier)
    );

    public static final SerializableDataType<Recipe> RECIPE = new SerializableDataType<>(Recipe.class,
        (buffer, recipe) -> {
            buffer.writeIdentifier(Registry.RECIPE_SERIALIZER.getId(recipe.getSerializer()));
            buffer.writeIdentifier(recipe.getId());
            recipe.getSerializer().write(buffer, recipe);
        },
        (buffer) -> {
            Identifier recipeSerializerId = buffer.readIdentifier();
            Identifier recipeId = buffer.readIdentifier();
            RecipeSerializer serializer = Registry.RECIPE_SERIALIZER.get(recipeSerializerId);
            return serializer.read(recipeId, buffer);
        },
        (jsonElement) -> {
            if(!jsonElement.isJsonObject()) {
                throw new RuntimeException("Expected recipe to be a JSON object.");
            }
            JsonObject json = jsonElement.getAsJsonObject();
            Identifier recipeSerializerId = Identifier.tryParse(JsonHelper.getString(json, "type"));
            Identifier recipeId = Identifier.tryParse(JsonHelper.getString(json, "id"));
            RecipeSerializer serializer = Registry.RECIPE_SERIALIZER.get(recipeSerializerId);
            return serializer.read(recipeId, json);
        });

    public static final SerializableDataType<GameEvent> GAME_EVENT = SerializableDataType.registry(GameEvent.class, Registry.GAME_EVENT);

    public static final SerializableDataType<List<GameEvent>> GAME_EVENTS =
        SerializableDataType.list(GAME_EVENT);

    public static final SerializableDataType<Tag<GameEvent>> GAME_EVENT_TAG = SerializableDataType.wrap(ClassUtil.castClass(Tag.class), SerializableDataTypes.IDENTIFIER,
        tag -> Calio.getTagManager().getTagId(Registry.GAME_EVENT_KEY, tag, RuntimeException::new),
        id -> new IdentifiedTag<>(Registry.GAME_EVENT_KEY, id));

    public static final SerializableDataType<Fluid> FLUID = SerializableDataType.registry(Fluid.class, Registry.FLUID);

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
                sec.statusEffectInstance = (StatusEffectInstance) data.get("effect");
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

}
