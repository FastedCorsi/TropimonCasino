package fr.tropimon.casino;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class CasinoHistoryScreen extends Screen {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
    private static final int PAGE_SIZE = 6;
    private final Screen parent;
    private final CasinoSnapshot snapshot;
    private boolean operations = true;
    private int page;

    CasinoHistoryScreen(Screen parent, CasinoSnapshot snapshot) {
        super(CasinoText.tr("screen.tropimon_casino.history"));
        this.parent = parent;
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        int left = CasinoUi.left(width);
        int top = CasinoUi.top(height);
        ButtonWidget operationTab = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.operations"), button -> {
            operations = true;
            page = 0;
            rebuild();
        }).dimensions(CasinoUi.x(left, 143), CasinoUi.y(top, 151),
                CasinoUi.scale(148), CasinoUi.scale(22)).build());
        ButtonWidget spinTab = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.spins"), button -> {
            operations = false;
            page = 0;
            rebuild();
        }).dimensions(CasinoUi.x(left, 306), CasinoUi.y(top, 151),
                CasinoUi.scale(148), CasinoUi.scale(22)).build());
        operationTab.active = !operations;
        spinTab.active = operations;

        ButtonWidget previous = addDrawableChild(ButtonWidget.builder(CasinoText.literal("<"), button -> {
            page--;
            rebuild();
        }).dimensions(CasinoUi.x(left, 162), CasinoUi.y(top, 335),
                CasinoUi.scale(42), CasinoUi.scale(22)).build());
        previous.active = page > 0;
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.back"), button -> close())
                .dimensions(CasinoUi.x(left, 218), CasinoUi.y(top, 335),
                        CasinoUi.scale(164), CasinoUi.scale(22)).build());
        ButtonWidget next = addDrawableChild(ButtonWidget.builder(CasinoText.literal(">"), button -> {
            page++;
            rebuild();
        }).dimensions(CasinoUi.x(left, 396), CasinoUi.y(top, 335),
                CasinoUi.scale(42), CasinoUi.scale(22)).build());
        int count = operations ? snapshot.transactions().size() : snapshot.spins().size();
        next.active = (page + 1) * PAGE_SIZE < count;
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
            CasinoUi.panelHeading(context, textRenderer, left, top, CasinoText.tr("screen.tropimon_casino.history"));
            if (operations) drawTransactions(context, left, top); else drawSpins(context, left, top);
        } finally {
            CasinoUi.end(context);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawTransactions(DrawContext context, int left, int top) {
        int start = page * PAGE_SIZE;
        int y = top + 185;
        for (int i = start; i < Math.min(start + PAGE_SIZE, snapshot.transactions().size()); i++) {
            RemoteTransaction tx = snapshot.transactions().get(i);
            Text kind = tx.type() == TransactionType.DEPOSIT ? CasinoText.tr("value.tropimon_casino.deposit")
                    : CasinoText.tr("value.tropimon_casino.withdrawal");
            int color = tx.overdue(Instant.now()) ? 0xFFCA2020 : CasinoUi.BROWN;
            context.drawTextWithShadow(textRenderer,
                    CasinoText.tr("line.tropimon_casino.transaction", DATE.format(tx.createdAt()), kind,
                            tx.pokeDollars(), status(tx.status())),
                    left + 145, y, color);
            y += 23;
        }
        if (snapshot.transactions().isEmpty()) empty(context, left, top, "empty.tropimon_casino.transactions");
    }

    private void drawSpins(DrawContext context, int left, int top) {
        int start = page * PAGE_SIZE;
        int y = top + 185;
        for (int i = start; i < Math.min(start + PAGE_SIZE, snapshot.spins().size()); i++) {
            RemoteSpin spin = snapshot.spins().get(i);
            context.drawTextWithShadow(textRenderer,
                    CasinoText.tr("line.tropimon_casino.spin", spin.bet(), spin.payout()),
                    left + 145, y, spin.payout() > 0 ? CasinoUi.GREEN : CasinoUi.BROWN);
            context.drawTextWithShadow(textRenderer,
                    CasinoText.tr("line.tropimon_casino.symbols", spin.first().label(), spin.second().label(), spin.third().label()),
                    left + 155, y + 10, 0xFF7C4A20);
            y += 25;
        }
        if (snapshot.spins().isEmpty()) empty(context, left, top, "empty.tropimon_casino.spins");
    }

    private void empty(DrawContext context, int left, int top, String key) {
        context.drawCenteredTextWithShadow(textRenderer, CasinoText.tr(key), left + CasinoUi.WIDTH / 2,
                top + 245, 0xFF7C4A20);
    }

    private static Text status(TransactionStatus status) {
        return switch (status) {
            case PENDING -> CasinoText.tr("status.tropimon_casino.pending");
            case APPROVED -> CasinoText.tr("status.tropimon_casino.approved");
            case PAID -> CasinoText.tr("status.tropimon_casino.paid");
            case REJECTED -> CasinoText.tr("status.tropimon_casino.rejected");
        };
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
