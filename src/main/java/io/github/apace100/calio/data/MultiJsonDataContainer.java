package io.github.apace100.calio.data;

import com.google.gson.JsonElement;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class MultiJsonDataContainer {

    private final Map<Identifier, List<JsonElement>> jsonData = new HashMap<>();
    private final String packName;

    public MultiJsonDataContainer(String packName) {
        this.packName = packName;
    }

    protected void putAll(Map<Identifier, List<JsonElement>> jsonData) {
        this.jsonData.putAll(jsonData);
    }

    protected void put(Identifier id, JsonElement jsonElement) {
        this.jsonData.computeIfAbsent(id, k -> new LinkedList<>()).add(jsonElement);
    }

    public List<JsonElement> get(Identifier id) {
        return this.jsonData.get(id);
    }

    public void forEach(Processor processor) {
        this.jsonData.forEach((id, jsonElements) -> jsonElements.forEach(jsonElement -> processor.process(packName, id, jsonElement)));
    }

    public int size() {
        return this.jsonData.size();
    }

    @Override
    public int hashCode() {
        return packName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof MultiJsonDataContainer other && this.packName.equals(other.packName));
    }

    @FunctionalInterface
    public interface Processor {
        void process(String packName, Identifier fileId, JsonElement jsonElement);
    }

}
