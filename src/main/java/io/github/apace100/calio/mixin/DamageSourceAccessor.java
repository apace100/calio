package io.github.apace100.calio.mixin;

import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DamageSource.class)
public interface DamageSourceAccessor {

    @Invoker("<init>")
    static DamageSource createDamageSource(String name) {
        throw new RuntimeException("Evil invoker exception! >:)");
    }

    @Invoker
    DamageSource callSetBypassesArmor();

    @Invoker
    DamageSource callSetOutOfWorld();

    @Invoker
    DamageSource callSetUnblockable();

    @Invoker
    DamageSource callSetFire();

    @Invoker
    DamageSource callSetUsesMagic();

    @Invoker
    DamageSource callSetProjectile();

    @Invoker
    DamageSource callSetExplosive();
}
