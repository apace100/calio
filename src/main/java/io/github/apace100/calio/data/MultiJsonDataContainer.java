package io.github.apace100.calio.data;

import com.google.gson.JsonElement;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.List;

public class MultiJsonDataContainer extends LinkedHashMap<Identifier, LinkedHashMap<String, List<JsonElement>>> {

    public void forEach(Processor processor) {
        super.forEach((id, packedJsonData) ->
            packedJsonData.forEach((packName, jsonElements) ->
                jsonElements.forEach(jsonElement -> processor.process(packName, id, jsonElement))));
    }

    @FunctionalInterface
    public interface Processor {
        void process(String packName, Identifier id, JsonElement jsonElement);
    }

}
