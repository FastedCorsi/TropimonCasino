package fr.tropimon.casino;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.UUID;

final class CasinoTransactionScreen extends Screen {
    private final Screen parent;
    private final CasinoScreen machine;
    private final CasinoApi api;
    private final boolean deposit;
    private TextFieldWidget amount;
    private ButtonWidget submit;
    private boolean working;
    private String status = "";

    CasinoTransactionScreen(Screen parent, CasinoScreen machine, CasinoApi api, boolean deposit) {
        super(deposit ? CasinoText.tr("screen.tropimon_casino.deposit") : CasinoText.tr("screen.tropimon_casino.withdraw"));
        this.parent = parent;
        this.machine = machine;
        this.api = api;
        this.deposit = deposit;
    }

    @Override
    protected void init() {
        int left = CasinoUi.left(width);
        int top = CasinoUi.top(height);
        amount = new TextFieldWidget(textRenderer, CasinoUi.x(left, 160), CasinoUi.y(top, 190),
                CasinoUi.scale(280), CasinoUi.scale(22),
                CasinoText.tr("field.tropimon_casino.pokedollars"));
        amount.setMaxLength(12);
        amount.setPlaceholder(CasinoText.tr("field.tropimon_casino.pokedollars"));
        amount.setChangedListener(ignored -> validate());
        addDrawableChild(amount);
        submit = addDrawableChild(ButtonWidget.builder(deposit ? CasinoText.tr("button.tropimon_casino.pay_deposit")
                        : CasinoText.tr("button.tropimon_casino.request_withdrawal"),
                        button -> submit()).dimensions(CasinoUi.x(left, 160), CasinoUi.y(top, 226),
                        CasinoUi.scale(280), CasinoUi.scale(22)).build());
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.back"), button -> close())
                .dimensions(CasinoUi.x(left, 225), CasinoUi.y(top, 329),
                        CasinoUi.scale(150), CasinoUi.scale(22)).build());
        setInitialFocus(amount);
        validate();
    }

    private void validate() {
        if (submit == null) return;
        submit.active = !working && positiveAmount() > 0;
    }

    private long positiveAmount() {
        try {
            long value = Long.parseLong(amount.getText().trim());
            return value > 0 ? value : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void submit() {
        long value = positiveAmount();
        if (value <= 0 || working) return;
        CasinoSettings settings = machine.snapshot().settings();
        if (deposit && settings.bankPlayerName().isBlank()) {
            status = CasinoText.string("error.tropimon_casino.bank_unconfigured");
            return;
        }
        working = true;
        status = CasinoText.string("status.tropimon_casino.recording");
        validate();
        var future = deposit ? api.requestDeposit(value, "casino_deposit", UUID.randomUUID())
                : api.requestWithdrawal(value, "casino_withdrawal", UUID.randomUUID());
        future.whenComplete((transaction, error) -> client.execute(() -> {
            working = false;
            if (error != null) {
                status = CasinoApi.friendly(error);
                validate();
                return;
            }
            if (deposit) {
                if (client.player != null) {
                    client.player.networkHandler.sendChatCommand("pay " + settings.bankPlayerName() + " " + value);
                    status = CasinoText.string("status.tropimon_casino.pay_sent");
                } else status = CasinoText.string("status.tropimon_casino.pay_not_sent");
            } else status = CasinoText.string("status.tropimon_casino.withdrawal_recorded");
            machine.refresh();
            submit.active = false;
        }));
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
            Text title = deposit ? CasinoText.tr("screen.tropimon_casino.deposit") : CasinoText.tr("screen.tropimon_casino.withdraw");
            CasinoUi.panelHeading(context, textRenderer, left, top, title);
            CasinoSettings settings = machine.snapshot().settings();
            context.drawCenteredTextWithShadow(textRenderer,
                    deposit ? CasinoText.tr("help.tropimon_casino.deposit_balance")
                            : CasinoText.tr("help.tropimon_casino.withdraw_balance"),
                    left + CasinoUi.WIDTH / 2, top + 158, CasinoUi.GREEN);
            Text detail = deposit
                    ? CasinoText.tr("help.tropimon_casino.pay_command", settings.bankPlayerName())
                    : CasinoText.tr("help.tropimon_casino.manual_payment");
            context.drawCenteredTextWithShadow(textRenderer, detail, left + CasinoUi.WIDTH / 2,
                    top + 264, 0xFF7C4A20);
            CasinoUi.panelStatus(context, textRenderer, left, top, CasinoText.literal(status), CasinoUi.BROWN);
        } finally {
            CasinoUi.end(context);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
