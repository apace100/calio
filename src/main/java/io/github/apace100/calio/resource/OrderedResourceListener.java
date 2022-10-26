package io.github.apace100.calio.resource;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * @deprecated  Deprecated in favour of using Fabric's IdentifiableResourceReloadListener.
 *              To establish an `after` dependency, simply add the other reload listener's identifier
 *              (IdentifiableResourceReloadListener#getFabricId) to the set your reload listener returns
 *              in IdentifiableResourceReloadListener#getFabricDependencies.
 *              To establish a `before` dependency, the other resource loader needs to expose the dependency
 *              set it returns in that method, e.g. by using a publicly accessible set, or exposing a public
 *              method to add to it.
 */
@Deprecated
public class OrderedResourceListener implements ModInitializer {

    public static final String ENTRYPOINT_KEY = "calio:ordered-resource-listener";

    @Override
    public void onInitialize() {
        OrderedResourceListenerManager manager = new OrderedResourceListenerManager();
        FabricLoader.getInstance().getEntrypoints(ENTRYPOINT_KEY, OrderedResourceListenerInitializer.class).forEach(
            entrypoint -> {
                entrypoint.registerResourceListeners(manager);
            }
        );
        manager.finishRegistration();
    }

    public static class Registration {

        private final OrderedResourceListenerManager.Instance manager;
        final Identifier id;
        final IdentifiableResourceReloadListener resourceReloadListener;
        final Set<Identifier> dependencies = new HashSet<>();
        final Set<Identifier> dependants = new HashSet<>();
        private boolean isCompleted;

        Registration(OrderedResourceListenerManager.Instance manager, IdentifiableResourceReloadListener listener) {
            this.id = listener.getFabricId();
            this.manager = manager;
            this.resourceReloadListener = listener;
        }

        public Registration after(String identifier) {
            return after(new Identifier(identifier));
        }

        public Registration after(Identifier identifier) {
            if(isCompleted) {
                throw new IllegalStateException(
                    "Can't add a resource reload listener registration dependency after it was completed.");
            }
            dependencies.add(identifier);
            return this;
        }

        public Registration before(String identifier) {
            return before(new Identifier(identifier));
        }

        public Registration before(Identifier identifier) {
            if(isCompleted) {
                throw new IllegalStateException(
                    "Can't add a resource reload listener registration dependant after it was completed.");
            }
            dependants.add(identifier);
            return this;
        }

        public void complete() {
            isCompleted = true;
            manager.add(this);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(id.toString());
            builder.append("{depends_on=[");
            boolean first = true;
            for (Identifier afterId : dependencies) {
                builder.append(afterId);
                if(!first) {
                    builder.append(',');
                }
                first = false;
            }
            builder.append("]}");
            return builder.toString();
        }
    }
}
