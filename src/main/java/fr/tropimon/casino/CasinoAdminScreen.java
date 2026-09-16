package fr.tropimon.casino;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

final class CasinoAdminScreen extends Screen {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
    private static final int TRANSACTIONS_PER_PAGE = 3;
    private static final int ROLES_PER_PAGE = 6;
    private static final int AUDIT_PER_PAGE = 6;
    private final Screen parent;
    private final CasinoScreen machine;
    private final CasinoApi api;
    private AdminSnapshot data = new AdminSnapshot(List.of(), List.of(), List.of(), 0);
    private Tab tab = Tab.OPERATIONS;
    private int page;
    private boolean loading = true;
    private String status = CasinoText.string("status.tropimon_casino.loading_operations");

    CasinoAdminScreen(Screen parent, CasinoScreen machine, CasinoApi api) {
        super(CasinoText.tr("screen.tropimon_casino.administration"));
        this.parent = parent;
        this.machine = machine;
        this.api = api;
    }

    private enum Tab { OPERATIONS, AUDIT, ROLES }

    @Override
    protected void init() {
        rebuild();
        refresh();
    }

    private void rebuild() {
        clearChildren();
        int left = CasinoUi.left(width);
        int top = CasinoUi.top(height);
        ButtonWidget operations = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.operations"), button -> {
            tab = Tab.OPERATIONS;
            page = 0;
            rebuild();
        }).dimensions(CasinoUi.x(left, 125), CasinoUi.y(top, 151),
                CasinoUi.scale(73), CasinoUi.scale(22)).build());
        operations.active = tab != Tab.OPERATIONS;

        ButtonWidget audit = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.audit"), button -> {
            tab = Tab.AUDIT;
            page = 0;
            rebuild();
        }).dimensions(CasinoUi.x(left, 202), CasinoUi.y(top, 151),
                CasinoUi.scale(60), CasinoUi.scale(22)).build());
        audit.active = tab != Tab.AUDIT;

        boolean superAdmin = profile().role() == CasinoRole.SUPER_ADMIN;
        ButtonWidget roleTab = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.roles"), button -> {
            tab = Tab.ROLES;
            page = 0;
            rebuild();
        }).dimensions(CasinoUi.x(left, 266), CasinoUi.y(top, 151),
                CasinoUi.scale(55), CasinoUi.scale(22)).build());
        roleTab.visible = superAdmin;
        roleTab.active = tab != Tab.ROLES;
        ButtonWidget settings = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.settings"), button ->
                        client.setScreen(new CasinoSettingsScreen(this, api, machine.snapshot().settings(), machine::refresh)))
                .dimensions(CasinoUi.x(left, 325), CasinoUi.y(top, 151),
                        CasinoUi.scale(68), CasinoUi.scale(22)).build());
        settings.visible = superAdmin;
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.refresh"), button -> refresh())
                .dimensions(CasinoUi.x(left, 397), CasinoUi.y(top, 151),
                        CasinoUi.scale(64), CasinoUi.scale(22)).build());

        int count = itemCount();
        int pageSize = pageSize();
        int maximumPage = Math.max(0, (count - 1) / pageSize);
        page = Math.min(page, maximumPage);

        ButtonWidget previous = addDrawableChild(ButtonWidget.builder(CasinoText.literal("<"), button -> {
            page--;
            rebuild();
        }).dimensions(CasinoUi.x(left, 154), CasinoUi.y(top, 342),
                CasinoUi.scale(42), CasinoUi.scale(22)).build());
        previous.active = page > 0;
        addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.back"), button -> close())
                .dimensions(CasinoUi.x(left, 218), CasinoUi.y(top, 342),
                        CasinoUi.scale(164), CasinoUi.scale(22)).build());
        ButtonWidget next = addDrawableChild(ButtonWidget.builder(CasinoText.literal(">"), button -> {
            page++;
            rebuild();
        }).dimensions(CasinoUi.x(left, 404), CasinoUi.y(top, 342),
                CasinoUi.scale(42), CasinoUi.scale(22)).build());
        next.active = (page + 1) * pageSize < count;
        if (tab == Tab.ROLES) addRoleButtons(left, top);
        else if (tab == Tab.OPERATIONS) addTransactionButtons(left, top);
    }

    private int itemCount() {
        return switch (tab) {
            case OPERATIONS -> data.pending().size();
            case AUDIT -> data.audit().size();
            case ROLES -> roleProfiles().size();
        };
    }

    private int pageSize() {
        return switch (tab) {
            case OPERATIONS -> TRANSACTIONS_PER_PAGE;
            case AUDIT -> AUDIT_PER_PAGE;
            case ROLES -> ROLES_PER_PAGE;
        };
    }

    private void addTransactionButtons(int left, int top) {
        int start = page * TRANSACTIONS_PER_PAGE;
        for (int row = 0; row < Math.min(TRANSACTIONS_PER_PAGE, data.pending().size() - start); row++) {
            RemoteTransaction tx = data.pending().get(start + row);
            int y = CasinoUi.y(top, 200 + row * 45);
            boolean own = tx.userId().equals(profile().userId());
            if (tx.status() == TransactionStatus.PENDING) {
                ButtonWidget approve = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.approve"), button -> review(tx, true))
                        .dimensions(CasinoUi.x(left, 306), y, CasinoUi.scale(70), CasinoUi.scale(20)).build());
                ButtonWidget reject = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.reject"), button -> review(tx, false))
                        .dimensions(CasinoUi.x(left, 381), y, CasinoUi.scale(70), CasinoUi.scale(20)).build());
                approve.active = reject.active = !loading && !own;
            } else if (tx.type() == TransactionType.WITHDRAWAL) {
                ButtonWidget pay = addDrawableChild(ButtonWidget.builder(CasinoText.literal("/PAY"), button -> sendPay(tx))
                        .dimensions(CasinoUi.x(left, 306), y, CasinoUi.scale(70), CasinoUi.scale(20)).build());
                ButtonWidget paid = addDrawableChild(ButtonWidget.builder(CasinoText.tr("button.tropimon_casino.paid"), button -> markPaid(tx))
                        .dimensions(CasinoUi.x(left, 381), y, CasinoUi.scale(70), CasinoUi.scale(20)).build());
                pay.active = paid.active = !loading && !own;
            }
        }
    }

    private void addRoleButtons(int left, int top) {
        List<RemoteProfile> profiles = roleProfiles();
        int start = page * ROLES_PER_PAGE;
        for (int row = 0; row < Math.min(ROLES_PER_PAGE, profiles.size() - start); row++) {
            RemoteProfile profile = profiles.get(start + row);
            int y = CasinoUi.y(top, 181 + row * 25);
            boolean enabled = profile.role() == CasinoRole.ADMIN;
            ButtonWidget button = addDrawableChild(ButtonWidget.builder(
                            enabled ? CasinoText.tr("button.tropimon_casino.remove_admin")
                                    : CasinoText.tr("button.tropimon_casino.make_admin"),
                            ignored -> setAdmin(profile, !enabled))
                    .dimensions(CasinoUi.x(left, 321), y, CasinoUi.scale(128), CasinoUi.scale(20)).build());
            button.active = !loading && !profile.userId().equals(machine.snapshot().profile().userId());
        }
    }

    private void refresh() {
        loading = true;
        status = CasinoText.string("status.tropimon_casino.refreshing");
        api.adminSnapshot().whenComplete((value, error) -> client.execute(() -> {
            loading = false;
            if (error != null) status = CasinoApi.friendly(error);
            else {
                data = value;
                status = value.overdue() > 0 ? CasinoText.string("status.tropimon_casino.overdue", value.overdue())
                        : CasinoText.string("status.tropimon_casino.admin_queue_current");
            }
            rebuild();
        }));
    }

    private void review(RemoteTransaction tx, boolean approve) {
        loading = true;
        status = approve ? CasinoText.string("status.tropimon_casino.approving")
                : CasinoText.string("status.tropimon_casino.rejecting");
        api.review(tx.id(), approve, "casino_admin_review").whenComplete((ignored, error) ->
                client.execute(() -> {
                    if (error != null) {
                        loading = false;
                        status = CasinoApi.friendly(error);
                        rebuild();
                    } else refresh();
                }));
    }

    private void sendPay(RemoteTransaction tx) {
        RemoteProfile target = profile(tx.userId());
        if (target == null || client.player == null) {
            status = CasinoText.string("error.tropimon_casino.player_not_found");
            return;
        }
        client.setScreen(new ChatScreen("/pay " + target.minecraftName() + " " + tx.pokeDollars()));
        status = CasinoText.string("status.tropimon_casino.pay_prepared");
    }

    private void markPaid(RemoteTransaction tx) {
        loading = true;
        status = CasinoText.string("status.tropimon_casino.confirming_payment");
        api.markWithdrawalPaid(tx.id()).whenComplete((ignored, error) ->
                client.execute(() -> {
                    if (error != null) {
                        loading = false;
                        status = CasinoApi.friendly(error);
                        rebuild();
                    } else refresh();
                }));
    }

    private void setAdmin(RemoteProfile profile, boolean enabled) {
        loading = true;
        status = enabled ? CasinoText.string("status.tropimon_casino.appointing")
                : CasinoText.string("status.tropimon_casino.removing_role");
        api.setAdmin(profile.userId(), enabled).whenComplete((ignored, error) ->
                client.execute(() -> {
                    if (error != null) {
                        loading = false;
                        status = CasinoApi.friendly(error);
                        rebuild();
                    } else refresh();
                }));
    }

    private RemoteProfile profile(UUID userId) {
        return data.profiles().stream().filter(value -> value.userId().equals(userId)).findFirst().orElse(null);
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
            CasinoUi.panelHeading(context, textRenderer, left, top, CasinoText.tr("screen.tropimon_casino.administration"));
            if (tab == Tab.ROLES) drawRoles(context, left, top);
            else if (tab == Tab.AUDIT) drawAudit(context, left, top);
            else drawTransactions(context, left, top);
            context.drawCenteredTextWithShadow(textRenderer, CasinoText.literal(status),
                    left + CasinoUi.WIDTH / 2, top + 325,
                    data.overdue() > 0 ? 0xFFCA2020 : CasinoUi.BROWN);
        } finally {
            CasinoUi.end(context);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawTransactions(DrawContext context, int left, int top) {
        int start = page * TRANSACTIONS_PER_PAGE;
        for (int row = 0; row < Math.min(TRANSACTIONS_PER_PAGE, data.pending().size() - start); row++) {
            RemoteTransaction tx = data.pending().get(start + row);
            RemoteProfile profile = profile(tx.userId());
            String name = profile == null ? "?" : profile.minecraftName();
            Text kind = tx.type() == TransactionType.DEPOSIT ? CasinoText.tr("value.tropimon_casino.deposit")
                    : CasinoText.tr("value.tropimon_casino.withdrawal");
            Instant now = Instant.now();
            int color = tx.overdue(now) ? 0xFFCA2020 : CasinoUi.BROWN;
            context.drawTextWithShadow(textRenderer,
                    CasinoText.tr("line.tropimon_casino.admin_transaction", name, kind, tx.pokeDollars()),
                    left + 145, top + 185 + row * 45, color);
            if (tx.type() == TransactionType.WITHDRAWAL) {
                context.drawTextWithShadow(textRenderer,
                        CasinoText.tr("line.tropimon_casino.admin_status", status(tx.status()), deadline(tx, now)),
                        left + 145, top + 197 + row * 45,
                        tx.overdue(now) ? 0xFFCA2020 : tx.needsAttention(now) ? 0xFFD06000 : 0xFF7C4A20);
            } else context.drawTextWithShadow(textRenderer, status(tx.status()), left + 145,
                    top + 197 + row * 45, 0xFF7C4A20);
        }
        if (!loading && data.pending().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, CasinoText.tr("empty.tropimon_casino.admin_queue"),
                    left + CasinoUi.WIDTH / 2,
                    top + 245, CasinoUi.GREEN);
        }
    }

    private void drawRoles(DrawContext context, int left, int top) {
        List<RemoteProfile> profiles = roleProfiles();
        int start = page * ROLES_PER_PAGE;
        for (int row = 0; row < Math.min(ROLES_PER_PAGE, profiles.size() - start); row++) {
            RemoteProfile profile = profiles.get(start + row);
            context.drawTextWithShadow(textRenderer, CasinoText.literal(profile.minecraftName()), left + 145,
                    top + 187 + row * 25, CasinoUi.BROWN);
            context.drawTextWithShadow(textRenderer, profile.role() == CasinoRole.ADMIN
                            ? CasinoText.tr("value.tropimon_casino.admin") : CasinoText.tr("value.tropimon_casino.player"),
                    left + 258, top + 187 + row * 25,
                    profile.role() == CasinoRole.ADMIN ? CasinoUi.GREEN : 0xFF7C4A20);
        }
    }

    private void drawAudit(DrawContext context, int left, int top) {
        int start = page * AUDIT_PER_PAGE;
        int y = top + 185;
        for (int index = start; index < Math.min(start + AUDIT_PER_PAGE, data.audit().size()); index++) {
            RemoteAudit entry = data.audit().get(index);
            RemoteProfile actor = profile(entry.actorUserId());
            String actorName = actor == null ? "?" : actor.minecraftName();
            context.drawTextWithShadow(textRenderer,
                    CasinoText.tr("line.tropimon_casino.audit", DATE.format(entry.createdAt()), actorName,
                            auditAction(entry.action())), left + 145, y, CasinoUi.BROWN);
            y += 25;
        }
        if (!loading && data.audit().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, CasinoText.tr("empty.tropimon_casino.audit"),
                    left + CasinoUi.WIDTH / 2, top + 245, 0xFF7C4A20);
        }
    }

    private static Text deadline(RemoteTransaction transaction, Instant now) {
        if (transaction.overdue(now)) return CasinoText.tr("status.tropimon_casino.withdrawal_overdue");
        return CasinoText.tr("status.tropimon_casino.withdrawal_deadline", transaction.hoursUntilDeadline(now));
    }

    private static Text status(TransactionStatus status) {
        return switch (status) {
            case PENDING -> CasinoText.tr("status.tropimon_casino.pending");
            case APPROVED -> CasinoText.tr("status.tropimon_casino.approved");
            case PAID -> CasinoText.tr("status.tropimon_casino.paid");
            case REJECTED -> CasinoText.tr("status.tropimon_casino.rejected");
        };
    }

    private static Text auditAction(String action) {
        return switch (action) {
            case "bootstrap_super_admin" -> CasinoText.tr("audit.tropimon_casino.bootstrap");
            case "review_transaction" -> CasinoText.tr("audit.tropimon_casino.review");
            case "mark_withdrawal_paid" -> CasinoText.tr("audit.tropimon_casino.payment");
            case "set_admin" -> CasinoText.tr("audit.tropimon_casino.role");
            case "update_settings" -> CasinoText.tr("audit.tropimon_casino.settings");
            default -> CasinoText.literal(action);
        };
    }

    private List<RemoteProfile> roleProfiles() {
        return data.profiles().stream().filter(profile -> profile.role() != CasinoRole.SUPER_ADMIN).toList();
    }

    private RemoteProfile profile() {
        return machine.snapshot().profile();
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
