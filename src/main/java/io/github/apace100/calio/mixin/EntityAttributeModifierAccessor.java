package io.github.apace100.calio.mixin;

import net.minecraft.entity.attribute.EntityAttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityAttributeModifier.class)
public interface EntityAttributeModifierAccessor {

    @Accessor
    String getName();

}
