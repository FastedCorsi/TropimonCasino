package fr.tropimon.casino;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

import java.util.Locale;

final class CasinoPaytableScreen extends Screen {
    private final Screen parent;
    private final CasinoScreen machine;

    CasinoPaytableScreen(Screen parent, CasinoScreen machine) {
        super(CasinoText.tr("screen.tropimon_casino.paytable"));
        this.parent = parent;
        this.machine = machine;
    }

    @Override
    protected void init() {
        int left = CasinoUi.left(width);
        int top = CasinoUi.top(height);
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.back"), button -> close())
                .dimensions(CasinoUi.x(left, 218), CasinoUi.y(top, 343),
                        CasinoUi.scale(164), CasinoUi.scale(22)).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = CasinoUi.left(width);
        int top = CasinoUi.top(height);
        CasinoUi.begin(context, left, top);
        try {
            CasinoUi.background(context, left, top);
            CasinoUi.title(context, textRenderer, left, top, CasinoText.tr("screen.tropimon_casino.title"));
            CasinoUi.panelHeading(context, textRenderer, left, top, CasinoText.tr("screen.tropimon_casino.paytable"));

            SlotSymbol[] symbols = SlotSymbol.values();
            for (int row = 0; row < symbols.length; row++) {
                SlotSymbol symbol = symbols[row];
                int y = top + 157 + row * 24;
                CasinoUi.symbol(context, symbol, left + 158, y + 8, 19);
                context.drawTextWithShadow(textRenderer, symbol.label(), left + 174, y + 4, CasinoUi.BROWN);
                context.drawTextWithShadow(textRenderer,
                        CasinoText.tr("line.tropimon_casino.paytable", symbol.pairMultiplier(), symbol.tripleMultiplier()),
                        left + 315, y + 4, symbol == SlotSymbol.TROPIMON ? CasinoUi.GREEN : 0xFF7C4A20);
            }

            CasinoSettings settings = machine.snapshot().settings();
            double rtp = SlotOdds.theoreticalReturn(machine.bet(), settings.maximumPayout()) * 100.0;
            context.drawCenteredTextWithShadow(textRenderer,
                    CasinoText.tr("footer.tropimon_casino.current_rtp",
                            String.format(Locale.ROOT, "%.2f %%", rtp), settings.maximumPayout()),
                    left + CasinoUi.WIDTH / 2, top + 329, 0xFF7C4A20);
        } finally {
            CasinoUi.end(context);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
