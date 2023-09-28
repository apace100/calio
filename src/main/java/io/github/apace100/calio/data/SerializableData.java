package io.github.apace100.calio.data;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("unused")
public class SerializableData {

    // Should be set to the current namespace of the file that is being read. Allows using * in identifiers.
    public static String CURRENT_NAMESPACE;

    // Should be set to the current path of the file that is being read. Allows using * in identifiers.
    public static String CURRENT_PATH;

    private final LinkedHashMap<String, Field<?>> dataFields = new LinkedHashMap<>();

    public SerializableData add(String name, SerializableDataType<?> type) {
        dataFields.put(name, new Field<>(type));
        return this;
    }

    public <T> SerializableData add(String name, SerializableDataType<T> type, T defaultValue) {
        dataFields.put(name, new Field<>(type, defaultValue));
        return this;
    }

    public <T> SerializableData addFunctionedDefault(String name, SerializableDataType<T> type, Function<Instance, T> defaultFunction) {
        dataFields.put(name, new Field<>(type, defaultFunction));
        return this;
    }

    public void write(PacketByteBuf buffer, Instance instance) {
        dataFields.forEach((name, field) -> {
            try {

                boolean isPresent = instance.get(name) != null;
                if (field.hasDefault && field.defaultValue == null) {
                    buffer.writeBoolean(isPresent);
                }

                if (isPresent) {
                    field.dataType.send(buffer, instance.get(name));
                }

            } catch(DataException e) {
                throw e.prepend(name);
            } catch(Exception e) {
                throw new DataException(DataException.Phase.WRITING, name, e);
            }
        });
    }

    public JsonObject write(Instance instance) {

        JsonObject jsonObject = new JsonObject();
        dataFields.forEach((name, field) -> instance.ifPresent(name, o -> jsonObject.add(name, field.dataType.write(o))));

        return jsonObject;

    }

    public Instance read(PacketByteBuf buffer) {

        Instance instance = new Instance();
        dataFields.forEach((name, field) -> {
            try {

                boolean isPresent = true;
                if (field.hasDefault && field.defaultValue == null) {
                    isPresent = buffer.readBoolean();
                }

                instance.set(name, isPresent ? field.dataType.receive(buffer) : null);

            } catch (DataException e) {
                throw e.prepend(name);
            } catch (Exception e) {
                throw new DataException(DataException.Phase.RECEIVING, name, e);
            }
        });

        return instance;

    }

    public Instance read(JsonObject jsonObject) {

        Instance instance = new Instance();
        dataFields.forEach((name, field) -> {
            try {

                if (jsonObject.has(name)) {
                    instance.set(name, field.dataType.read(jsonObject.get(name)));
                } else if (field.hasDefault()) {
                    instance.set(name, field.getDefault(instance));
                } else {
                    throw new JsonSyntaxException("JSON requires field: " + name);
                }

            } catch (DataException e) {
                throw e.prepend(name);
            } catch (Exception e) {
                throw new DataException(DataException.Phase.READING, name, e);
            }
        });

        return instance;

    }

    public SerializableData copy() {

        SerializableData copy = new SerializableData();
        copy.dataFields.putAll(dataFields);

        return copy;

    }

    public Iterable<String> getFieldNames() {
        return ImmutableSet.copyOf(dataFields.keySet());
    }

    public Field<?> getField(String fieldName) {
        if(!dataFields.containsKey(fieldName)) {
            throw new IllegalArgumentException("SerializableData contains no field with name \"" + fieldName + "\".");
        } else {
            return dataFields.get(fieldName);
        }
    }

    public class Instance {

        private final HashMap<String, Object> data = new HashMap<>();

        public boolean isPresent(String name) {

            if (!dataFields.containsKey(name)) {
                return data.containsKey(name);
            }

            Field<?> field = dataFields.get(name);
            if (field.hasDefault && field.defaultValue == null) {
                return get(name) != null;
            }

            return false;

        }

        public <T> void ifPresent(String name, Consumer<T> consumer) {
            if (isPresent(name)) {
                consumer.accept(get(name));
            }
        }

        public void set(String name, Object value) {
            this.data.put(name, value);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String name) {

            if(!data.containsKey(name)) {
                throw new RuntimeException("Tried to get field \"" + name + "\" from data, which did not exist.");
            }

            return (T) data.get(name);
        }

        public int getInt(String name) {
            return get(name);
        }

        public boolean getBoolean(String name) {
            return get(name);
        }

        public float getFloat(String name) {
            return get(name);
        }

        public double getDouble(String name) {
            return get(name);
        }

        public String getString(String name) {
            return get(name);
        }

        public Identifier getId(String name) {
            return get(name);
        }

        public EntityAttributeModifier getModifier(String name) {
            return get(name);
        }

    }

    public static class Field<T> {
        private final SerializableDataType<T> dataType;
        private final T defaultValue;
        private final Function<Instance, T> defaultFunction;
        private final boolean hasDefault;
        private final boolean hasDefaultFunction;

        public Field(SerializableDataType<T> dataType) {
            this.dataType = dataType;
            this.defaultValue = null;
            this.defaultFunction = null;
            this.hasDefault = false;
            this.hasDefaultFunction = false;
        }

        public Field(SerializableDataType<T> dataType, T defaultValue) {
            this.dataType = dataType;
            this.defaultValue = defaultValue;
            this.defaultFunction = null;
            this.hasDefault = true;
            this.hasDefaultFunction = false;
        }

        public Field(SerializableDataType<T> dataType, Function<Instance, T> defaultFunction) {
            this.dataType = dataType;
            this.defaultValue = null;
            this.defaultFunction = defaultFunction;
            this.hasDefault = false;
            this.hasDefaultFunction = true;
        }

        public boolean hasDefault() {
            return hasDefault || hasDefaultFunction;
        }

        public T getDefault(Instance dataInstance) {
            if (hasDefaultFunction && defaultFunction != null) {
                return defaultFunction.apply(dataInstance);
            } else if (hasDefault) {
                return defaultValue;
            } else {
                throw new IllegalStateException("Tried to access default value of serializable data entry, when no default was provided.");
            }
        }

        public SerializableDataType<T> getDataType() {
            return dataType;
        }

    }

}