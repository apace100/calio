package io.github.apace100.calio.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.profiler.Profiler;
import org.apache.commons.io.FilenameUtils;
import org.quiltmc.parsers.json.JsonFormat;
import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.parsers.json.gson.GsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.*;

/**
 *  Similar to {@link MultiJsonDataLoader}, except it provides a list of {@link MultiJsonDataContainer} that contains a map
 *  of {@link Identifier} and a {@link List} of {@link JsonElement JsonElements} with a {@link String} that identifies the
 *  data/resource pack the JSON data is from.
 */
public abstract class IdentifiableMultiJsonDataLoader extends SinglePreparationResourceReloader<MultiJsonDataContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentifiableMultiJsonDataLoader.class);
    private static final Map<String, JsonFormat> VALID_EXTENSIONS = Util.make(new HashMap<>(), map -> {
        map.put(".json", JsonFormat.JSON);
        map.put(".json5", JsonFormat.JSON5);
        map.put(".jsonc", JsonFormat.JSONC);
    });

    private final ResourceType resourceType;
    private final String directoryName;
    private final Gson gson;

    public IdentifiableMultiJsonDataLoader(Gson gson, String directoryName, ResourceType resourceType) {
        this.gson = gson;
        this.directoryName = directoryName;
        this.resourceType = resourceType;
    }

    @Override
    protected MultiJsonDataContainer prepare(ResourceManager manager, Profiler profiler) {

        MultiJsonDataContainer result = new MultiJsonDataContainer();
        manager.findResources(directoryName, this::hasValidExtension).keySet().forEach(fileId -> {

            Identifier id = this.trim(fileId);
            String fileExtension = "." + FilenameUtils.getExtension(fileId.getPath());

            JsonFormat jsonFormat = VALID_EXTENSIONS.get(fileExtension);
            manager.getAllResources(fileId).forEach(resource -> {

                String packName = resource.getResourcePackName();
                try (BufferedReader resourceReader = resource.getReader()) {

                    GsonReader gsonReader = new GsonReader(JsonReader.create(resourceReader, jsonFormat));
                    JsonElement jsonElement = gson.fromJson(gsonReader, JsonElement.class);

                    if (jsonElement == null) {
                        throw new JsonParseException("JSON cannot be null! Caused by either the file being empty or a syntax error when being parsed by " + gsonReader);
                    }

                    result
                        .computeIfAbsent(id, k -> new LinkedHashMap<>())
                        .computeIfAbsent(packName, k -> new LinkedList<>())
                        .add(jsonElement);

                } catch (Exception e) {
                    String filePath = packName + "/" + resourceType.getDirectory() + "/" + fileId.getNamespace() + "/" + fileId.getPath();
                    LOGGER.error("Couldn't parse data file \"{}\" from \"{}\": {}", id, filePath, e.getMessage());
                }

            });

        });

        return result;

    }

    protected Identifier trim(Identifier fileId) {
        String path = FilenameUtils.removeExtension(fileId.getPath()).substring(directoryName.length() + 1);
        return new Identifier(fileId.getNamespace(), path);
    }

    protected boolean hasValidExtension(Identifier fileId) {
        return VALID_EXTENSIONS.keySet()
            .stream()
            .anyMatch(suffix -> fileId.getPath().endsWith(suffix));
    }

}
