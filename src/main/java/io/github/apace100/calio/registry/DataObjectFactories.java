package io.github.apace100.calio.registry;

import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataType;
import io.github.apace100.calio.data.SerializableDataTypes;
import io.github.apace100.calio.mixin.ItemStackAccessor;
import net.minecraft.component.ComponentChanges;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;

public final class DataObjectFactories {

	public static final DataObjectFactory<EntityAttributeModifier> ATTRIBUTE_MODIFIER = DataObjectFactory.simple(
		new SerializableData()
			.add("id", SerializableDataTypes.IDENTIFIER)
			.add("amount", SerializableDataTypes.DOUBLE)
			.add("operation", SerializableDataTypes.MODIFIER_OPERATION),
		data -> new EntityAttributeModifier(
			data.get("id"),
			data.get("amount"),
			data.get("operation")
		),
		(attributeModifier, serializableData) -> serializableData.instance()
			.set("id", attributeModifier.id())
			.set("amount", attributeModifier.value())
			.set("operation", attributeModifier.operation())
	);

	public static final DataObjectFactory<ItemStack> UNCOUNTED_ITEM_STACK = DataObjectFactory.simple(
		new SerializableData()
			.add("id", SerializableDataTypes.ITEM_ENTRY)
			.add("components", SerializableDataTypes.COMPONENT_CHANGES, ComponentChanges.EMPTY),
		data -> new ItemStack(
			data.get("id"), 1,
			data.get("components")
		),
		(stack, serializableData) -> serializableData.instance()
			.set("id", stack.getRegistryEntry())
			.set("components", stack.getComponentChanges())
	);

	public static final DataObjectFactory<ItemStack> ITEM_STACK = DataObjectFactory.simple(
		UNCOUNTED_ITEM_STACK.getSerializableData().copy()
			.add("count", SerializableDataType.boundNumber(SerializableDataTypes.INT, 1, 99), 1),
		data -> {

			ItemStack stack = UNCOUNTED_ITEM_STACK.fromData(data);
			stack.setCount(data.getInt("count"));

			return stack;

		},
		(stack, serializableData) -> UNCOUNTED_ITEM_STACK
			.toData(stack, serializableData)
			.set("count", ((ItemStackAccessor) (Object) stack).getCountOverride())
	);

}
