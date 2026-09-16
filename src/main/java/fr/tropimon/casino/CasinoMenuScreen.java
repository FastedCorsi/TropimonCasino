package fr.tropimon.casino;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

final class CasinoMenuScreen extends Screen {
    private final CasinoScreen parent;
    private final CasinoApi api;

    CasinoMenuScreen(CasinoScreen parent, CasinoApi api) {
        super(CasinoText.tr("screen.tropimon_casino.menu"));
        this.parent = parent;
        this.api = api;
    }

    @Override
    protected void init() {
        CasinoSnapshot snapshot = parent.snapshot();
        RemoteProfile profile = snapshot.profile();
        int left = CasinoUi.left(width);
        int top = CasinoUi.top(height);
        int x1 = CasinoUi.x(left, 143);
        int x2 = CasinoUi.x(left, 306);
        int buttonWidth = CasinoUi.scale(148);

        ButtonWidget buy = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.deposit"), button ->
                        client.setScreen(new CasinoTransactionScreen(this, parent, api, true)))
                .dimensions(x1, CasinoUi.y(top, 172), buttonWidth, CasinoUi.scale(22)).build());
        ButtonWidget withdraw = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.withdraw"), button ->
                        client.setScreen(new CasinoTransactionScreen(this, parent, api, false)))
                .dimensions(x2, CasinoUi.y(top, 172), buttonWidth, CasinoUi.scale(22)).build());
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.history"), button ->
                        client.setScreen(new CasinoHistoryScreen(this, snapshot)))
                .dimensions(x1, CasinoUi.y(top, 205), buttonWidth, CasinoUi.scale(22)).build());
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.paytable"), button ->
                        client.setScreen(new CasinoPaytableScreen(this, parent)))
                .dimensions(x2, CasinoUi.y(top, 205), buttonWidth, CasinoUi.scale(22)).build());
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.refresh"), button -> {
                    parent.refresh();
                    client.setScreen(parent);
                }).dimensions(x1, CasinoUi.y(top, 238), buttonWidth, CasinoUi.scale(22)).build());

        boolean admin = profile != null && profile.admin();
        ButtonWidget adminButton = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.administration"), button ->
                        client.setScreen(new CasinoAdminScreen(this, parent, api)))
                .dimensions(x2, CasinoUi.y(top, 238), buttonWidth, CasinoUi.scale(22)).build());
        adminButton.visible = admin;
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.quit"), button -> parent.close())
                .dimensions(admin ? x1 : x2, CasinoUi.y(top, admin ? 271 : 238),
                        buttonWidth, CasinoUi.scale(22)).build());
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.return_machine"), button -> close())
                .dimensions(admin ? x2 : CasinoUi.x(left, 218), CasinoUi.y(top, 271),
                        admin ? buttonWidth : CasinoUi.scale(164), CasinoUi.scale(22)).build());

        boolean playerAccount = profile != null && !profile.bank();
        buy.active = playerAccount && !snapshot.settings().bankPlayerName().isBlank();
        withdraw.active = playerAccount && profile.chips() > 0;
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
            CasinoUi.panelHeading(context, textRenderer, left, top, CasinoText.tr("screen.tropimon_casino.menu"));
            RemoteProfile profile = parent.snapshot().profile();
            Text account = profile != null && profile.bank() ? CasinoText.tr("status.tropimon_casino.bank_account")
                    : CasinoText.tr("status.tropimon_casino.balance", profile == null ? 0 : profile.chips());
            context.drawCenteredTextWithShadow(textRenderer, account, left + CasinoUi.WIDTH / 2,
                    top + 149, CasinoUi.GREEN);
            context.drawCenteredTextWithShadow(textRenderer, CasinoText.tr("footer.tropimon_casino.withdrawal_delay"),
                    left + CasinoUi.WIDTH / 2, top + 335, 0xFF7C4A20);
        } finally {
            CasinoUi.end(context);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
