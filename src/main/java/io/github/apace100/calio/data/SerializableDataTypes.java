package io.github.apace100.calio.data;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.apace100.calio.Calio;
import io.github.apace100.calio.ClassUtil;
import io.github.apace100.calio.SerializationHelper;
import io.github.apace100.calio.mixin.DamageSourceAccessor;
import io.github.apace100.calio.util.IdentifiedTag;
import net.minecraft.block.Block;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.sound.SoundEvent;
import net.minecraft.tag.Tag;
import net.minecraft.tag.TagGroup;
import net.minecraft.tag.TagManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public final class SerializableDataTypes {

    public static final SerializableDataType<Integer> INT = new SerializableDataType<>(
        Integer.class,
        PacketByteBuf::writeInt,
        PacketByteBuf::readInt,
        JsonElement::getAsInt);

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

    public static final SerializableDataType<Double> DOUBLE = new SerializableDataType<>(
        Double.class,
        PacketByteBuf::writeDouble,
        PacketByteBuf::readDouble,
        JsonElement::getAsDouble);

    public static final SerializableDataType<String> STRING = new SerializableDataType<>(
        String.class,
        PacketByteBuf::writeString,
        (buf) -> buf.readString(32767),
        JsonElement::getAsString);

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

    public static final SerializableDataType<Tag<Item>> ITEM_TAG = SerializableDataType.wrap(ClassUtil.castClass(Tag.class), IDENTIFIER,
        item -> Calio.getTagManager().getTagId(Registry.ITEM_KEY, item, () -> new JsonSyntaxException("Unknown fluid tag")),
        id -> new IdentifiedTag<>(Registry.ITEM_KEY, id));

    public static final SerializableDataType<Tag<Fluid>> FLUID_TAG = SerializableDataType.wrap(ClassUtil.castClass(Tag.class), IDENTIFIER,
        fluid -> Calio.getTagManager().getTagId(Registry.FLUID_KEY, fluid, () -> new JsonSyntaxException("Unknown fluid tag")),
        id -> new IdentifiedTag<>(Registry.FLUID_KEY, id));

    public static final SerializableDataType<Tag<Block>> BLOCK_TAG = SerializableDataType.wrap(ClassUtil.castClass(Tag.class), IDENTIFIER,
        block -> Calio.getTagManager().getTagId(Registry.BLOCK_KEY, block, () -> new JsonSyntaxException("Unknown block tag")),
        id -> new IdentifiedTag<>(Registry.BLOCK_KEY, id));

    public static final SerializableDataType<Tag<EntityType<?>>> ENTITY_TAG = SerializableDataType.wrap(ClassUtil.castClass(Tag.class), SerializableDataTypes.IDENTIFIER,
        tag -> Calio.getTagManager().getTagId(Registry.ENTITY_TYPE_KEY, tag, RuntimeException::new),
        id -> new IdentifiedTag<>(Registry.ENTITY_TYPE_KEY, id));

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
}
