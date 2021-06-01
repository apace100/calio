package io.github.apace100.calio;

import net.minecraft.entity.damage.DamageSource;

public class SimpleDamageSource extends DamageSource {

    public SimpleDamageSource(String name) {
        super(name);
    }

    public SimpleDamageSource setBypassesArmor() {
        super.setBypassesArmor();
        return this;
    }

    public SimpleDamageSource setFire() {
        super.setFire();
        return this;
    }

    public SimpleDamageSource setExplosive() {
        super.setExplosive();
        return this;
    }

    public SimpleDamageSource setUsesMagic() {
        super.setUsesMagic();
        return this;
    }

    public SimpleDamageSource setUnblockable() {
        super.setUnblockable();
        return this;
    }

    public SimpleDamageSource setFallingBlock() {
        super.setFallingBlock();
        return this;
    }

    public SimpleDamageSource setFromFalling() {
        super.setFromFalling();
        return this;
    }

    public SimpleDamageSource setOutOfWorld() {
        super.setOutOfWorld();
        return this;
    }

    public SimpleDamageSource setProjectile() {
        super.setProjectile();
        return this;
    }

    public SimpleDamageSource setScaledWithDifficulty() {
        super.setScaledWithDifficulty();
        return this;
    }
}
