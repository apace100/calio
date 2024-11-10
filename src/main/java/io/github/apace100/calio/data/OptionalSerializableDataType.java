package io.github.apace100.calio.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.github.apace100.calio.util.Validatable;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.Optional;
import java.util.function.Consumer;

public class OptionalSerializableDataType<A> extends SerializableDataType<Optional<A>> {

	private final SerializableDataType<A> dataType;
	private final Consumer<String> warningHandler;

	private final boolean lenient;

	public OptionalSerializableDataType(SerializableDataType<A> dataType, Consumer<String> warningHandler, boolean lenient) {
		super(createCodec(dataType, warningHandler, lenient), createPacketCodec(dataType), dataType.isRoot());
		this.dataType = dataType;
		this.warningHandler = warningHandler;
		this.lenient = lenient;
	}

	@Override
	public void validateValue(Optional<A> value) throws Exception {

		if (value.isPresent() && value.get() instanceof Validatable validatable) {
			validatable.validate();
		}

	}

	@Override
	public OptionalSerializableDataType<A> setRoot(boolean root) {
		return new OptionalSerializableDataType<>(dataType.setRoot(root), warningHandler, lenient);
	}

	private static <A> Codec<Optional<A>> createCodec(SerializableDataType<A> dataType, Consumer<String> warningHandler, boolean lenient) {
		return new Codec<>() {

			@Override
			public <T> DataResult<Pair<Optional<A>, T>> decode(DynamicOps<T> ops, T input) {
				return dataType.codec().decode(ops, input)
					.map(optAndInput -> optAndInput.mapFirst(Optional::of))
					.mapOrElse(
						DataResult::success,
						error -> {

							if (lenient) {
								warningHandler.accept(error.message());
								return DataResult.success(Pair.of(Optional.empty(), input));
							}

							else {
								return error;
							}

						}
					);
			}

			@Override
			public <T> DataResult<T> encode(Optional<A> input, DynamicOps<T> ops, T prefix) {
				return input.map(a -> dataType.write(ops, a)).orElse(DataResult.success(prefix));
			}

		};
	}

	private static <A> PacketCodec<RegistryByteBuf, Optional<A>> createPacketCodec(SerializableDataType<A> dataType) {
		return PacketCodec.ofStatic(
			(buf, optional) -> {
				buf.writeBoolean(optional.isPresent());
				optional.ifPresent(value -> dataType.send(buf, value));
			},
			buf -> buf.readBoolean()
				? Optional.of(dataType.receive(buf))
				: Optional.empty()
		);
	}

}
