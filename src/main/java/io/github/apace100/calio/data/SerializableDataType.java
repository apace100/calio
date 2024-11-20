package io.github.apace100.calio.data;

import com.google.common.base.Suppliers;
import com.google.common.collect.BiMap;
import com.google.gson.JsonElement;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import io.github.apace100.calio.CalioServer;
import io.github.apace100.calio.codec.JsonCodec;
import io.github.apace100.calio.mixin.WeightedListAccessor;
import io.github.apace100.calio.registry.DataObjectFactory;
import io.github.apace100.calio.util.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.WeightedList;
import net.minecraft.util.function.ValueLists;
import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"unused", "unchecked"})
public class SerializableDataType<T> {

    private final Codec<T> codec;
    private final PacketCodec<RegistryByteBuf, T> packetCodec;

    private final boolean root;

    public SerializableDataType(Codec<T> codec, PacketCodec<RegistryByteBuf, T> packetCodec, boolean root) {
        this.codec = codec;
        this.packetCodec = packetCodec;
        this.root = root;
    }

    public SerializableDataType(Codec<T> codec, PacketCodec<RegistryByteBuf, T> packetCodec) {
        this(codec, packetCodec, true);
    }

    public SerializableDataType(Codec<T> codec) {
        this(codec, PacketCodecs.codec(codec).cast());
    }

    /**
     *  <p>Use {@link #of(Codec)}, {@link #of(Codec, PacketCodec)}, or {@link #jsonBacked(BiConsumer, Function, Function, Function)}
     *  (if you want to keep using the JSON codec for encoding/decoding the type) instead.</p>
     */
    @Deprecated
    public SerializableDataType(Class<?> dataClass, BiConsumer<RegistryByteBuf, T> send, Function<RegistryByteBuf, T> receive, Function<JsonElement, T> fromJson, Function<T, JsonElement> toJson) {
        this(new JsonCodec<>(fromJson, toJson), PacketCodec.of((value, buf) -> send.accept(buf, value), receive::apply));
    }

    public Codec<T> codec() {
        return codec;
    }

    public PacketCodec<RegistryByteBuf, T> packetCodec() {
        return packetCodec;
    }

    public <I> DataResult<T> read(DynamicOps<I> ops, I input) {
        return this.codec().parse(ops, input);
    }

    public <I> DataResult<I> write(DynamicOps<I> ops, T input) {
        return this.codec().encodeStart(ops, input);
    }

    public T receive(RegistryByteBuf buf) {
        return this.packetCodec().decode(buf);
    }

    public void send(RegistryByteBuf buf, T value) {
        this.packetCodec().encode(buf, value);
    }

    /**
     *  Use {@link #read(DynamicOps, Object)} with {@link JsonOps#INSTANCE} instead.
     */
    @Deprecated
    public T read(JsonElement jsonElement) {
        return read(JsonOps.INSTANCE, jsonElement).getOrThrow();
    }

    /**
     *  Use {@link #write(DynamicOps, T)} with {@link JsonOps#INSTANCE} instead.
     */
    @Deprecated
    public JsonElement writeUnsafely(Object value) throws Exception {

        try {
            return this.write(this.cast(value));
        }

        catch (ClassCastException e) {
            throw new Exception(e);
        }

    }

    /**
     *  Use {@link #write(DynamicOps, T)} with {@link JsonOps#INSTANCE} instead.
     */
    @Deprecated
    public JsonElement write(T value) {
        return write(JsonOps.INSTANCE, value).getOrThrow();
    }

    public T cast(Object data) {
        return (T) data;
    }

    public <S> SerializableDataType<S> xmap(Function<? super T, ? extends S> to, Function<? super S, ? extends T> from) {
        return new SerializableDataType<>(codec().xmap(to, from), packetCodec().xmap(to, from), this.isRoot());
    }

    public <S> SerializableDataType<S> comapFlatMap(Function<? super T, ? extends DataResult<? extends S>> to, Function<? super S, ? extends T> from) {
        return new SerializableDataType<>(codec().comapFlatMap(to, from), packetCodec().xmap(t -> to.apply(t).getOrThrow(), from), this.isRoot());
    }

    public <S> SerializableDataType<S> flatComapMap(Function<? super T, ? extends S> to, Function<? super S, ? extends DataResult<? extends T>> from) {
        return new SerializableDataType<>(codec().flatComapMap(to, from), packetCodec().xmap(to, s -> from.apply(s).getOrThrow()), this.isRoot());
    }

