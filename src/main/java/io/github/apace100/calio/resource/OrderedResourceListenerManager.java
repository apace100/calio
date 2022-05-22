package io.github.apace100.calio.resource;

import com.google.common.collect.Lists;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.Consumer;

public class OrderedResourceListenerManager {

    private final HashMap<ResourceType, Instance> instances = new HashMap<>();

    OrderedResourceListenerManager() {}

    public OrderedResourceListener.Registration register(ResourceType resourceType, IdentifiableResourceReloadListener resourceReloadListener) {
        Instance inst = instances.computeIfAbsent(resourceType, rt -> new Instance(ResourceManagerHelper.get(rt)::registerReloadListener));
        return new OrderedResourceListener.Registration(inst, resourceReloadListener);
    }

    void finishRegistration() {
        for(Instance inst : instances.values()) {
            inst.finish();
        }
    }

    static class Instance {
        private final HashMap<Identifier, OrderedResourceListener.Registration> registrations = new HashMap<>();
        private final HashMap<Integer, List<Identifier>> sortedMap = new HashMap<>();
        private int maxIndex = 0;

        private final Consumer<IdentifiableResourceReloadListener> registrationMethod;

        private Instance(Consumer<IdentifiableResourceReloadListener> registrationMethod) {
            this.registrationMethod = registrationMethod;
        }

        void add(OrderedResourceListener.Registration registration) {
            registrations.put(registration.id, registration);
        }

        void finish() {
            prepareSetsAndSort();
            List<Identifier> sortedList = new LinkedList<>();
            List<Identifier> nextListeners;
            while(!(nextListeners = copy(getRegistrations(0))).isEmpty()) {
                sortedList.addAll(nextListeners);
                sortedMap.remove(0);
                for(int i = 1; i <= maxIndex; i++) {
                    for(Identifier regId : copy(getRegistrations(i))) {
                        OrderedResourceListener.Registration registration = registrations.get(regId);
                        int before = registration.dependencies.size();
                        nextListeners.forEach(registration.dependencies::remove);
                        update(registration, before);
                    }
                }
            }
            if(!sortedMap.isEmpty()) {
                StringBuilder errorBuilder = new StringBuilder("Couldn't resolve ordered resource listener dependencies. Unsolved:");
                for(int i = 0; i <= maxIndex; i++) {
                    if(!getRegistrations(i).isEmpty()) {
                        errorBuilder.append("\t").append(i).append(" dependencies:");
                        for(Identifier id : getRegistrations(i)) {
                            OrderedResourceListener.Registration registration = registrations.get(id);
                            errorBuilder.append("\t\t").append(registration.toString());
                            registrationMethod.accept(registration.resourceReloadListener);
                        }
                    }
                }
                throw new RuntimeException(errorBuilder.toString());
            } else {
                for(Identifier id : sortedList) {
                    OrderedResourceListener.Registration registration = registrations.get(id);
                    registrationMethod.accept(registration.resourceReloadListener);
                }
            }
        }

        private void prepareSetsAndSort() {
            for (OrderedResourceListener.Registration reg : registrations.values()) {
                reg.dependencies.removeIf(id -> !registrations.containsKey(id));
                reg.dependants.forEach(id -> {
                    if(registrations.containsKey(id)) {
                        registrations.get(id).dependencies.add(reg.id);
                    }
                });
            }
            registrations.values().forEach(this::sortIntoMap);
        }

        private void sortIntoMap(OrderedResourceListener.Registration registration) {
            int index = registration.dependencies.size();
            List<Identifier> list = sortedMap.computeIfAbsent(index, i -> new LinkedList<>());
            list.add(registration.id);
            if(index > maxIndex) {
                maxIndex = index;
            }
        }

        private void update(OrderedResourceListener.Registration registration, int indexBefore) {
            int index = registration.dependencies.size();
            if(index == indexBefore) {
                return;
            }
            List<Identifier> regs = getRegistrations(indexBefore);
            regs.remove(registration.id);
            if(regs.isEmpty()) {
                sortedMap.remove(indexBefore);
            }
            List<Identifier> list = sortedMap.computeIfAbsent(index, i -> new LinkedList<>());
            list.add(registration.id);
        }

        private List<Identifier> getRegistrations(int index) {
            return sortedMap.getOrDefault(index, new LinkedList<>());
        }
    }

    private static <T> List<T> copy(List<T> list) {
        return Lists.newLinkedList(list);
    }
}
