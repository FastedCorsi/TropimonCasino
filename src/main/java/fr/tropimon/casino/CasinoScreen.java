package fr.tropimon.casino;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CasinoScreen extends Screen {
    private static final int[] BET_STEPS = {1, 5, 10, 20, 25, 50, 100, 250, 500, 1_000, 2_500,
            5_000, 10_000, 25_000, 50_000, 100_000};
    private final CasinoApi api;
    private CasinoSnapshot snapshot = CasinoSnapshot.empty();
    private final SlotSymbol[] visible = {SlotSymbol.PSYDUCK, SlotSymbol.SLOWPOKE, SlotSymbol.GENGAR};
    private RemoteSpin pending;
    private long spinStarted;
    private long resultReadyElapsed = -1;
    private final long[] stopElapsed = {-1, -1, -1};
    private int requestedStops;
    private int stoppedReels;
    private boolean spinning;
    private int bet = 1;
    private long lastPayout;
    private boolean loading = true;
    private boolean positiveStatus;
    private String status = CasinoText.string("status.tropimon_casino.connecting");
    private CasinoMachineButton betButton;
    private CasinoMachineButton spinButton;
    private CasinoMachineButton menuButton;

    CasinoScreen(CasinoApi api) {
        super(CasinoText.tr("screen.tropimon_casino.title"));
        this.api = api;
    }

    @Override
    protected void init() {
        int left = CasinoUi.left(width);
        int top = CasinoUi.top(height);
        betButton = addDrawableChild(new CasinoMachineButton(CasinoUi.x(left, CasinoUi.LEFT_BUTTON_X),
                CasinoUi.y(top, CasinoUi.BUTTON_Y), CasinoText.tr("button.tropimon_casino.bet"), this::cycleBet));
        spinButton = addDrawableChild(new CasinoMachineButton(CasinoUi.x(left, CasinoUi.CENTER_BUTTON_X),
                CasinoUi.y(top, CasinoUi.BUTTON_Y), CasinoText.tr("button.tropimon_casino.play"), () -> {
                    if (spinning) stopNextReel(); else spin();
                }));
        menuButton = addDrawableChild(new CasinoMachineButton(CasinoUi.x(left, CasinoUi.RIGHT_BUTTON_X),
                CasinoUi.y(top, CasinoUi.BUTTON_Y), CasinoText.tr("button.tropimon_casino.menu"), () ->
                client.setScreen(new CasinoMenuScreen(this, api))));
        updateButtons();
        if (snapshot.profile() == null) connect(); else refresh();
    }

    CasinoSnapshot snapshot() {
        return snapshot;
    }

    int bet() {
        return bet;
    }

    private void connect() {
        if (client == null || client.player == null) {
            fail(CasinoText.string("error.tropimon_casino.minecraft_connection"));
            return;
        }
        loading = true;
        positiveStatus = false;
        status = CasinoText.string("status.tropimon_casino.connecting");
        updateButtons();
        api.connect(client.player.getUuid(), client.player.getGameProfile().getName())
                .whenComplete((value, error) -> client.execute(() -> apply(value, error)));
    }

    void refresh() {
        loading = true;
        positiveStatus = false;
        status = CasinoText.string("status.tropimon_casino.refreshing");
        updateButtons();
        api.snapshot().whenComplete((value, error) -> client.execute(() -> apply(value, error)));
    }

    private void apply(CasinoSnapshot value, Throwable error) {
        if (error != null) {
            fail(CasinoApi.friendly(error));
            return;
        }
        snapshot = value;
        CasinoSettings settings = value.settings();
        bet = Math.max(settings.minimumBet(), Math.min(bet, settings.maximumBet()));
        loading = false;
        positiveStatus = value.profile().bank() || settings.open();
        status = value.profile().bank() ? CasinoText.string("status.tropimon_casino.bank_mode")
                : settings.open() ? CasinoText.string("status.tropimon_casino.open")
                : CasinoText.string("status.tropimon_casino.closed");
        updateButtons();
    }

    private void fail(String message) {
        loading = false;
        positiveStatus = false;
        status = message;
        updateButtons();
    }

    private void cycleBet() {
        CasinoSettings settings = snapshot.settings();
        if (bet >= settings.maximumBet()) bet = settings.minimumBet();
        else {
            int next = settings.maximumBet();
            for (int candidate : BET_STEPS) {
                if (candidate > bet && candidate >= settings.minimumBet()) {
                    next = Math.min(candidate, settings.maximumBet());
                    break;
                }
            }
            bet = next;
        }
        updateButtons();
    }

    private void spin() {
        if (!canSpin()) return;
        spinning = true;
        positiveStatus = false;
        pending = null;
        resultReadyElapsed = -1;
        requestedStops = 0;
        stoppedReels = 0;
        java.util.Arrays.fill(stopElapsed, -1L);
        spinStarted = System.currentTimeMillis();
        status = CasinoText.string("status.tropimon_casino.stop_reel", 1);
        updateButtons();
        api.spin(bet, UUID.randomUUID()).whenComplete((result, error) -> client.execute(() -> {
            if (error != null) {
                spinning = false;
                pending = null;
                fail(CasinoApi.friendly(error));
                return;
            }
            pending = result;
            resultReadyElapsed = System.currentTimeMillis() - spinStarted;
            for (int reel = 0; reel < requestedStops; reel++) {
                stopElapsed[reel] = ReelAnimation.queuedStopElapsed(reel, resultReadyElapsed);
            }
        }));
    }

    private void stopNextReel() {
        if (!spinning || stoppedReels >= 3 || requestedStops > stoppedReels) return;
        int reel = stoppedReels;
        requestedStops = reel + 1;
        if (pending != null) stopElapsed[reel] = System.currentTimeMillis() - spinStarted;
        status = CasinoText.string("status.tropimon_casino.stopping_reel", reel + 1);
        updateButtons();
    }

    private boolean canSpin() {
        RemoteProfile profile = snapshot.profile();
        return !loading && !spinning && profile != null && snapshot.settings().open()
                && (profile.bank() || profile.chips() >= bet);
    }

    private void updateButtons() {
        if (betButton == null) return;
        RemoteProfile profile = snapshot.profile();
        boolean ready = !loading && profile != null;
        betButton.setMessage(CasinoText.tr("button.tropimon_casino.bet"));
        betButton.active = ready && !spinning;
        spinButton.setMessage(spinning
                ? requestedStops > stoppedReels ? CasinoText.tr("button.tropimon_casino.stopping")
                    : stoppedReels < 3 ? CasinoText.tr("button.tropimon_casino.stop", stoppedReels + 1)
                    : CasinoText.tr("button.tropimon_casino.stopping")
                : CasinoText.tr("button.tropimon_casino.play"));
        spinButton.active = spinning ? stoppedReels < 3 && requestedStops == stoppedReels : canSpin();
        menuButton.active = ready && !spinning;
    }

    @Override
    public void tick() {
        if (!spinning) return;
        long elapsed = System.currentTimeMillis() - spinStarted;
        if (pending != null) {
            for (int reel = 0; reel < 3; reel++) {
                if (!ReelAnimation.turning(elapsed, stopElapsed[reel])) visible[reel] = pending.symbol(reel);
            }
            int before = stoppedReels;
            while (stoppedReels < 3 && stopElapsed[stoppedReels] >= 0
                    && elapsed >= stopElapsed[stoppedReels]) stoppedReels++;
            if (stoppedReels != before && stoppedReels < 3) {
                requestedStops = Math.max(requestedStops, stoppedReels);
                status = CasinoText.string("status.tropimon_casino.reel_stopped", stoppedReels, stoppedReels + 1);
                updateButtons();
            }
        }
        if (pending != null && ReelAnimation.finished(elapsed, stopElapsed)) {
            lastPayout = pending.payout();
            RemoteProfile old = snapshot.profile();
            RemoteProfile updated = new RemoteProfile(old.userId(), old.minecraftUuid(), old.minecraftName(), old.role(), pending.balanceAfter());
            List<RemoteSpin> spins = new ArrayList<>(snapshot.spins());
            spins.add(0, pending);
            snapshot = new CasinoSnapshot(updated, snapshot.settings(), snapshot.transactions(), List.copyOf(spins));
            status = pending.payout() > 0 ? CasinoText.string("status.tropimon_casino.won", pending.payout())
                    : CasinoText.string("status.tropimon_casino.lost");
            positiveStatus = pending.payout() > 0;
            spinning = false;
            pending = null;
            resultReadyElapsed = -1;
            requestedStops = 0;
            stoppedReels = 0;
            java.util.Arrays.fill(stopElapsed, -1L);
            updateButtons();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        int left = CasinoUi.left(width);
        int top = CasinoUi.top(height);
        CasinoUi.begin(context, left, top);
        try {
            long elapsed = spinning ? System.currentTimeMillis() - spinStarted : 0;
            CasinoUi.background(context, left, top,
                    ReelAnimation.activeIndicator(spinning, elapsed, stopElapsed), elapsed);
            CasinoUi.title(context, textRenderer, left, top, CasinoText.tr("screen.tropimon_casino.title"));
            RemoteProfile profile = snapshot.profile();
            CasinoUi.meter(context, textRenderer, left + 142, top + 139, CasinoText.tr("meter.tropimon_casino.balance"),
                    profile != null && profile.bank() ? CasinoText.tr("value.tropimon_casino.bank")
                            : CasinoText.tr("value.tropimon_casino.pokedollars", profile == null ? 0 : profile.chips()));
            CasinoUi.meter(context, textRenderer, left + 252, top + 139, CasinoText.tr("meter.tropimon_casino.bet"),
                    CasinoText.tr("value.tropimon_casino.pokedollars", bet));
            CasinoUi.meter(context, textRenderer, left + 358, top + 139, CasinoText.tr("meter.tropimon_casino.payout"),
                    CasinoText.tr("value.tropimon_casino.pokedollars", lastPayout));

            for (int reel = 0; reel < 3; reel++) {
                boolean turning = spinning && (pending == null || ReelAnimation.turning(elapsed, stopElapsed[reel]));
                SlotSymbol selected = !turning && pending != null ? pending.symbol(reel) : visible[reel];
                CasinoUi.reel(context, textRenderer, left, top, left + 165 + reel * 93, top + 175,
                        selected, reel, turning, elapsed);
            }
            int color = positiveStatus ? CasinoUi.GREEN : CasinoUi.BROWN;
            context.drawCenteredTextWithShadow(textRenderer, CasinoText.literal(status), left + CasinoUi.WIDTH / 2,
                    top + 346, color);
            context.drawCenteredTextWithShadow(textRenderer,
                    CasinoText.tr("footer.tropimon_casino.rtp",
                            String.format(java.util.Locale.ROOT, "%.2f %%",
                                    SlotOdds.theoreticalReturn(bet, snapshot.settings().maximumPayout()) * 100.0)),
                    left + CasinoUi.WIDTH / 2, top + 357, 0xFF7C4A20);
        } finally {
            CasinoUi.end(context);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean shouldPause() { return false; }
}
