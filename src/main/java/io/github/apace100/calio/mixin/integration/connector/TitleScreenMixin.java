package io.github.apace100.calio.mixin.integration.connector;

import io.github.apace100.calio.screen.MissingConnectorModDependencyScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void openScreen(CallbackInfo ci) {
        if (FabricLoader.getInstance().isModLoaded("connectormod")) {
            List<Text> errorText = buildConnectorDependencyText();
            if (!errorText.isEmpty()) {
                String subtitle = errorText.size() == 1 ? "1 error has occurred during loading" : errorText.size() + " errors have occured during loading";
                MinecraftClient.getInstance().setScreen(new MissingConnectorModDependencyScreen(errorText, "Error loading mods", subtitle));
            }
        }
    }

    @Unique
    private static List<Text> buildConnectorDependencyText() {
        List<Text> lines = new ArrayList<>();

        FabricLoader.getInstance().getAllMods().forEach(mod -> {
            String modId = mod.getMetadata().getId();
            var value = mod.getMetadata().getCustomValue("connector-depends");
            if (value != null && value.getType() == CustomValue.CvType.OBJECT) {
                var obj = value.getAsObject();

                obj.forEach((customValueEntry) -> {
                    String depId = customValueEntry.getKey();
                    if(!FabricLoader.getInstance().isModLoaded(depId)) {
                        CustomValue depVal = customValueEntry.getValue();
                        String version;
                        String displayName = null;

                        if (depVal.getType() == CustomValue.CvType.STRING) {
                            version = depVal.getAsString();
                        } else if (depVal.getType() == CustomValue.CvType.OBJECT) {
                            var depObj = depVal.getAsObject();
                            version = depObj.containsKey("version") ? depObj.get("version").getAsString() : "unknown";
                            if (depObj.containsKey("name")) {
                                displayName = depObj.get("name").getAsString();
                            }
                        } else {
                            version = "unknown";
                        }

                        MutableText line = Text.literal("Mod ")
                                .append(Text.literal(modId).formatted(Formatting.YELLOW))
                                .append(Text.literal(" requires "))
                                .append(Text.literal(depId).formatted(Formatting.GOLD));

                        if (displayName != null && !displayName.isEmpty()) {
                            line = line.append(Text.literal(" (" + displayName + ")").formatted(Formatting.GRAY));
                        }

                        line = line.append(Text.literal(" " + version).formatted(Formatting.ITALIC));
                        lines.add(line);
                    }
                });
            }
        });

        return lines;
    }
}
