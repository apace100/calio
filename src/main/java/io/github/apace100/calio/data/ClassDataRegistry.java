package io.github.apace100.calio.data;

import io.github.apace100.calio.ClassUtil;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class ClassDataRegistry<T> {

    private static final HashMap<Class<?>, ClassDataRegistry<?>> REGISTRIES = new HashMap<>();

    private final Class<T> clazz;
    private SerializableDataType<Class<? extends T>> dataType;
    private SerializableDataType<List<Class<? extends T>>> listDataType;

    private final HashMap<String, Class<? extends T>> directMappings = new HashMap<>();
    private final List<String> packages = new LinkedList<>();
    private final String classSuffix;

    protected ClassDataRegistry(Class<T> cls, String classSuffix) {
        this.clazz = cls;
        this.classSuffix = classSuffix;
    }

    public void addMapping(String className, Class<?> cls) {
        directMappings.put(className, ClassUtil.castClass(cls));
    }

    public void addPackage(String packagePath) {
        packages.add(packagePath);
    }

    public SerializableDataType<Class<? extends T>> getDataType() {
        if(dataType == null) {
            dataType = createDataType();
        }
        return dataType;
    }

    public SerializableDataType<List<Class<? extends T>>> getListDataType() {
        if(listDataType == null) {
            listDataType = SerializableDataType.list(getDataType());
        }
        return listDataType;
    }

    public Optional<Class<? extends T>> mapStringToClass(String str) {
        return mapStringToClass(str, new StringBuilder());
    }

    public Optional<Class<? extends T>> mapStringToClass(String str, StringBuilder failedClasses) {
        if(directMappings.containsKey(str)) {
            return Optional.of(directMappings.get(str));
        }
        try {
            return Optional.of((Class<? extends T>)Class.forName(str));
        } catch(Exception e0) {
            failedClasses.append(str);
        }
        for(String pkg : packages) {
            String full = pkg + "." + str;
            try {
                return Optional.of((Class<? extends T>)Class.forName(full));
            } catch(Exception e1) {
                failedClasses.append(", ");
                failedClasses.append(full);
            }
            full = pkg + "." + transformJsonToClass(str, classSuffix);
            try {
                return Optional.of((Class<? extends T>)Class.forName(full));
            } catch(Exception e2) {
                failedClasses.append(", ");
                failedClasses.append(full);
            }
        }
        return Optional.empty();
    }

    private SerializableDataType<Class<? extends T>> createDataType() {
        return SerializableDataType.wrap(ClassUtil.castClass(Class.class), SerializableDataTypes.STRING,
            Class::getName, str -> {
                StringBuilder failedClasses = new StringBuilder();
                Optional<Class<? extends T>> optionalClass = mapStringToClass(str, failedClasses);
                if(optionalClass.isPresent()) {
                    return optionalClass.get();
                }
                throw new RuntimeException("Specified class does not exist: \"" + str + "\". Looked at [" + failedClasses + "]");
            });
    }

    public static Optional<ClassDataRegistry> get(Class<?> cls) {
        if(REGISTRIES.containsKey(cls)) {
            return Optional.of(REGISTRIES.get(cls));
        } else {
            return Optional.empty();
        }
    }

    public static <T> ClassDataRegistry<T> getOrCreate(Class<T> cls, String classSuffix) {
        if(REGISTRIES.containsKey(cls)) {
            return (ClassDataRegistry<T>) REGISTRIES.get(cls);
        } else {
            ClassDataRegistry<T> cdr = new ClassDataRegistry<>(cls, classSuffix);
            REGISTRIES.put(cls, cdr);
            return cdr;
        }
    }

    private static String transformJsonToClass(String jsonName, String classSuffix) {
        StringBuilder builder = new StringBuilder();
        boolean caps = true;
        int capsOffset = 'A' - 'a';
        for(char c : jsonName.toCharArray()) {
            if(c == '_') {
                caps = true;
                continue;
            }
            if(caps) {
                builder.append(Character.toUpperCase(c));
                caps = false;
            } else {
                builder.append(c);
            }
        }
        builder.append(classSuffix);
        return builder.toString();
    }
}

