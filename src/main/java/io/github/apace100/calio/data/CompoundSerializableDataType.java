package io.github.apace100.calio.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.function.Function;

public class CompoundSerializableDataType<T> extends SerializableDataType<T> {

    private final SerializableData serializableData;

    private final Function<SerializableData, MapCodec<T>> mapCodecGetter;
    private final Function<SerializableData, PacketCodec<RegistryByteBuf, T>> packetCodecGetter;

    private final MapCodec<T> mapCodec;
    private final PacketCodec<RegistryByteBuf, T> packetCodec;

    public CompoundSerializableDataType(SerializableData serializableData, Function<SerializableData, MapCodec<T>> mapCodecGetter, Function<SerializableData, PacketCodec<RegistryByteBuf, T>> packetCodecGetter) {
        super(null, null, serializableData.isRoot());

        this.serializableData = serializableData;

        this.mapCodecGetter = mapCodecGetter;
        this.packetCodecGetter = packetCodecGetter;

        this.mapCodec = this.mapCodecGetter.apply(this.serializableData);
        this.packetCodec = this.packetCodecGetter.apply(this.serializableData);

    }

    @Override
    public Codec<T> codec() {
        return mapCodec().codec();
    }

    @Override
    public PacketCodec<RegistryByteBuf, T> packetCodec() {
        return packetCodec;
    }

    @Override
    public <S> CompoundSerializableDataType<S> xmap(Function<? super T, ? extends S> to, Function<? super S, ? extends T> from) {
        return new CompoundSerializableDataType<>(
            serializableData(),
            _serializableData -> mapCodecGetter
                .apply(_serializableData)
                .xmap(to, from),
            _serializableData -> packetCodecGetter
                .apply(_serializableData)
                .xmap(to, from)
        );
    }

    @Override
    public <S> CompoundSerializableDataType<S> comapFlatMap(Function<? super T, ? extends DataResult<? extends S>> to, Function<? super S, ? extends T> from) {

        Function<? super T, ? extends S> toUnwrapped = t -> to.apply(t).getOrThrow();
        Function<? super S, ? extends DataResult<? extends T>> fromWrapped = s -> DataResult.success(from.apply(s));

        return new CompoundSerializableDataType<>(
            serializableData(),
            _serializableData -> mapCodecGetter
                .apply(_serializableData)
                .flatXmap(to, fromWrapped),
            _serializableData -> packetCodecGetter
                .apply(_serializableData)
                .xmap(toUnwrapped, from)
        );

    }

    @Override
    public <S> CompoundSerializableDataType<S> flatComapMap(Function<? super T, ? extends S> to, Function<? super S, ? extends DataResult<? extends T>> from) {

        Function<? super T, ? extends DataResult<? extends S>> toWrapped = t -> DataResult.success(to.apply(t));
        Function<? super S, ? extends T> fromUnwrapped = s -> from.apply(s).getOrThrow();

        return new CompoundSerializableDataType<>(
            serializableData(),
            _serializableData -> mapCodecGetter
                .apply(_serializableData)
                .flatXmap(toWrapped, from),
            _serializableData -> packetCodecGetter
                .apply(_serializableData)
                .xmap(to, fromUnwrapped)
        );

    }

    @Override
    public <S> CompoundSerializableDataType<S> flatXmap(Function<? super T, ? extends DataResult<? extends S>> to, Function<? super S, ? extends DataResult<? extends T>> from) {

        Function<? super T, ? extends S> toUnwrapped = t -> to.apply(t).getOrThrow();
        Function<? super S, ? extends T> fromUnwrapped = s -> from.apply(s).getOrThrow();

        return new CompoundSerializableDataType<>(
            serializableData(),
            _serializableData -> mapCodecGetter
                .apply(_serializableData)
                .flatXmap(to, from),
            _serializableData -> packetCodecGetter
                .apply(_serializableData)
                .xmap(toUnwrapped, fromUnwrapped)
        );

    }

    @Override
    public CompoundSerializableDataType<T> validate(Function<T, DataResult<T>> checker) {
        return flatXmap(checker, checker);
    }

    @Override
    public CompoundSerializableDataType<T> setRoot(boolean root) {
        return new CompoundSerializableDataType<>(serializableData().setRoot(root), this.mapCodecGetter, this.packetCodecGetter);
    }

    public SerializableData serializableData() {
        return serializableData;
    }

    public MapCodec<T> mapCodec() {
        return mapCodec;
    }

}
