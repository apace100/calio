package io.github.apace100.calio.util;

import io.github.apace100.calio.Calio;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 *  <p>A utility class used for adding aliases to identifiers and/or its namespace and/or path, which can be used for substituting an
 *  {@link Identifier} (or its namespace and/or path).</p>
 *
 *  <p>This can be used in cases, such as serializing objects with {@linkplain com.mojang.serialization.Codec#dispatchMap(Function, Function) codec map dispatcher}
 *  (usually queried via {@link Registry#getCodec()}.)</p>
 */
public class IdentifierAlias {

    protected static final Function<Identifier, RuntimeException> NO_ALIAS_EXCEPTION = id -> new RuntimeException("Tried resolving non-existent alias for id \"" + id + "\"");
    public static final IdentifierAlias GLOBAL = new IdentifierAlias();

    protected final Map<Identifier, Identifier> identifierAliases = new HashMap<>();
    protected final Map<String, String> namespaceAliases = new HashMap<>();
    protected final Map<String, String> pathAliases = new HashMap<>();

    public void addAlias(Identifier fromId, Identifier toId) {

        if (identifierAliases.containsKey(fromId)) {
            Identifier owner = identifierAliases.get(fromId);
            Calio.LOGGER.error("Couldn't add alias \"{}\" to identifier \"{}\": {}", fromId, toId, (owner.equals(toId) ? "it's already defined!" : "it's already defined for a different identifier: \"" + owner + "\""));
        }

        else {
            identifierAliases.put(fromId, toId);
        }

    }

    public void addNamespaceAlias(String fromNamespace, String toNamespace) {

        if (namespaceAliases.containsKey(fromNamespace)) {
            String owner = namespaceAliases.get(fromNamespace);
            Calio.LOGGER.error("Couldn't add alias \"{}\" to namespace \"{}\": {}", fromNamespace, toNamespace, (owner.equals(toNamespace) ? "it's already defined!" : "it's already defined for a different namespace: \"" + owner + "\""));
        }

        else {
            namespaceAliases.put(fromNamespace, toNamespace);
        }

    }

    public void addPathAlias(String fromPath, String toPath) {

        if (pathAliases.containsKey(fromPath)) {
            String owner = pathAliases.get(fromPath);
            Calio.LOGGER.error("Couldn't add alias \"{}\" to path \"{}\": {}", fromPath, toPath, (owner.equals(toPath) ? "it's already defined!" : "it's already defined for a different path: \"" + owner + "\""));
        }

        else {
            pathAliases.put(fromPath, toPath);
        }

    }

    public boolean hasIdentifierAlias(Identifier id) {
        return identifierAliases.containsKey(id)
            || (this != GLOBAL && GLOBAL.hasIdentifierAlias(id));
    }

    public boolean hasNamespaceAlias(Identifier id) {
        String namespace = id.getNamespace();
        return namespaceAliases.containsKey(namespace)
            || (this != GLOBAL && GLOBAL.hasNamespaceAlias(id));
    }

    public boolean hasPathAlias(Identifier id) {
        String path = id.getPath();
        return pathAliases.containsKey(path)
            || (this != GLOBAL && GLOBAL.hasPathAlias(id));
    }

    public boolean hasAlias(Identifier id) {
        return this.hasIdentifierAlias(id)
            || this.hasNamespaceAlias(id)
            || this.hasPathAlias(id);
    }

    public Identifier resolveIdentifierAlias(Identifier id, boolean strict) {

        if (identifierAliases.containsKey(id)) {
            return identifierAliases.get(id);
        }

        else if (this != GLOBAL) {
            return GLOBAL.resolveIdentifierAlias(id, strict);
        }

        else if (strict) {
            throw NO_ALIAS_EXCEPTION.apply(id);
        }

        else {
            return id;
        }

    }

    public Identifier resolveNamespaceAlias(Identifier id, boolean strict) {

        String namespace = id.getNamespace();
        if (namespaceAliases.containsKey(namespace)) {
            return Identifier.of(namespaceAliases.get(namespace), id.getPath());
        }

        else if (this != GLOBAL) {
            return GLOBAL.resolveNamespaceAlias(id, strict);
        }

        else if (strict) {
            throw NO_ALIAS_EXCEPTION.apply(id);
        }

        else {
            return id;
        }

    }

    public Identifier resolvePathAlias(Identifier id, boolean strict) {

        String path = id.getPath();
        if (pathAliases.containsKey(path)) {
            return Identifier.of(id.getNamespace(), pathAliases.get(path));
        }

        else if (this != GLOBAL) {
            return GLOBAL.resolvePathAlias(id, strict);
        }

        else if (strict) {
            throw NO_ALIAS_EXCEPTION.apply(id);
        }

        else {
            return id;
        }

    }

    public Identifier resolveAlias(Identifier id, Predicate<Identifier> untilPredicate) {

        Identifier aliasedId;
        for (Resolver resolver : Resolver.values()) {

            aliasedId = resolver.apply(this, id);

            if (untilPredicate.test(aliasedId)) {
                return aliasedId;
            }

        }

        return id;

    }

    public enum Resolver implements BiFunction<IdentifierAlias, Identifier, Identifier> {

        NO_OP {

            @Override
            public Identifier apply(IdentifierAlias aliases, Identifier id) {
                return id;
            }

        },

        IDENTIFIER {

            @Override
            public Identifier apply(IdentifierAlias aliases, Identifier id) {
                return aliases.resolveIdentifierAlias(id, false);
            }

        },

        NAMESPACE {

            @Override
            public Identifier apply(IdentifierAlias aliases, Identifier id) {
                return aliases.resolveNamespaceAlias(id, false);
            }

        },

        PATH {

            @Override
            public Identifier apply(IdentifierAlias aliases, Identifier id) {
                return aliases.resolvePathAlias(id, false);
            }

        },

        NAMESPACE_AND_PATH {

            @Override
            public Identifier apply(IdentifierAlias aliases, Identifier id) {

                String aliasedNamespace = aliases.resolveNamespaceAlias(id, false).getNamespace();
                String aliasedPath = aliases.resolvePathAlias(id, false).getPath();

                return Identifier.of(aliasedNamespace, aliasedPath);

            }

        }

    }

}
