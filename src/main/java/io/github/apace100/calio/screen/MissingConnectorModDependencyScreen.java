package io.github.apace100.calio.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MissingConnectorModDependencyScreen extends Screen {
    private final List<Text> lines = new ArrayList<>();
    private final String titleLine;
    private final String subtitleLine;
    private double scrollOffset = 0.0;

    public MissingConnectorModDependencyScreen(Text content, String titleLine, String subtitleLine) {
        super(Text.of("Exception"));
        this.titleLine = titleLine;
        this.subtitleLine = subtitleLine;

        String raw = content.getString();
        if (raw.contains("\n")) {
            for (String ln : raw.split("\\r?\\n")) {
                this.lines.add(Text.literal(ln));
            }
        } else {
            this.lines.add(content);
        }
    }

    public MissingConnectorModDependencyScreen(List<Text> lines, String titleLine, String subtitleLine) {
        super(Text.of("Exception"));
        this.titleLine = titleLine;
        this.subtitleLine = subtitleLine;
        this.lines.addAll(lines);
    }

    @Override
    protected void init() {
        super.init();

        final int buttonWidth = 160;
        final int buttonHeight = 20;
        final int spacing = 8;
        final int totalWidth = buttonWidth * 3 + spacing * 2;
        final int startX = (this.width - totalWidth) / 2;
        final int y = this.height - 35;


        ButtonWidget modsBtn = ButtonWidget.builder(Text.of("Open Mods Folder"), btn -> {
            try {
                Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
                openPath(mods);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).position(startX + buttonWidth + spacing, y).size(buttonWidth, buttonHeight).build();
        addDrawableChild(modsBtn);
    }

    private void openPath(Path p) throws Exception {
        File f = p.toFile();
        if (!f.exists()) {
            File parent = f.getParentFile();
            if (parent != null && parent.exists()) {
                openFile(parent);
            }
            return;
        }
        openFile(f);
    }

    private void openFile(File f) throws Exception {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(f);
        } else {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", f.getAbsolutePath()});
            } else if (os.contains("nix") || os.contains("nux")) {
                Runtime.getRuntime().exec(new String[]{"xdg-open", f.getAbsolutePath()});
            } else if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "\"\"", f.getAbsolutePath()});
            } else {
                throw new UnsupportedOperationException("Cannot open file on this OS: " + os);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        final TextRenderer tr = this.textRenderer;

        final int centreX = this.width / 2;
        int y = 15;

        int titleWidth = tr.getWidth(titleLine);
        context.drawTextWithShadow(tr, Text.of(titleLine), centreX - titleWidth / 2, y, 0xFF5555);
        y += tr.fontHeight + 2;

        int subtitleWidth = tr.getWidth(subtitleLine);
        context.drawTextWithShadow(tr, Text.of(subtitleLine), centreX - subtitleWidth / 2, y, 0xFF5555);
        y += tr.fontHeight + 6;

        int leftPadding = 20;
        int areaX = leftPadding;
        int topPadding = 50;
        int areaY = topPadding;
        int rightPadding = 20;
        int areaWidth = this.width - leftPadding - rightPadding;
        int bottomButtonAreaHeight = 50;
        int areaHeight = this.height - topPadding - bottomButtonAreaHeight - 10;

        context.fill(areaX - 4, areaY - 4, areaX + areaWidth + 4, areaY + areaHeight + 4, 0x88000000);
        drawRectOutline(context, areaX - 4, areaY - 4, areaX + areaWidth + 4, areaY + areaHeight + 4, 0xFF333333);

        List<OrderedText> wrappedLines = new ArrayList<>();
        for (Text line : lines) {
            wrappedLines.addAll(tr.wrapLines(line, areaWidth));
        }

        int fh = tr.fontHeight;
        int maxVisibleLines = Math.max(1, areaHeight / fh);
        double maxScroll = Math.max(0, wrappedLines.size() - maxVisibleLines);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int startLine = (int) Math.floor(scrollOffset);
        int endLine = Math.min(wrappedLines.size(), startLine + maxVisibleLines);

        context.enableScissor(areaX, areaY, areaX + areaWidth, areaY + areaHeight);
        int drawY = areaY;
        for (int i = startLine; i < endLine; i++) {
            context.drawTextWithShadow(tr, wrappedLines.get(i), areaX, drawY, 0xFFFFFF);
            drawY += fh;
        }
        context.disableScissor();

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawRectOutline(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scrollOffset -= amount * 3;
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}