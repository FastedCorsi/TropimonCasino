package fr.tropimon.casino;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

final class CasinoSettingsScreen extends Screen {
    private final Screen parent;
    private final CasinoApi api;
    private boolean open;
    private TextFieldWidget bank;
    private TextFieldWidget minimumBet;
    private TextFieldWidget maximumBet;
    private TextFieldWidget maximumPayout;
    private ButtonWidget openButton;
    private ButtonWidget saveButton;
    private boolean working;
    private String status = "";
    private final Runnable onSaved;

    CasinoSettingsScreen(Screen parent, CasinoApi api, CasinoSettings settings, Runnable onSaved) {
        super(CasinoText.tr("screen.tropimon_casino.settings"));
        this.parent = parent;
        this.api = api;
        this.open = settings.open();
        initial = settings;
        this.onSaved = onSaved;
    }

    private final CasinoSettings initial;

    @Override
    protected void init() {
        int left = CasinoUi.left(width);
        int top = CasinoUi.top(height);
        bank = field(CasinoUi.x(left, 293), CasinoUi.y(top, 160), CasinoUi.scale(154), initial.bankPlayerName(), 16);
        minimumBet = field(CasinoUi.x(left, 293), CasinoUi.y(top, 190), CasinoUi.scale(154), Integer.toString(initial.minimumBet()), 8);
        maximumBet = field(CasinoUi.x(left, 293), CasinoUi.y(top, 220), CasinoUi.scale(154), Integer.toString(initial.maximumBet()), 8);
        maximumPayout = field(CasinoUi.x(left, 293), CasinoUi.y(top, 250), CasinoUi.scale(154), Integer.toString(initial.maximumPayout()), 12);
        openButton = addDrawableChild(ButtonWidget.builder(CasinoText.literal(""), button -> { open = !open; updateButtons(); })
                .dimensions(CasinoUi.x(left, 138), CasinoUi.y(top, 295),
                        CasinoUi.scale(100), CasinoUi.scale(22)).build());
        saveButton = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.save"), button -> save())
                .dimensions(CasinoUi.x(left, 248), CasinoUi.y(top, 295),
                        CasinoUi.scale(105), CasinoUi.scale(22)).build());
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.back"), button -> close())
                .dimensions(CasinoUi.x(left, 360), CasinoUi.y(top, 295),
                        CasinoUi.scale(94), CasinoUi.scale(22)).build());
        updateButtons();
    }

    private TextFieldWidget field(int x, int y, int width, String text, int maxLength) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, CasinoUi.scale(20), CasinoText.literal(""));
        field.setMaxLength(maxLength);
        field.setText(text);
        field.setChangedListener(ignored -> updateButtons());
        addDrawableChild(field);
        return field;
    }

    private void updateButtons() {
        if (openButton == null) return;
        openButton.setMessage(open ? CasinoText.tr("button.tropimon_casino.casino_open")
                : CasinoText.tr("button.tropimon_casino.casino_closed"));
        CasinoSettings value = value();
        saveButton.active = !working && value != null && value.valid();
    }

    private long parse(TextFieldWidget field) {
        try { return Long.parseLong(field.getText().trim()); }
        catch (Exception ignored) { return -1; }
    }

    private void save() {
        if (!saveButton.active) return;
        CasinoSettings value = value();
        if (value == null) return;
        working = true;
        status = CasinoText.string("status.tropimon_casino.recording");
        updateButtons();
        api.updateSettings(value).whenComplete((ignored, error) -> client.execute(() -> {
            working = false;
            status = error == null ? CasinoText.string("status.tropimon_casino.settings_saved") : CasinoApi.friendly(error);
            if (error == null) onSaved.run();
            updateButtons();
        }));
    }

    private CasinoSettings value() {
        long minimum = parse(minimumBet);
        long maximum = parse(maximumBet);
        long payout = parse(maximumPayout);
        if (minimum > Integer.MAX_VALUE || maximum > Integer.MAX_VALUE || payout > Integer.MAX_VALUE) return null;
        return new CasinoSettings(open, bank.getText().trim(), (int) minimum, (int) maximum, (int) payout);
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
            CasinoUi.panelHeading(context, textRenderer, left, top, CasinoText.tr("screen.tropimon_casino.settings_admin"));
            label(context, left, top + 166, "label.tropimon_casino.bank_account");
            label(context, left, top + 196, "label.tropimon_casino.minimum_bet");
            label(context, left, top + 226, "label.tropimon_casino.maximum_bet");
            label(context, left, top + 256, "label.tropimon_casino.maximum_payout");
            if (!status.isBlank()) context.drawCenteredTextWithShadow(textRenderer, CasinoText.literal(status),
                    left + CasinoUi.WIDTH / 2, top + 340, CasinoUi.BROWN);
        } finally {
            CasinoUi.end(context);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void label(DrawContext context, int left, int y, String key) {
        context.drawTextWithShadow(textRenderer, CasinoText.tr(key), left + 145, y, CasinoUi.BROWN);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
