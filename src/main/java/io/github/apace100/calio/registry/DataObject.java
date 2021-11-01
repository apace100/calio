package io.github.apace100.calio.registry;

public interface DataObject<T extends DataObject<T>> {

    DataObjectFactory<T> getFactory();
}
