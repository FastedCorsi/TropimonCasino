package fr.tropimon.casino;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

final class CasinoMachineButton extends PressableWidget {
    private final Runnable action;

    CasinoMachineButton(int centerX, int centerY, Text message, Runnable action) {
        super(centerX - CasinoUi.scale(36), centerY - CasinoUi.scale(26),
                CasinoUi.scale(72), CasinoUi.scale(52), message);
        this.action = action;
    }

    @Override
    public void onPress() {
        if (active) action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int color = !active ? 0xFF777777 : hovered ? 0xFFD33C00 : CasinoUi.BROWN;
        int centerX = getX() + getWidth() / 2;
        var renderer = MinecraftClient.getInstance().textRenderer;
        int textY = getY() + (getHeight() - renderer.fontHeight) / 2;
        context.drawCenteredTextWithShadow(renderer, getMessage().copy().formatted(Formatting.BOLD), centerX, textY, color);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
