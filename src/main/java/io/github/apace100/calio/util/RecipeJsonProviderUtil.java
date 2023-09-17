package io.github.apace100.calio.util;

import com.google.common.reflect.ClassPath;
import io.github.apace100.calio.Calio;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.item.ItemConvertible;
import net.minecraft.recipe.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiFunction;

// TODO: Finish implementation of this. I don't know if I'm doing it right.
public class RecipeJsonProviderUtil {

    public static final Set<Class<RecipeJsonProvider>> HAS_OVERRIDE = new HashSet<>();
    public static final Set<BiFunction<Recipe<?>, DynamicRegistryManager, RecipeJsonProvider>> FUNCTIONS = new HashSet<>();
    public static final Map<RecipeSerializer<?>, BiFunction<Recipe<?>, DynamicRegistryManager, RecipeJsonProvider>> OVERRIDES = new HashMap<>();

    public static RecipeJsonProvider findBestMatch(Recipe<?> recipe, DynamicRegistryManager registryManager) {
        if (OVERRIDES.containsKey(recipe.getSerializer())) {
            return OVERRIDES.get(recipe.getSerializer()).apply(recipe, registryManager);
        }
        RecipeJsonProvider provider = null;
        for (BiFunction<Recipe<?>, DynamicRegistryManager, RecipeJsonProvider> function : FUNCTIONS) {

        }

        if (provider == null) {
            Calio.LOGGER.warn("Could not find suitable RecipeJsonProvider for writing recipe. (skipping).");
        }

        return provider;
    }

    public static void populateFunctions() {
        try {
            ClassPath.from(ClassLoader.getSystemClassLoader())
                    .getAllClasses()
                    .stream()
                    .filter(classInfo -> {
                        Class<?> clazz = classInfo.load();
                        return clazz.isAssignableFrom(RecipeJsonProvider.class) && !Modifier.isAbstract(clazz.getModifiers());
                    })
                    .map(classInfo -> {
                        Class<?> clazz = classInfo.load();
                        if (HAS_OVERRIDE.contains(clazz)) {
                            return null;
                        }
                        Constructor<?> usedCon = Arrays.stream(clazz.getConstructors()).max(Comparator.comparingInt(Constructor::getParameterCount)).orElse(null);
                        if (usedCon == null) {
                            return null;
                        }
                        BiFunction<Recipe<?>, DynamicRegistryManager, RecipeJsonProvider> function = (recipe, registry) -> {
                            try {
                                List<Object> objects = new ArrayList<>();
                                int currentInput = 0;
                                for (int i = 0; i < usedCon.getParameterCount(); ++i) {
                                    Class<?> c = usedCon.getParameterTypes()[i];
                                    if (c.isAssignableFrom(Identifier.class)) {
                                        // Because the recipe id is handled through the power and the advancement ID are only handled through non
                                        // toJson and serialize methods, it should be safe to not worry about them.
                                        objects.add(i, new Identifier("calio", "not_required"));
                                    }
                                    if (c.isAssignableFrom(ItemConvertible.class)) {
                                        objects.add(i, recipe.getOutput(registry).getItem());
                                    }
                                    if (c.isAssignableFrom(Ingredient.class)) {
                                        objects.add(i, recipe.getIngredients().get(currentInput));
                                        currentInput++;
                                    }
                                }

                                return (RecipeJsonProvider) usedCon.newInstance(objects);
                            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                                Calio.LOGGER.warn("Could not create function for RecipeJsonProvider '{}'. (skipping): {}", classInfo.getName(), e);
                            }
                            return null;
                        };
                        return (BiFunction<Recipe<?>, DynamicRegistryManager, RecipeJsonProvider>)null;
                    })
                    .filter(Objects::nonNull)
                    .forEach(FUNCTIONS::add);
        } catch (IOException e) {
            Calio.LOGGER.warn("Could not create functions for RecipeJsonProvider");
        }
    }

}
