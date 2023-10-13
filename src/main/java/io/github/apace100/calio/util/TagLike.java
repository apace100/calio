package io.github.apace100.calio.util;

import com.google.gson.JsonArray;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.*;

@SuppressWarnings("unused")
public class TagLike<T> {

    private final Registry<T> registry;
    private final List<TagKey<T>> tags = new LinkedList<>();
    private final Set<T> items = new HashSet<>();

    public TagLike(Registry<T> registry) {
        this.registry = registry;
    }

    public void addTag(Identifier id) {
        addTag(TagKey.of(registry.getKey(), id));
    }

    public void add(Identifier id) {
        add(registry.get(id));
    }

    public void addTag(TagKey<T> tagKey) {
        tags.add(tagKey);
    }

    public void add(T t) {
        items.add(t);
    }

    public void addAll(TagLike<T> otherTagLike) {
        this.tags.addAll(otherTagLike.tags);
        this.items.addAll(otherTagLike.items);
    }

    public boolean contains(T t) {

        if (items.contains(t)) {
            return true;
        }

        RegistryEntry<T> entry = registry.getEntry(t);
        return tags
            .stream()
            .anyMatch(entry::isIn);

    }

    public void clear() {
        this.tags.clear();
        this.items.clear();
    }

    public void write(PacketByteBuf buf) {

        buf.writeVarInt(tags.size());
        for (TagKey<T> tagKey : tags) {
            buf.writeIdentifier(tagKey.id());
        }

        List<Identifier> ids = new LinkedList<>();
        for (T t : items) {

            Identifier id = registry.getId(t);

            if (id != null) {
                ids.add(id);
            }

        }

        buf.writeVarInt(ids.size());
        ids.forEach(buf::writeIdentifier);

    }

    public void read(PacketByteBuf buf) {

        tags.clear();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            tags.add(TagKey.of(registry.getKey(), buf.readIdentifier()));
        }

        count = buf.readVarInt();
        for (int i = 0; i < count; i++) {

            T t = registry.get(buf.readIdentifier());

            if (t != null) {
                items.add(t);
            }

        }

    }

    public void write(JsonArray array) {

        for (TagKey<T> tagKey : tags) {
            array.add("#" + tagKey.id().toString());
        }

        for(T t : items) {

            Identifier id = registry.getId(t);

            if (id != null) {
                array.add(id.toString());
            }

        }

    }

}
