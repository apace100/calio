package io.github.apace100.calio.registry;

import com.google.gson.*;
import io.github.apace100.calio.data.MultiJsonDataLoader;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataType;
import io.github.apace100.calio.data.SerializableDataTypes;
import io.github.apace100.calio.network.CalioNetworking;
import io.github.apace100.calio.util.OrderedResourceListeners;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class DataObjectRegistry<T extends DataObject<T>> {

    private static final HashMap<Identifier, DataObjectRegistry<?>> REGISTRIES = new HashMap<>();
    private static final Set<Identifier> AUTO_SYNC_SET = new HashSet<>();

    private final Identifier registryId;
    private final Class<T> objectClass;

    private final HashMap<Identifier, T> idToEntry = new HashMap<>();
    private final HashMap<T, Identifier> entryToId = new HashMap<>();

    private final String factoryFieldName;
    private final HashMap<Identifier, DataObjectFactory<T>> factoriesById = new HashMap<>();
    private final HashMap<DataObjectFactory<T>, Identifier> factoryToId = new HashMap<>();

    private SerializableDataType<T> dataType;
    private SerializableDataType<T> registryDataType;

    private final Function<JsonElement, JsonElement> jsonPreprocessor;

    private DataObjectRegistry(Identifier registryId, Class<T> objectClass, String factoryFieldName, Function<JsonElement, JsonElement> jsonPreprocessor) {
        this.registryId = registryId;
        this.objectClass = objectClass;
        this.factoryFieldName = factoryFieldName;
        this.jsonPreprocessor = jsonPreprocessor;
    }

    private DataObjectRegistry(Identifier registryId, Class<T> objectClass, String factoryFieldName, Function<JsonElement, JsonElement> jsonPreprocessor, String dataFolder, boolean useLoadingPriority, BiConsumer<Identifier, Exception> errorHandler) {
        this(registryId, objectClass, factoryFieldName, jsonPreprocessor);
        Loader loader = new Loader(dataFolder, useLoadingPriority, errorHandler);
        OrderedResourceListeners.register(loader).complete();
    }

    public Identifier getRegistryId() {
        return registryId;
    }

    public Identifier getId(T entry) {
        return entryToId.get(entry);
    }

    public DataObjectFactory<T> getFactory(Identifier id) {
        return factoriesById.get(id);
    }

    public void registerFactory(Identifier id, DataObjectFactory<T> factory) {
        factoriesById.put(id, factory);
        factoryToId.put(factory, id);
    }

    public void register(Identifier id, T entry) {
        idToEntry.put(id, entry);
        entryToId.put(entry, id);
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(idToEntry.size());
        for(Map.Entry<Identifier, T> entry : idToEntry.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            writeDataObject(buf, entry.getValue());
        }
    }

    public void writeDataObject(PacketByteBuf buf, T t) {
        DataObjectFactory<T> factory = t.getFactory();
        buf.writeIdentifier(factoryToId.get(factory));
        SerializableData.Instance data = factory.toData(t);
        factory.getData().write(buf, data);
    }

    public void receive(PacketByteBuf buf) {
        clear();
        int entryCount = buf.readInt();
        for(int i = 0; i < entryCount; i++) {
            Identifier entryId = buf.readIdentifier();
            T entry = receiveDataObject(buf);
            register(entryId, entry);
        }
    }

    public T receiveDataObject(PacketByteBuf buf) {
        Identifier factoryId = buf.readIdentifier();
        DataObjectFactory<T> factory = getFactory(factoryId);
        SerializableData.Instance data = factory.getData().read(buf);
        return factory.fromData(data);
    }

    public T readDataObject(JsonElement element) {
        if(jsonPreprocessor != null) {
            element = jsonPreprocessor.apply(element);
        }
        if(!element.isJsonObject()) {
            throw new JsonParseException(
                "Could not read data object of type \"" + registryId +
                    "\": expected a json object.");
        }
        JsonObject jsonObject = element.getAsJsonObject();
        String type = JsonHelper.getString(jsonObject, factoryFieldName);
        Identifier factoryId = null;
        try {
            factoryId = new Identifier(type);
        } catch (InvalidIdentifierException e) {
            throw new JsonParseException(
                "Could not read data object of type \"" + registryId +
                    "\": invalid factory identifier (id: \"" + factoryId + "\").", e);
        }
        if(!factoriesById.containsKey(factoryId)) {
            throw new JsonParseException(
                "Could not read data object of type \"" + registryId +
                    "\": unknown factory (id: \"" + factoryId + "\").");
        }
        DataObjectFactory<T> factory = getFactory(factoryId);
        SerializableData.Instance data = factory.getData().read(jsonObject);
        return factory.fromData(data);
    }

    public void sync(ServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeIdentifier(registryId);
        write(buf);
        ServerPlayNetworking.send(player, CalioNetworking.SYNC_DATA_OBJECT_REGISTRY, buf);
    }

    public void clear() {
        idToEntry.clear();
        entryToId.clear();
    }

    @Nullable
    public T get(Identifier id) {
        return idToEntry.get(id);
    }

    public Set<Identifier> getIds() {
        return idToEntry.keySet();
    }

    public boolean containsId(Identifier id) {
        return idToEntry.containsKey(id);
    }

    @NotNull
    public Iterator<T> iterator() {
        return idToEntry.values().iterator();
    }

    public SerializableDataType<T> dataType() {
        if(dataType == null) {
            dataType = createDataType();
        }
        return dataType;
    }

    public SerializableDataType<T> registryDataType() {
        if(registryDataType == null) {
            registryDataType = createRegistryDataType();
        }
        return registryDataType;
    }

    private SerializableDataType<T> createDataType() {
        return new SerializableDataType<>(objectClass, this::writeDataObject, this::receiveDataObject, this::readDataObject);
    }

    private SerializableDataType<T> createRegistryDataType() {
        return SerializableDataType.wrap(objectClass, SerializableDataTypes.IDENTIFIER, this::getId, this::get);
    }

    public static DataObjectRegistry<?> getRegistry(Identifier registryId) {
        return REGISTRIES.get(registryId);
    }

    public static void performAutoSync(ServerPlayerEntity player) {
        for(Identifier registryId : AUTO_SYNC_SET) {
            DataObjectRegistry<?> registry = getRegistry(registryId);
            registry.sync(player);
        }
    }

    private class Loader extends MultiJsonDataLoader implements IdentifiableResourceReloadListener {

        private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
        private static final HashMap<Identifier, Integer> LOADING_PRIORITIES = new HashMap<>();
        private final boolean useLoadingPriority;
        private final BiConsumer<Identifier, Exception> errorHandler;

        public Loader(String dataFolder, boolean useLoadingPriority, BiConsumer<Identifier, Exception> errorHandler) {
            super(GSON, dataFolder);
            this.useLoadingPriority = useLoadingPriority;
            this.errorHandler = errorHandler;
        }

        @Override
        protected void apply(Map<Identifier, List<JsonElement>> data, ResourceManager manager, Profiler profiler) {
            clear();
            LOADING_PRIORITIES.clear();
            data.forEach((id, jel) -> {
                for(JsonElement je : jel) {
                    try {
                        SerializableData.CURRENT_NAMESPACE = id.getNamespace();
                        SerializableData.CURRENT_PATH = id.getPath();
                        JsonObject jo = je.getAsJsonObject();
                        T t = readDataObject(je);
                        if(useLoadingPriority) {
                            int loadingPriority = JsonHelper.getInt(jo, "loading_priority", 0);
                            if(!containsId(id) || LOADING_PRIORITIES.get(id) < loadingPriority) {
                                LOADING_PRIORITIES.put(id, loadingPriority);
                                register(id, t);
                            }
                        } else {
                            register(id, t);
                        }
                    } catch (Exception e) {
                        if(errorHandler != null) {
                            errorHandler.accept(id, e);
                        }
                    }
                }
            });
        }

        @Override
        public Identifier getFabricId() {
            return registryId;
        }
    }

    public static class Builder<T extends DataObject<T>> {

        private final Identifier registryId;
        private final Class<T> objectClass;
        private String factoryFieldName = "type";
        private boolean autoSync = false;
        private Function<JsonElement, JsonElement> jsonPreprocessor;
        private String dataFolder;
        private boolean readFromData = false;
        private boolean useLoadingPriority;
        private BiConsumer<Identifier, Exception> errorHandler;

        public Builder(Identifier registryId, Class<T> objectClass) {
            this.registryId = registryId;
            this.objectClass = objectClass;
            if(REGISTRIES.containsKey(registryId)) {
                throw new IllegalArgumentException("A data object registry with id \"" + registryId + "\" already exists.");
            }
        }

        public Builder<T> autoSync() {
            this.autoSync = true;
            return this;
        }

        public Builder<T> jsonPreprocessor(Function<JsonElement, JsonElement> nonJsonObjectHandler) {
            this.jsonPreprocessor = nonJsonObjectHandler;
            return this;
        }

        public Builder<T> factoryFieldName(String factoryFieldName) {
            this.factoryFieldName = factoryFieldName;
            return this;
        }

        public Builder<T> readFromData(String dataFolder, boolean useLoadingPriority) {
            readFromData = true;
            this.dataFolder = dataFolder;
            this.useLoadingPriority = useLoadingPriority;
            return this;
        }

        public Builder<T> dataErrorHandler(BiConsumer<Identifier, Exception> handler) {
            this.errorHandler = handler;
            return this;
        }

        public DataObjectRegistry<T> buildAndRegister() {
            DataObjectRegistry<T> registry;
            if(readFromData) {
                registry = new DataObjectRegistry<>(registryId, objectClass, factoryFieldName, jsonPreprocessor, dataFolder, useLoadingPriority, errorHandler);
            } else {
                registry = new DataObjectRegistry<>(registryId, objectClass, factoryFieldName, jsonPreprocessor);
            }
            REGISTRIES.put(registryId, registry);
            if(autoSync) {
                AUTO_SYNC_SET.add(registryId);
            }
            return registry;
        }
    }
}
