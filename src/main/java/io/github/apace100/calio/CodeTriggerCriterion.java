package io.github.apace100.calio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;

import java.util.Optional;

public class CodeTriggerCriterion extends AbstractCriterion<CodeTriggerCriterion.Conditions> {

    public static final CodeTriggerCriterion INSTANCE = new CodeTriggerCriterion();
    public static final Identifier ID = new Identifier("apacelib", "code_trigger");

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    public void trigger(ServerPlayerEntity player, String triggerId) {
        super.trigger(player, conditions -> conditions.matches(triggerId));
    }

    public record Conditions(Optional<LootContextPredicate> playerPredicate, Optional<String> triggerId) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codecs.createStrictOptionalFieldCodec(EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC, "player").forGetter(Conditions::playerPredicate),
            Codecs.createStrictOptionalFieldCodec(Codec.STRING, "trigger_id").forGetter(Conditions::triggerId)
        ).apply(instance, Conditions::new));

        @Override
        public Optional<LootContextPredicate> player() {
            return playerPredicate;
        }

        public boolean matches(String triggerId) {
            return this.triggerId
                .map(triggerId::equals)
                .orElse(true);
        }

    }

}
