package io.github.apace100.calio.data;

import com.google.gson.JsonElement;
import net.minecraft.util.Identifier;

import java.util.*;

@SuppressWarnings("unused")
public class MultiJsonDataContainer {

    private final Map<Identifier, List<JsonElement>> jsonData;
    private final String packName;

    public MultiJsonDataContainer(String packName) {
        this.jsonData = new LinkedHashMap<>();
        this.packName = packName;
    }

    protected void putAll(Map<Identifier, List<JsonElement>> jsonData) {
        this.jsonData.putAll(jsonData);
    }

    protected void put(Identifier id, JsonElement jsonElement) {
        this.jsonData.computeIfAbsent(id, k -> new LinkedList<>()).add(jsonElement);
    }

    public Map<Identifier, List<JsonElement>> getAll() {
        return jsonData;
    }

    public List<JsonElement> get(Identifier id) {
        return this.jsonData.get(id);
    }

    public void forEach(Processor processor) {
        this.jsonData.forEach((id, jsonElements) -> jsonElements.forEach(jsonElement -> processor.process(packName, id, jsonElement)));
    }

    public boolean contains(Identifier id) {
        return jsonData.containsKey(id);
    }

    public boolean contains(JsonElement jsonElement) {
        return jsonData.values()
            .stream()
            .anyMatch(jsonElements -> jsonElements.contains(jsonElement));
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
