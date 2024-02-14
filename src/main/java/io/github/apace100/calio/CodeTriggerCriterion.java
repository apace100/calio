package io.github.apace100.calio;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Optional;

public class CodeTriggerCriterion extends AbstractCriterion<CodeTriggerCriterion.Conditions> {

    public static final CodeTriggerCriterion INSTANCE = new CodeTriggerCriterion();

    public static final Identifier ID = new Identifier("apacelib", "code_trigger");

    public Identifier getId() {
        return ID;
    }

    public Conditions conditionsFromJson(JsonObject jsonObject, Optional<LootContextPredicate> extended, AdvancementEntityPredicateDeserializer advancementEntityPredicateDeserializer) {
        String triggerId = "empty";
        if(jsonObject.has("trigger_id")) {
            triggerId = jsonObject.get("trigger_id").getAsString();
        }
        return new CodeTriggerCriterion.Conditions(extended, triggerId);
    }

    public void trigger(ServerPlayerEntity player, String triggeredId) {
        this.trigger(player, (conditions) -> conditions.matches(triggeredId));
    }

    public static class Conditions extends AbstractCriterionConditions {
        private final String triggerId;

        public Conditions(Optional<LootContextPredicate> playerPredicate, String triggerId) {
            super(playerPredicate);
            this.triggerId = triggerId;
        }

        public static CodeTriggerCriterion.Conditions trigger(String triggerId) {
            return new CodeTriggerCriterion.Conditions(Optional.empty(), triggerId);
        }

        public boolean matches(String triggered) {
            return this.triggerId.equals(triggered);
        }

        public JsonObject toJson() {
            JsonObject jsonObject = super.toJson();
            jsonObject.add("trigger_id", new JsonPrimitive(triggerId));
            return jsonObject;
        }
    }
}