    public <S> SerializableDataType<S> flatXmap(Function<? super T, ? extends DataResult<? extends S>> to, Function<? super S, ? extends DataResult<? extends T>> from) {
        return new SerializableDataType<>(codec().flatXmap(to, from), packetCodec().xmap(t -> to.apply(t).getOrThrow(), s -> from.apply(s).getOrThrow()), this.isRoot());
    }

    public SerializableDataType<T> validate(Function<T, DataResult<T>> checker) {
        return flatXmap(checker, checker);
    }

    public void validateValue(T value) throws Exception {

        if (value instanceof Validatable validatable) {
            validatable.validate();
        }

    }

    public SerializableDataType<List<T>> list() {
        return list(this);
    }

    public SerializableDataType<List<T>> list(int max) {
        return list(this, max);
    }

    public SerializableDataType<List<T>> list(int min, int max) {
        return list(this, min, max);
    }

    public SerializableDataType<Optional<T>> optional() {
        return optional(this, false);
    }

    public SerializableDataType<Optional<T>> lenientOptional() {
        return optional(this, true);
    }

    public SerializableDataType<Optional<T>> lenientOptional(Consumer<String> warningHandler) {
        return optional(this, true, warningHandler);
    }

    public SerializableDataType<T> setRoot(boolean root) {
        return new SerializableDataType<>(this.codec, this.packetCodec, root);
    }

    public boolean isRoot() {
        return root;
    }

    public SerializableData.Field<T> field(String name) {
        return new SerializableData.FieldImpl<>(name, setRoot(false));
    }

    public SerializableData.Field<T> defaultedField(String name, Supplier<T> defaultSupplier) {
        return new SerializableData.DefaultedFieldImpl<>(name, setRoot(false), defaultSupplier);
    }

    public SerializableData.Field<T> functionedDefaultField(String name, Function<SerializableData.Instance, T> defaultFunction) {
        return new SerializableData.FunctionedDefaultFieldImpl<>(name, setRoot(false), defaultFunction);
    }

    public static <T> SerializableDataType<T> of(Codec<T> codec) {
        return new SerializableDataType<>(codec);
    }

    public static <T> SerializableDataType<T> of(Codec<T> codec, PacketCodec<RegistryByteBuf, T> packetCodec) {
        return new SerializableDataType<>(codec, packetCodec);
    }

    public static <T> SerializableDataType<T> jsonBacked(BiConsumer<RegistryByteBuf, T> send, Function<RegistryByteBuf, T> receive, Function<T, JsonElement> toJson, Function<JsonElement, T> fromJson) {
        return new SerializableDataType<>(new JsonCodec<>(fromJson, toJson), PacketCodec.ofStatic(send::accept, receive::apply));
    }

    public static <T> RecursiveSerializableDataType<T> recursive(Function<SerializableDataType<T>, SerializableDataType<T>> wrapped) {
        return new RecursiveSerializableDataType<>(wrapped);
    }

    public static <T> RecursiveSerializableDataType<T> lazy(Supplier<SerializableDataType<T>> delegate) {
        return recursive(self -> delegate.get());
    }

    public static <T> ListSerializableDataType<T> list(SerializableDataType<T> elementDataType) {
        return list(elementDataType, Integer.MAX_VALUE);
    }

    public static <T> ListSerializableDataType<T> list(SerializableDataType<T> elementDataType, int max) {
        return list(elementDataType, 0, max);
    }

    public static <T> ListSerializableDataType<T> list(SerializableDataType<T> elementDataType, int min, int max) {
        return new ListSerializableDataType<>(elementDataType, min, max);
    }

    public static <T> SerializableDataType<WeightedList<T>> weightedList(SerializableDataType<T> singleDataType) {

        CompoundSerializableDataType<WeightedList.Entry<T>> entryDataType = compound(
            new SerializableData()
                .add("element", singleDataType)
                .add("weight", SerializableDataTypes.INT, 1),
            data -> new WeightedList.Entry<>(
                data.get("element"),
                data.get("weight")
            ),
            (entry, serializableData) -> serializableData.instance()
                .set("element", entry.getElement())
                .set("weight", entry.getWeight())
        );

        return entryDataType.list().xmap(
            WeightedList::new,
            weightedList -> new ObjectArrayList<>(((WeightedListAccessor<T>) weightedList).getEntries())
        );

    }

