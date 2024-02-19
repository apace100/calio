package io.github.apace100.calio.mixin;

import io.github.apace100.calio.Calio;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.tag.TagManagerLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(TagManagerLoader.class)
public abstract class TagManagerLoaderMixin {

    @Shadow private List<TagManagerLoader.RegistryTags<?>> registryTags;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "method_40098", at = @At("RETURN"))
    private void calio$cacheRegistryTags(List<?> list, Void void_, CallbackInfo ci) {

        Map<TagKey<?>, Collection<RegistryEntry<?>>> registryTagsCache = new HashMap<>();
        this.registryTags.forEach(entry -> entry.tags().forEach((id, entries) ->
            registryTagsCache.put(TagKey.of(entry.key(), id), (Collection) entries))
        );

        Calio.REGISTRY_TAGS.set(registryTagsCache);

    }

}
