package io.github.apace100.calio.data;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.function.Function;
import java.util.function.Supplier;

public class RecursiveSerializableDataType<T> extends SerializableDataType<T> {

    private final Supplier<SerializableDataType<T>> dataType;
    private final Function<SerializableDataType<T>, SerializableDataType<T>> wrapper;

    public RecursiveSerializableDataType(Function<SerializableDataType<T>, SerializableDataType<T>> wrapper, boolean root) {
        super(null, null, root);
        this.dataType = Suppliers.memoize(() -> wrapper.apply(this));
        this.wrapper = wrapper;
    }

    public RecursiveSerializableDataType(Function<SerializableDataType<T>, SerializableDataType<T>> wrapper) {
        this(wrapper, true);
    }

    @Override
    public Codec<T> codec() {
        return this.dataType.get().codec();
    }

    @Override
    public PacketCodec<RegistryByteBuf, T> packetCodec() {
        return dataType.get().packetCodec();
    }

    @Override
    public <S> RecursiveSerializableDataType<S> xmap(Function<? super T, ? extends S> to, Function<? super S, ? extends T> from) {
        return new RecursiveSerializableDataType<>(dt -> wrapper.apply(this).xmap(to, from), this.isRoot());
    }

    @Override
    public <S> RecursiveSerializableDataType<S> comapFlatMap(Function<? super T, ? extends DataResult<? extends S>> to, Function<? super S, ? extends T> from) {
        return new RecursiveSerializableDataType<>(dt -> wrapper.apply(this).comapFlatMap(to, from), this.isRoot());
    }

    @Override
    public <S> RecursiveSerializableDataType<S> flatComapMap(Function<? super T, ? extends S> to, Function<? super S, ? extends DataResult<? extends T>> from) {
        return new RecursiveSerializableDataType<>(dt -> wrapper.apply(this).flatComapMap(to, from), this.isRoot());
    }

    @Override
    public <S> RecursiveSerializableDataType<S> flatXmap(Function<? super T, ? extends DataResult<? extends S>> to, Function<? super S, ? extends DataResult<? extends T>> from) {
        return new RecursiveSerializableDataType<>(dt -> wrapper.apply(this).flatXmap(to, from), this.isRoot());
    }

    @Override
    public void validateValue(T value) throws Exception {
        dataType.get().validateValue(value);
    }

    @Override
    public RecursiveSerializableDataType<T> validate(Function<T, DataResult<T>> checker) {
        return flatXmap(checker, checker);
    }

    @Override
    public RecursiveSerializableDataType<T> setRoot(boolean root) {
        return new RecursiveSerializableDataType<>(dt -> wrapper.apply(dt).setRoot(root), root);
    }

    @SuppressWarnings("unchecked")
    public <DT extends SerializableDataType<T>> DT cast() {
        return (DT) dataType.get();
    }

}
