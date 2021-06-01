package io.github.apace100.calio;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class CodeTriggerCriterion extends AbstractCriterion<CodeTriggerCriterion.Conditions> {

    public static final CodeTriggerCriterion INSTANCE = new CodeTriggerCriterion();

    public static final Identifier ID = new Identifier("apacelib", "code_trigger");

    public Identifier getId() {
        return ID;
    }

    public Conditions conditionsFromJson(JsonObject jsonObject, EntityPredicate.Extended extended, AdvancementEntityPredicateDeserializer advancementEntityPredicateDeserializer) {
        String triggerId = "empty";
        if(jsonObject.has("trigger_id")) {
            triggerId = jsonObject.get("trigger_id").getAsString();
        }
        return new CodeTriggerCriterion.Conditions(extended, triggerId);
    }

    public void trigger(ServerPlayerEntity player, String triggeredId) {
        this.test(player, (conditions) -> conditions.matches(triggeredId));
    }

    public static class Conditions extends AbstractCriterionConditions {
        private final String triggerId;

        public Conditions(EntityPredicate.Extended player, String triggerId) {
            super(CodeTriggerCriterion.ID, player);
            this.triggerId = triggerId;
        }

        public static CodeTriggerCriterion.Conditions trigger(String triggerId) {
            return new CodeTriggerCriterion.Conditions(EntityPredicate.Extended.EMPTY, triggerId);
        }

        public boolean matches(String triggered) {
            return this.triggerId.equals(triggered);
        }

        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            JsonObject jsonObject = super.toJson(predicateSerializer);
            jsonObject.add("trigger_id", new JsonPrimitive(triggerId));
            return jsonObject;
        }
    }
}