    /**
     *  Use {@link #registry(Registry)} instead.
     */
    @Deprecated
    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry) {
        return registry(dataClass, registry, false);
    }

    /**
     *  Use {@link #registry(Registry, String)} instead.
     */
    @Deprecated
    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry, String defaultNamespace) {
        return registry(dataClass, registry, defaultNamespace, false);
    }

    /**
     *  Use {@link #registry(Registry, boolean)} instead.
     */
    @Deprecated
    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry, boolean showPossibleValues) {
        return registry(dataClass, registry, Identifier.DEFAULT_NAMESPACE, showPossibleValues);
    }

    /**
     *  Use {@link #registry(Registry, String, boolean)} instead.
     */
    @Deprecated
    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry, String defaultNamespace, boolean showPossibleValues) {
        return registry(dataClass, registry, defaultNamespace, null, (reg, id) -> {
            String possibleValues = showPossibleValues ? ". Expected value to be any of " + String.join(", ", reg.getIds().stream().map(Identifier::toString).toList()) : "";
            return new RuntimeException("Type \"%s\" is not registered in registry \"%s\"%s".formatted(id, registry.getKey().getValue(), possibleValues));
        });
    }

    /**
     *  Use {@link #registry(Registry, BiFunction)} instead.
     */
    @Deprecated
    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry, BiFunction<Registry<T>, Identifier, RuntimeException> exception) {
        return registry(dataClass, registry, Identifier.DEFAULT_NAMESPACE, null, exception);
    }

    /**
     *  Use {@link #registry(Registry, String, IdentifierAlias, BiFunction)} instead.
     */
    @Deprecated
    public static <T> SerializableDataType<T> registry(Class<T> dataClass, Registry<T> registry, String defaultNamespace, @Nullable IdentifierAlias aliases, BiFunction<Registry<T>, Identifier, RuntimeException> exception) {
        return registry(registry, defaultNamespace, aliases, (_registry, id) -> exception.apply(_registry, id).getMessage());
    }

    public static <T> SerializableDataType<T> registry(Registry<T> registry) {
        return registry(registry, false);
    }

    public static <T> SerializableDataType<T> registry(Registry<T> registry, String defaultNamespace) {
        return registry(registry, defaultNamespace, false);
    }

    public static <T> SerializableDataType<T> registry(Registry<T> registry, boolean showPossibleValues) {
        return registry(registry, Identifier.DEFAULT_NAMESPACE, showPossibleValues);
    }

    public static <T> SerializableDataType<T> registry(Registry<T> registry, String defaultNamespace, boolean showPossibleValues) {
        return registry(registry, defaultNamespace, null, (reg, id) -> {
            String possibleValues = showPossibleValues ? " Expected value to be any of " + String.join(", ", reg.getIds().stream().map(Identifier::toString).toList()) : "";
            return "Type \"%s\" is not registered in registry \"%s\"!%s".formatted(id, registry.getKey().getValue(), possibleValues);
        });
    }

    public static <T> SerializableDataType<T> registry(Registry<T> registry, BiFunction<Registry<T>, Identifier, String> exception) {
        return registry(registry, Identifier.DEFAULT_NAMESPACE, null, exception);
    }

    public static <T> SerializableDataType<T> registry(Registry<T> registry, String defaultNamespace, @Nullable IdentifierAlias aliases, BiFunction<Registry<T>, Identifier, String> exception) {
        return of(
            new Codec<>() {

                @Override
                public <I> DataResult<Pair<T, I>> decode(DynamicOps<I> ops, I input) {
                    return ops.getStringValue(input)
                        .flatMap(str -> DynamicIdentifier.ofResult(str, defaultNamespace))
                        .flatMap(id -> registry
                            .getOrEmpty(aliases == null ? id : aliases.resolveAlias(id, registry::containsId))
                            .map(t -> Pair.of(t, input))
                            .map(DataResult::success)
                            .orElse(DataResult.error(() -> exception.apply(registry, id))));
                }

                @Override
                public <I> DataResult<I> encode(T input, DynamicOps<I> ops, I prefix) {
                    return registry.getCodec().encode(input, ops, prefix);
                }

            },
            PacketCodecs.registryValue(registry.getKey())
        );
    }

    /**
     *
     *  <p>Use any of the following instead:</p>
     *
     *  <ul>
     *      <li>{@link #compound(DataObjectFactory) compound(DataObjectFactory&lt;T&gt;)} for processing with a {@link DataObjectFactory}.</li>
     *      <li>{@link #compound(SerializableData, Function, BiFunction) compound(SerializableData, Function&lt;SerializableData.Instance, T&gt, BiFunction&lt;T, SerializableData, SerializableData.Instance&gt;)} for simple processing.</li>
     *      <li>{@link CompoundSerializableDataType#CompoundSerializableDataType(SerializableData, Function, Function) CompoundSerializableDataType(SerializableData, Function&lt;SerializableData, MapCodec&lt;T&gt;&gt;, Function&lt;SerializableData, PacketCodec&lt;RegistryByteBuf, T&gt;&gt;)} for more granular control on how the element will be decoded/encoded.</li>
     *  </ul>
     *
     */
    @Deprecated
    public static <T> CompoundSerializableDataType<T> compound(Class<T> dataClass, SerializableData serializableData, Function<SerializableData.Instance, T> fromData, BiFunction<SerializableData, T, SerializableData.Instance> toData) {
        return compound(serializableData, fromData, (t, _serializableData) -> toData.apply(_serializableData, t));
    }

    public static <T> CompoundSerializableDataType<T> compound(DataObjectFactory<T> factory) {
        return compound(factory.getSerializableData(), factory::fromData, factory::toData);
    }

    public static <T> CompoundSerializableDataType<T> compound(SerializableData serializableData, Function<SerializableData.Instance, T> fromData, BiFunction<T, SerializableData, SerializableData.Instance> toData) {
        return new CompoundSerializableDataType<>(
            serializableData,
            _serializableData -> new MapCodec<>() {

                @Override
                public <I> DataResult<T> decode(DynamicOps<I> ops, MapLike<I> input) {
                    return _serializableData.decode(ops, input).flatMap(data -> {

                        try {
                            return DataResult.success(fromData.apply(data));
                        }

                        catch (Exception e) {

                            if (_serializableData.isRoot()) {
                                return DataResult.error(e::getMessage);
                            }

                            else {
                                throw e;
                            }

                        }

                    });
                }

                @Override
                public <I> RecordBuilder<I> encode(T input, DynamicOps<I> ops, RecordBuilder<I> prefix) {

                    try {
                        return _serializableData.encode(toData.apply(input, _serializableData), ops, prefix);
                    }

                    catch (Exception e) {

                        if (_serializableData.isRoot()) {
                            return prefix.withErrorsFrom(DataResult.error(e::getMessage));
                        }

                        else {
                            throw e;
                        }

                    }

                }

                @Override
                public <I> Stream<I> keys(DynamicOps<I> ops) {
                    return _serializableData.keys(ops);
                }

            },
            _serializableData -> new PacketCodec<>() {

                @Override
                public T decode(RegistryByteBuf buf) {
                    SerializableData.Instance data = _serializableData.receive(buf);
                    return fromData.apply(data);
                }

                @Override
                public void encode(RegistryByteBuf buf, T value) {
                    SerializableData.Instance data = toData.apply(value, _serializableData);
                    _serializableData.send(buf, data);
                }

            }
        );
    }

    public static <V> SerializableDataType<Map<String, V>> map(SerializableDataType<V> valueDataType) {
        return lazy(() -> map(SerializableDataTypes.STRING, valueDataType));
    }

    public static <K, V> SerializableDataType<Map<K, V>> map(SerializableDataType<K> keyDataType, SerializableDataType<V> valueDataType) {
        return new SerializableDataType<>(
            new UnboundedMapCodec<>(keyDataType.codec(), valueDataType.codec()),
            PacketCodec.ofStatic(
                (buf, map) -> {

                    buf.writeVarInt(map.size());
                    map.forEach((key, value) -> {
                        keyDataType.send(buf, key);
                        valueDataType.send(buf, value);
                    });

                },
                buf -> {

                    Map<K, V> map = new Object2ObjectLinkedOpenHashMap<>();
                    int size = buf.readVarInt();

                    for (int i = 0; i < size; i++) {

                        K key = keyDataType.receive(buf);
                        V value = valueDataType.receive(buf);

                        map.put(key, value);

                    }

                    return map;

                }
            )
        );
    }

    /**
     *  Use {@link #mapped(Supplier)} <b>(recommended)</b> or {@link #mapped(BiMap)} instead.
     */
    @Deprecated
    public static <T> SerializableDataType<T> mapped(Class<T> dataClass, @NotNull BiMap<String, T> biMap) {
        return mapped(biMap);
    }

    public static <V> SerializableDataType<V> mapped(@NotNull BiMap<String, V> biMap) {
        return mapped(Suppliers.memoize(() -> biMap));
    }

    public static <V> SerializableDataType<V> mapped(Supplier<BiMap<String, V>> acceptedValuesSupplier) {
        return new SerializableDataType<>(
            new Codec<>() {

                @Override
                public <T> DataResult<Pair<V, T>> decode(DynamicOps<T> ops, T input) {
                    return ops.getStringValue(input)
                        .flatMap(stringInput -> {

                            BiMap<String, V> acceptedValues = acceptedValuesSupplier.get();
                            V value = acceptedValues.get(stringInput);

                            if (acceptedValues.containsKey(stringInput)) {
                                return DataResult.success(Pair.of(value, input));
                            }

                            else {
                                return DataResult.error(() -> "Expected value to be any of " + String.join(", ", acceptedValues.keySet()));
                            }

                        });
                }

                @Override
                public <T> DataResult<T> encode(V input, DynamicOps<T> ops, T prefix) {

                    BiMap<String, V> acceptedValues = acceptedValuesSupplier.get();
                    String key = acceptedValues.inverse().get(input);

                    if (key != null) {
                        return DataResult.success(ops.createString(key));
                    }

                    else {
                        return DataResult.error(() -> "Element " + input + " is not associated with any keys!");
                    }

                }

            },
            PacketCodec.ofStatic(
                (buf, value) -> {

                    BiMap<String, V> acceptedValues = acceptedValuesSupplier.get();
                    String key = acceptedValues.inverse().get(value);

                    if (key != null) {
                        buf.writeString(key);
                    }

                    else {
                        throw new IllegalStateException("Element " + value + " is not associated with any keys!");
                    }

                },
                buf -> {

                    BiMap<String, V> acceptedValues = acceptedValuesSupplier.get();

                    String key = buf.readString();
                    V value = acceptedValues.get(key);

                    if (acceptedValues.containsKey(key)) {
                        return value;
                    }

                    else {
                        throw new IllegalStateException("Expected value to be any of " + String.join(", ", acceptedValues.keySet()));
                    }

                }
            )
        );
    }

    /**
     *  Use {@link #xmap(Function, Function)} instead.
     */
    @Deprecated
    public static <T, U> SerializableDataType<T> wrap(Class<T> dataClass, SerializableDataType<U> base, Function<T, U> toFunction, Function<U, T> fromFunction) {
        return base.xmap(fromFunction, toFunction);
    }

    /**
     *  Use {@link #tagKey(RegistryKey)} instead.
     */
    @Deprecated
    public static <T> SerializableDataType<TagKey<T>> tag(RegistryKey<? extends Registry<T>> registryRef) {
        return tagKey(registryRef);
    }

    public static <T> SerializableDataType<TagKey<T>> tagKey(RegistryKey<? extends Registry<T>> registryRef) {
        return lazy(() -> new SerializableDataType<>(
            new Codec<>() {

                @Override
                public <I> DataResult<Pair<TagKey<T>, I>> decode(DynamicOps<I> ops, I input) {
                    return SerializableDataTypes.IDENTIFIER.codec().decode(ops, input)
                        .flatMap(idAndInput -> {

                            Pair<TagKey<T>, I> tagAndInput = idAndInput.mapFirst(id -> TagKey.of(registryRef, id));
                            TagKey<T> tag = tagAndInput.getFirst();

                            if (CalioServer.getRegistryTags().containsKey(tag)) {
                                return DataResult.success(tagAndInput);
                            }

                            else {
                                return RegistryOpsUtil.getEntryLookup(ops, registryRef)
                                    .map(DataResult::success)
                                    .orElse(DataResult.error(() -> "Couldn't find registry \"" + registryRef.getValue() + "\"; " + (ops instanceof RegistryOps<I> ? "it doesn't exist!" : "the passed dynamic ops is not a registry ops!")))
                                    .flatMap(entryLookup -> entryLookup.getOptional(tag)
                                        .map(registryEntries -> tagAndInput)
                                        .map(DataResult::success)
                                        .orElse(DataResult.error(() -> "Tag \"" + tag.id() + "\" for registry \"" + registryRef.getValue() + "\" doesn't exist!")));
                            }

                        });
                }

                @Override
                public <I> DataResult<I> encode(TagKey<T> input, DynamicOps<I> ops, I prefix) {
                    return SerializableDataTypes.IDENTIFIER.codec().encode(input.id(), ops, prefix);
                }

            },
            PacketCodec.ofStatic(
                (buf, value) -> buf.writeIdentifier(value.id()),
                buf -> TagKey.of(registryRef, buf.readIdentifier())
            )
        ));
    }

    public static <A> SerializableDataType<RegistryEntry<A>> registryEntry(Registry<A> registry) {
        return new SerializableDataType<>(
			new Codec<>() {

				@Override
				public <T> DataResult<Pair<RegistryEntry<A>, T>> decode(DynamicOps<T> ops, T input) {
					return SerializableDataTypes.IDENTIFIER.codec().decode(ops, input)
                        .flatMap(idAndInput -> {

                            Pair<RegistryKey<A>, T> keyAndInput = idAndInput.mapFirst(id -> RegistryKey.of(registry.getKey(), id));
                            RegistryKey<A> key = keyAndInput.getFirst();

                            return registry.getEntry(key)
                                .map(entry -> keyAndInput.mapFirst(k -> (RegistryEntry<A>) entry))
                                .map(DataResult::success)
                                .orElse(DataResult.error(() -> "Type \"" + key.getValue() + "\" is not registered in registry \"" + registry.getKey().getValue() + "\"!"));

                        });
				}

				@Override
				public <T> DataResult<T> encode(RegistryEntry<A> input, DynamicOps<T> ops, T prefix) {
					return input.getKeyOrValue().map(
                        key -> SerializableDataTypes.IDENTIFIER.codec().encode(key.getValue(), ops, prefix),
                        a -> registry.getCodec().encode(a, ops, prefix)
                    );
				}

			},
            PacketCodecs.registryEntry(registry.getKey())
        );
    }

    public static <T> SerializableDataType<RegistryKey<T>> registryKey(RegistryKey<? extends Registry<T>> registryRef) {
        return registryKey(registryRef, Set.of());
    }

    public static <T> SerializableDataType<RegistryKey<T>> registryKey(RegistryKey<? extends Registry<T>> registryRef, Collection<RegistryKey<T>> exemptions) {
        return new SerializableDataType<>(
            new Codec<>() {

                @Override
                public <I> DataResult<Pair<RegistryKey<T>, I>> decode(DynamicOps<I> ops, I input) {
                    return SerializableDataTypes.IDENTIFIER.codec().decode(ops, input)
                        .flatMap(idAndInput -> {

                            Pair<RegistryKey<T>, I> keyAndInput = idAndInput.mapFirst(id -> RegistryKey.of(registryRef, id));
                            RegistryKey<T> key = keyAndInput.getFirst();

                            if (exemptions.contains(key)) {
                                return DataResult.success(keyAndInput);
                            }

                            else {
                                return RegistryOpsUtil.getEntryLookup(ops, registryRef)
                                    .map(DataResult::success)
                                    .orElse(DataResult.error(() -> "Couldn't find registry \"" + registryRef.getValue() + "\"; " + (ops instanceof RegistryOps<I> ? "it doesn't exist!" : "the passed dynamic ops is not a registry ops!")))
                                    .flatMap(entryLookup -> entryLookup.getOptional(key)
                                        .map(ref -> keyAndInput)
                                        .map(DataResult::success)
                                        .orElse(DataResult.error(() -> "Type \"" + key.getValue() + "\" is not registered in registry \"" + registryRef.getValue() + "\"!")));
                            }

                        });
                }

                @Override
                public <I> DataResult<I> encode(RegistryKey<T> input, DynamicOps<I> ops, I prefix) {
                    return SerializableDataTypes.IDENTIFIER.codec().encode(input.getValue(), ops, prefix);
                }

            },
            PacketCodec.ofStatic(
                PacketByteBuf::writeRegistryKey,
                buf -> buf.readRegistryKey(registryRef)
            )
        );
    }

    public static <E extends Enum<E>> SerializableDataType<E> enumValue(Class<E> enumClass) {
        return enumValueInternal(enumClass, HashMap::new);
    }

    public static <E extends Enum<E>> SerializableDataType<E> enumValue(Class<E> enumClass, Map<String, E> additionalMap) {
        return enumValue(enumClass, () -> additionalMap);
    }

    public static <E extends Enum<E>> SerializableDataType<E> enumValue(Class<E> enumClass, Supplier<Map<String, E>> additionalMapSupplier) {
        return enumValueInternal(enumClass, Suppliers.memoize(() -> additionalMapSupplier.get().entrySet()
            .stream()
            .map(e -> Map.entry(e.getKey().toUpperCase(Locale.ROOT), e.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (prev, curr) -> curr, HashMap::new))));
    }

    @ApiStatus.Internal
    private static <E extends Enum<E>> SerializableDataType<E> enumValueInternal(Class<E> enumClass, Supplier<Map<String, E>> additionalMapSupplier) {
        IntFunction<E> ordinalToEnum = ValueLists.createIdToValueFunction((ToIntFunction<E>) Enum::ordinal, enumClass.getEnumConstants(), ValueLists.OutOfBoundsHandling.CLAMP);
        return new SerializableDataType<>(
            new Codec<>() {

                @Override
                public <T> DataResult<Pair<E, T>> decode(DynamicOps<T> ops, T input) {

                    DataResult<Pair<E, T>> enumResult = ops.getNumberValue(input)
                        .map(Number::intValue)
                        .map(ordinalToEnum::apply)
                        .map(e -> Pair.of(e, input));

                    if (enumResult.isSuccess()) {
                        return enumResult;
                    }

                    else {
                        return ops.getStringValue(input)
                            .map(stringInput -> stringInput.toUpperCase(Locale.ROOT))
                            .flatMap(stringInput -> {

                                Map<String, E> additionalMap = additionalMapSupplier.get();
                                E[] enumValues = enumClass.getEnumConstants();

                                if (additionalMap.containsKey(stringInput)) {
                                    return DataResult.success(Pair.of(additionalMap.get(stringInput), input));
                                }

                                E queriedEnum = EnumUtils.getEnumIgnoreCase(enumClass, stringInput);
                                if (queriedEnum != null) {
                                    return DataResult.success(Pair.of(queriedEnum, input));
                                }

                                else {

                                    Set<String> validValues = new LinkedHashSet<>();

                                    Stream.of(enumValues).map(Enum::name).forEach(validValues::add);
                                    validValues.addAll(additionalMap.keySet());

                                    return DataResult.error(() -> "Expected value to be any of: " + String.join(", ", validValues) + " (case-insensitive)");

                                }

                            });
                    }

                }

                @Override
                public <T> DataResult<T> encode(E input, DynamicOps<T> ops, T prefix) {
                    return DataResult.success(ops.createString(input.name()));
                }

            },
            PacketCodecs.indexed(ordinalToEnum, Enum::ordinal).cast()
        );
    }

    /**
     *  Use {@link #enumSet(SerializableDataType)} instead.
     */
    @Deprecated
    public static <E extends Enum<E>> SerializableDataType<EnumSet<E>> enumSet(Class<E> enumClass, SerializableDataType<E> enumDataType) {
        return enumSet(enumDataType);
    }

    public static <E extends Enum<E>> SerializableDataType<EnumSet<E>> enumSet(SerializableDataType<E> enumDataType) {
        return enumDataType.list(1, Integer.MAX_VALUE).xmap(
            EnumSet::copyOf,
            ObjectArrayList::new
        );
    }

    /**
     *  Use either {@link #boundNumber(SerializableDataType, Number, BiFunction, Number, BiFunction)} or the built-in methods for ranged numbers, like {@link Codec#intRange(int, int)} and pass it to {@link #of(Codec)}
     *  (or {@link #of(Codec, PacketCodec)} if you want to pass its packet codec as well.)
     */
    @Deprecated
    public static <N extends Number & Comparable<N>> SerializableDataType<N> boundNumber(SerializableDataType<N> numberDataType, N min, N max, Function<N, BiFunction<N, N, N>> read) {
        return numberDataType.comapFlatMap(
            number -> {

                try {
                    return DataResult.success(read.apply(number).apply(min, max));
                }

                catch (Exception e) {
                    return DataResult.error(e::getMessage);
                }

            },
            Function.identity()
        );
    }

    public static <N extends Number & Comparable<N>> SerializableDataType<N> boundNumber(SerializableDataType<N> numberDataType, N min, N max) {
        return boundNumber(numberDataType,
            min, (value, _min) -> "Expected value to be at least " + _min + "! (current value: " + value + ")",
            max, (value, _max) -> "Expected value to be at most " + _max + "! (current value: " + value + ")"
        );
    }

    public static <N extends Number & Comparable<N>> SerializableDataType<N> boundNumber(SerializableDataType<N> numberDataType, N min, BiFunction<N, N, String> underMinError, N max, BiFunction<N, N, String> overMaxError) {
        return numberDataType.comapFlatMap(
            number -> {

                if (number.compareTo(min) < 0) {
                    return DataResult.error(() -> underMinError.apply(number, min));
                }

                else if (number.compareTo(max) > 0) {
                    return DataResult.error(() -> overMaxError.apply(number, max));
                }

                else {
                    return DataResult.success(number);
                }

            },
            Function.identity()
        );
    }

    public static <T, U extends ArgumentType<T>> SerializableDataType<ArgumentWrapper<T>> argumentType(U argumentType) {
        return lazy(() -> SerializableDataTypes.STRING.comapFlatMap(
            input -> {

                try {

                    StringReader inputReader = new StringReader(input);
                    T argument = argumentType.parse(inputReader);

                    return DataResult.success(new ArgumentWrapper<>(argument, input));

                }

                catch (Exception e) {
                    return DataResult.error(e::getMessage);
                }

            },
            ArgumentWrapper::input
        ));
    }

    private static final Supplier<SerializableDataType<Set<TagEntry>>> TAG_ENTRY_SET = Suppliers.memoize(() -> SerializableDataTypes.TAG_ENTRIES.xmap(
        ObjectOpenHashSet::new,
        ObjectArrayList::new
    ));

    public static <E> SerializableDataType<TagLike<E>> tagLike(Registry<E> registry) {
        return lazy(() -> of(
            new Codec<>() {

                @Override
                public <T> DataResult<Pair<TagLike<E>, T>> decode(DynamicOps<T> ops, T input) {
                    return TAG_ENTRY_SET.get().codec().decode(ops, input)
                        .map(entriesAndInput -> entriesAndInput
                            .mapFirst(entries -> new TagLike.Builder<>(registry.getKey(), entries))
                            .mapFirst(builder -> builder.build(registry.getReadOnlyWrapper())));
                }

                @Override
                public <T> DataResult<T> encode(TagLike<E> input, DynamicOps<T> ops, T prefix) {
                    return TAG_ENTRY_SET.get().codec().encode(input.entries(), ops, prefix);
                }

            },
            PacketCodec.of(
                TagLike::write,
                TagLike::read
            )
        ));
    }

    public static <E> SerializableDataType<TagLike<E>> tagLike(RegistryKey<E> registryKey) {
        return lazy(() -> of(
            new Codec<>() {

                private final RegistryKey<? extends Registry<E>> registryRef = registryKey.getRegistryRef();

                @Override
                public <T> DataResult<Pair<TagLike<E>, T>> decode(DynamicOps<T> ops, T input) {
                    return RegistryOpsUtil.getEntryLookup(ops, registryRef)
                        .map(DataResult::success)
                        .orElse(DataResult.error(() -> "Couldn't find registry \"" + registryRef.getValue() + "\"; " + (ops instanceof RegistryOps<T> ? "it doesn't exist!" : "the passed dynamic ops is not a registry ops!")))
                        .flatMap(entryLookup -> TAG_ENTRY_SET.get().codec().decode(ops, input)
                            .map(entriesAndInput -> entriesAndInput
                                .mapFirst(entries -> new TagLike.Builder<>(registryRef, entries))
                                .mapFirst(builder -> builder.build(entryLookup))));
                }

                @Override
                public <T> DataResult<T> encode(TagLike<E> input, DynamicOps<T> ops, T prefix) {
                    return TAG_ENTRY_SET.get().codec().encode(input.entries(), ops, prefix);
                }

            },
            PacketCodec.of(
                TagLike::write,
                TagLike::read
            )
        ));
    }

    public static <A> OptionalSerializableDataType<A> optional(SerializableDataType<A> dataType, boolean lenient) {
        return optional(dataType, lenient, warn -> {});
    }

    public static <A> OptionalSerializableDataType<A> optional(SerializableDataType<A> dataType, boolean lenient, Consumer<String> warningHandler) {
        return new OptionalSerializableDataType<>(dataType, warningHandler, lenient);
    }

}
