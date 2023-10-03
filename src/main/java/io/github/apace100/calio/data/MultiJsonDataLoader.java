package io.github.apace100.calio.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resource.ResourceManager;
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
 *  <p>Like {@link net.minecraft.resource.JsonDataLoader}, except it provides a list of {@link JsonElement JsonElements} associated
 *  with an {@link Identifier}, where each element is loaded by different resource packs. This allows for overriding and merging several
 *  data files into one, similar to how tags work. There is no guarantee on the order of the resulting list, so make sure to implement
 *  some kind of "priority" system.</p>
 *
 *  <p>This is now <b>deprecated</b> in favor of using {@link IdentifiableMultiJsonDataLoader}</p>
 */
@Deprecated
public abstract class MultiJsonDataLoader extends SinglePreparationResourceReloader<Map<Identifier, List<JsonElement>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiJsonDataLoader.class);
    private static final Map<String, JsonFormat> VALID_EXTENSIONS = Util.make(new HashMap<>(), map -> {
        map.put(".json", JsonFormat.JSON);
        map.put(".json5", JsonFormat.JSON5);
        map.put(".jsonc", JsonFormat.JSONC);
    });

    private final Gson gson;
    private final String directoryName;

    public MultiJsonDataLoader(Gson gson, String directoryName) {
        this.gson = gson;
        this.directoryName = directoryName;
    }

    @Override
    protected Map<Identifier, List<JsonElement>> prepare(ResourceManager manager, Profiler profiler) {

        Map<Identifier, List<JsonElement>> result = new HashMap<>();
        manager.findResources(directoryName, this::hasValidExtension).keySet().forEach(fileId -> {

            Identifier id = trim(fileId);
            String fileExtension = "." + FilenameUtils.getExtension(fileId.getPath());

            JsonFormat jsonFormat = VALID_EXTENSIONS.get(fileExtension);
            manager.getAllResources(fileId).forEach(resource -> {

                String packName = resource.getResourcePackName();
                try (BufferedReader resourceReader = resource.getReader()) {

                    if (jsonFormat == null) {
                        throw new JsonSyntaxException("The file extension \"" + fileExtension + "\" is not supported");
                    }

                    GsonReader gsonReader = new GsonReader(JsonReader.create(resourceReader, jsonFormat));
                    JsonElement jsonElement = gson.fromJson(gsonReader, JsonElement.class);

                    result.computeIfAbsent(id, k -> new LinkedList<>())
                        .add(jsonElement);

                } catch (Exception e) {
                    String filePath = packName + "/.../" + fileId.getNamespace() + "/" + fileId.getPath();
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
        return VALID_EXTENSIONS.keySet()
            .stream()
            .anyMatch(suffix -> id.getPath().endsWith(suffix));
    }

}
