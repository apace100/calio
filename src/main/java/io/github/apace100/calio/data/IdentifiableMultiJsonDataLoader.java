package io.github.apace100.calio.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 *  Similar to {@link MultiJsonDataLoader}, except it provides a list of {@link MultiJsonDataContainer} that contains a map
 *  of {@link Identifier} and a {@link List} of {@link JsonElement JsonElements} with a {@link String} that determines the
 *  data/resource pack the data is from.
 */
@SuppressWarnings("unused")
public abstract class IdentifiableMultiJsonDataLoader extends SinglePreparationResourceReloader<List<MultiJsonDataContainer>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentifiableMultiJsonDataLoader.class);
    private static final Set<String> VALID_EXTENSIONS = Set.of(".json");

    private final ResourceType resourceType;
    private final String directoryName;
    private final Gson gson;

    public IdentifiableMultiJsonDataLoader(Gson gson, String directoryName, ResourceType resourceType) {
        this.gson = gson;
        this.directoryName = directoryName;
        this.resourceType = resourceType;
    }

    @Override
    protected List<MultiJsonDataContainer> prepare(ResourceManager manager, Profiler profiler) {

        List<MultiJsonDataContainer> result = new LinkedList<>();
        manager.findResources(directoryName, this::hasValidExtension).keySet().forEach(fileId -> {

            Identifier id = trim(fileId);
            manager.getAllResources(fileId).forEach(resource -> {

                String packName = resource.getResourcePackName();
                try (BufferedReader resourceReader = resource.getReader()) {

                    JsonElement jsonElement = JsonHelper.deserialize(gson, resourceReader, JsonElement.class);

                    MultiJsonDataContainer dataContainer = new MultiJsonDataContainer(packName);
                    dataContainer.put(id, jsonElement);

                    if (!result.contains(dataContainer)) {
                        result.add(dataContainer);
                    } else {
                        int index = result.indexOf(dataContainer);
                        result.get(index).put(id, jsonElement);
                    }

                } catch (Exception e) {
                    String filePath = packName + "/" + resourceType.getDirectory() + "/" + fileId.getNamespace() + "/" + fileId.getPath();
                    LOGGER.error("Couldn't parse data file \"{}\" from \"{}\": {}", id, filePath, e.getMessage());
                }

            });

        });

        return result;

    }

    private Identifier trim(Identifier id) {
        String path = FilenameUtils.removeExtension(id.getPath()).substring(directoryName.length() + 1);
        return new Identifier(id.getNamespace(), path);
    }

    private boolean hasValidExtension(Identifier id) {
        return VALID_EXTENSIONS
            .stream()
            .anyMatch(suffix -> id.getPath().endsWith(suffix));
    }

}
