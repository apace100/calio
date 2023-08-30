package io.github.apace100.calio.data;

import com.google.common.collect.BiMap;
import com.google.gson.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.apace100.calio.ClassUtil;
import io.github.apace100.calio.FilterableWeightedList;
import io.github.apace100.calio.mixin.WeightedListEntryAccessor;
import io.github.apace100.calio.util.ArgumentWrapper;
import io.github.apace100.calio.util.DynamicIdentifier;
import io.github.apace100.calio.util.TagLike;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SerializableDataType<T> {

    private final Class<T> dataClass;
    private final BiConsumer<PacketByteBuf, T> send;
    private final Function<PacketByteBuf, T> receive;
    private final Function<JsonElement, T> read;

    public SerializableDataType(Class<T> dataClass,
                                BiConsumer<PacketByteBuf, T> send,
                                Function<PacketByteBuf, T> receive,
                                Function<JsonElement, T> read) {
        this.dataClass = dataClass;
        this.send = send;
        this.receive = receive;
        this.read = read;
    }

    public void send(PacketByteBuf buffer, Object value) {
        send.accept(buffer, cast(value));
    }

    public T receive(PacketByteBuf buffer) {
        return receive.apply(buffer);
    }

    public T read(JsonElement jsonElement) {
        return read.apply(jsonElement);
    }

    public T cast(Object data) {
        return dataClass.cast(data);
    }

    public static <T> SerializableDataType<List<T>> list(SerializableDataType<T> singleDataType) {
        return new SerializableDataType<>(ClassUtil.castClass(List.class), (buf, list) -> {
            buf.writeInt(list.size());
            int i = 0;
            for(T elem : list) {
                try {
                    singleDataType.send(buf, elem);
                } catch(DataException e) {
                    throw e.prepend("[" + i + "]");
                } catch(Exception e) {
                    throw new DataException(DataException.Phase.WRITING, "[" + i + "]", e);
                }
                i++;
            }
        }, (buf) -> {
            int count = buf.readInt();
            LinkedList<T> list = new LinkedList<>();
            for(int i = 0; i < count; i++) {
                try {
                    list.add(singleDataType.receive(buf));
                } catch(DataException e) {
                    throw e.prepend("[" + i + "]");
                } catch(Exception e) {
                    throw new DataException(DataException.Phase.RECEIVING, "[" + i + "]", e);
                }
            }
            return list;
        }, (json) -> {
            LinkedList<T> list = new LinkedList<>();
            if(json.isJsonArray()) {
                int i = 0;
                for(JsonElement je : json.getAsJsonArray()) {
                    try {
                        list.add(singleDataType.read(je));
                    } catch(DataException e) {
                        throw e.prepend("[" + i + "]");
                    } catch(Exception e) {
                        throw new DataException(DataException.Phase.READING, "[" + i + "]", e);
                    }
                    i++;
                }
            } else {
                list.add(singleDataType.read(json));
            }
            return list;
        });
    }

    public static <T> SerializableDataType<FilterableWeightedList<T>> weightedList(SerializableDataType<T> singleDataType) {
        return new SerializableDataType<>(ClassUtil.castClass(FilterableWeightedList.class), (buf, list) -> {
            buf.writeInt(list.size());
            AtomicInteger i = new AtomicInteger();
            list.entryStream().forEach(entry -> {
                try {
                    singleDataType.send(buf, entry.getElement());
                    buf.writeInt(((WeightedListEntryAccessor) entry).getWeight());
                } catch(DataException e) {
                    throw e.prepend("[" + i.get() + "]");
                } catch(Exception e) {
                    throw new DataException(DataException.Phase.WRITING, "[" + i.get() + "]", e);
                }
                i.getAndIncrement();
            });
        }, (buf) -> {
            int count = buf.readInt();
            FilterableWeightedList<T> list = new FilterableWeightedList<>();
            for (int i = 0; i < count; i++) {
                try {
                    T t = singleDataType.receive(buf);
                    int weight = buf.readInt();
                    list.add(t, weight);
                } catch(DataException e) {
                    throw e.prepend("[" + i + "]");
                } catch(Exception e) {
                    throw new DataException(DataException.Phase.RECEIVING, "[" + i + "]", e);
                }
            }
            return list;
        }, (json) -> {
            FilterableWeightedList<T> list = new FilterableWeightedList<>();
            if (json.isJsonArray()) {
                int i = 0;
                for (JsonElement je : json.getAsJsonArray()) {
                    try {
                        JsonObject weightedObj = je.getAsJsonObject();
                        T elem = singleDataType.read(weightedObj.get("element"));
                        int weight = JsonHelper.getInt(weightedObj, "weight");
                        list.add(elem, weight);
                    } catch(DataException e) {
                        throw e.prepend("[" + i + "]");
                    } catch(Exception e) {
                        throw new DataException(DataException.Phase.READING, "[" + i + "]", e);
                    }
                    i++;
                }
            }
            return list;
        });
    }

    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry) {
        return wrap(
            dataClass,
            SerializableDataTypes.IDENTIFIER,
            registry::getId,
            id -> registry.getOrEmpty(id).orElseThrow(() -> {
                String possibleValues = String.join(", ", registry.getIds().stream().map(Identifier::toString).toList());
                return new RuntimeException("Identifier \"" + id + "\" was not registered in registry \"" + registry.getKey().getValue() + "\". Expected " + possibleValues);
            })
        );
    }

    public static <T> SerializableDataType<T> defaultedRegistry(Class<T> dataClass, Registry<T> registry, String defaultNamespace) {
        return wrap(
            dataClass,
            SerializableDataTypes.STRING,
            t -> Objects.requireNonNull(registry.getId(t)).toString(),
            idString -> {
                Identifier id = DynamicIdentifier.of(idString, defaultNamespace);
                return registry.getOrEmpty(id).orElseThrow(() -> {
                    String possibleValues = String.join(", ", registry.getIds().stream().map(Identifier::toString).toList());
                    return new RuntimeException("Identifier \"" + id + "\" was not registered in registry \"" + registry.getKey().getValue() + "\". Expected " + possibleValues);
                });
            }
        );
    }

    public static <T> SerializableDataType<T> compound(Class<T> dataClass, SerializableData data, Function<SerializableData.Instance, T> toInstance, BiFunction<SerializableData, T, SerializableData.Instance> toData) {
        return new SerializableDataType<>(dataClass,
            (buf, t) -> data.write(buf, toData.apply(data, t)),
            (buf) -> toInstance.apply(data.read(buf)),
            (json) -> toInstance.apply(data.read(json.getAsJsonObject())));
    }

    public static <T extends Enum<T>> SerializableDataType<T> enumValue(Class<T> dataClass) {
        return enumValue(dataClass, null);
    }

    public static <T extends Enum<T>> SerializableDataType<T> enumValue(Class<T> dataClass, HashMap<String, T> additionalMap) {
        return new SerializableDataType<>(dataClass,
            (buf, t) -> buf.writeInt(t.ordinal()),
            (buf) -> dataClass.getEnumConstants()[buf.readInt()],
            (json) -> {
                if(json.isJsonPrimitive()) {
                    JsonPrimitive primitive = json.getAsJsonPrimitive();
                    if(primitive.isNumber()) {
                        int enumOrdinal = primitive.getAsInt();
                        T[] enumValues = dataClass.getEnumConstants();
                        if(enumOrdinal < 0 || enumOrdinal >= enumValues.length) {
                            throw new JsonSyntaxException("Expected to be in the range of 0 - " + (enumValues.length - 1));
                        }
                        return enumValues[enumOrdinal];
                    } else if(primitive.isString()) {
                        String enumName = primitive.getAsString();
                        try {
                            T t = Enum.valueOf(dataClass, enumName);
                            return t;
                        } catch(IllegalArgumentException e0) {
                            try {
                                T t = Enum.valueOf(dataClass, enumName.toUpperCase(Locale.ROOT));
                                return t;
                            } catch (IllegalArgumentException e1) {
                                try {
                                    if(additionalMap == null || !additionalMap.containsKey(enumName)) {
                                        throw new IllegalArgumentException();
                                    }
                                    T t = additionalMap.get(enumName);
                                    return t;
                                } catch (IllegalArgumentException e2) {
                                    T[] enumValues = dataClass.getEnumConstants();
                                    String stringOf = enumValues[0].name() + ", " + enumValues[0].name().toLowerCase(Locale.ROOT);
                                    for(int i = 1; i < enumValues.length; i++) {
                                        stringOf += ", " + enumValues[i].name() + ", " + enumValues[i].name().toLowerCase(Locale.ROOT);
                                    }
                                    throw new JsonSyntaxException("Expected value to be a string of: " + stringOf);
                                }
                            }
                        }
                    }
                }
                throw new JsonSyntaxException("Expected value to be either an integer or a string.");
            });
    }

    public static <T> SerializableDataType<T> mapped(Class<T> dataClass, BiMap<String, T> map) {
        return new SerializableDataType<>(dataClass,
            (buf, t) -> buf.writeString(map.inverse().get(t)),
            (buf) -> map.get(buf.readString(32767)),
            (json) -> {
                if(json.isJsonPrimitive()) {
                    JsonPrimitive primitive = json.getAsJsonPrimitive();
                    if(primitive.isString()) {
                        String name = primitive.getAsString();
                        try {
                            if(map == null || !map.containsKey(name)) {
                                throw new IllegalArgumentException();
                            }
                            T t = map.get(name);
                            return t;
                        } catch (IllegalArgumentException e2) {
                            throw new JsonSyntaxException("Expected value to be a string of: " + map.keySet().stream().reduce((s0, s1) -> s0 + ", " + s1));
                        }
                    }
                }
                throw new JsonSyntaxException("Expected value to be a string.");
            });
    }

    public static <T, U> SerializableDataType<T> wrap(Class<T> dataClass, SerializableDataType<U> base, Function<T, U> toFunction, Function<U, T> fromFunction) {
        return new SerializableDataType<>(dataClass,
            (buf, t) -> base.send(buf, toFunction.apply(t)),
            (buf) -> fromFunction.apply(base.receive(buf)),
            (json) -> fromFunction.apply(base.read(json)));
    }

    public static <T> SerializableDataType<TagKey<T>> tag(RegistryKey<? extends Registry<T>> registryKey) {
        return SerializableDataType.wrap(ClassUtil.castClass(TagKey.class), SerializableDataTypes.IDENTIFIER,
            TagKey::id,
            id -> TagKey.of(registryKey, id));
    }

    public static <T> SerializableDataType<RegistryKey<T>> registryKey(RegistryKey<Registry<T>> registryKeyRegistry) {
        return SerializableDataType.wrap(
            ClassUtil.castClass(RegistryKey.class),
            SerializableDataTypes.IDENTIFIER,
            RegistryKey::getValue, identifier -> RegistryKey.of(registryKeyRegistry, identifier)
        );
    }

    public static <T extends Enum<T>> SerializableDataType<EnumSet<T>> enumSet(Class<T> enumClass, SerializableDataType<T> enumDataType) {
        return new SerializableDataType<>(ClassUtil.castClass(EnumSet.class),
            (buf, set) -> {
                buf.writeInt(set.size());
                set.forEach(t -> buf.writeInt(t.ordinal()));
            },
            (buf) -> {
                int size = buf.readInt();
                EnumSet<T> set = EnumSet.noneOf(enumClass);
                T[] allValues = enumClass.getEnumConstants();
                for(int i = 0; i < size; i++) {
                    int ordinal = buf.readInt();
                    set.add(allValues[ordinal]);
                }
                return set;
            },
            (json) -> {
                EnumSet<T> set = EnumSet.noneOf(enumClass);
                if(json.isJsonPrimitive()) {
                    T t = enumDataType.read.apply(json);
                    set.add(t);
                } else
                if(json.isJsonArray()) {
                    JsonArray array = json.getAsJsonArray();
                    for (JsonElement jsonElement : array) {
                        T t = enumDataType.read.apply(jsonElement);
                        set.add(t);
                    }
                } else {
                    throw new RuntimeException("Expected enum set to be either an array or a primitive.");
                }
                return set;
            });
    }

    public static <T, U extends ArgumentType<T>> SerializableDataType<ArgumentWrapper<T>> argumentType(U argumentType) {
        return wrap(ClassUtil.castClass(ArgumentWrapper.class), SerializableDataTypes.STRING,
            ArgumentWrapper::rawArgument,
            str -> {
                try {
                    T t = argumentType.parse(new StringReader(str));
                    return new ArgumentWrapper<>(t, str);
                } catch (CommandSyntaxException e) {
                    throw new RuntimeException("Wrong syntax in argument type data", e);
                }
            });
    }

    public static <T> SerializableDataType<TagLike<T>> tagLike(Registry<T> registry) {
        return new SerializableDataType<>(ClassUtil.castClass(TagLike.class),
                (packetByteBuf, tagLike) -> tagLike.write(packetByteBuf),
                packetByteBuf -> {
                    TagLike<T> tagLike = new TagLike<>(registry);
                    tagLike.read(packetByteBuf);
                    return tagLike;
                },
                jsonElement -> {
                    TagLike<T> tagLike = new TagLike<>(registry);
                    if (!jsonElement.isJsonArray()) {
                        throw new JsonSyntaxException("Expected a JSON array,");
                    }
                    JsonArray jsonArray = jsonElement.getAsJsonArray();
                    jsonArray.forEach(je -> {
                        String s = je.getAsString();
                        if (s.startsWith("#")) {
                            Identifier id = new Identifier(s.substring(1));
                            tagLike.addTag(id);
                        } else {
                            tagLike.add(new Identifier(s));
                        }
                    });
                    return tagLike;
                });
    }
}
