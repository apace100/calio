package io.github.apace100.calio.util;

import com.google.common.collect.Lists;
import io.github.apace100.calio.Calio;
import net.minecraft.tag.Tag;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;

import java.util.List;

public class IdentifiedTag<T> implements Tag.Identified<T> {

    private final RegistryKey<? extends Registry<T>> registryKey;
    private Tag<T> containedTag;
    private Identifier id;

    public IdentifiedTag(RegistryKey<? extends Registry<T>> registryKey, Identifier identifier) {
        this.registryKey = registryKey;
        this.id = identifier;
    }

    private void updateContainedTag() {
        try {
            this.containedTag = Calio.getTagManager().getTag(registryKey, id, id -> new RuntimeException("Could not load tag: " + id.toString()));
        } catch (RuntimeException e) {
            // Fail silently. This sometimes happens one frame at world load.
        }
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public boolean contains(T entry) {
        if(containedTag == null) {
            updateContainedTag();
            if(containedTag == null) {
                return false;
            }
        }
        return containedTag.contains(entry);
    }

    @Override
    public List<T> values() {
        if(containedTag == null) {
            updateContainedTag();
            if(containedTag == null) {
                return Lists.newArrayList();
            }
        }
        return containedTag.values();
    }
}
