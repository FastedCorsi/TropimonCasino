package fr.tropimon.casino;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class TropimonCasinoClient implements ClientModInitializer {
    private static final int KEY_BINDING_VERSION = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger("tropimon_casino");
    private static final CasinoConfig CONFIG = CasinoConfig.load();
    private static final CasinoApi API = new CasinoApi(CONFIG);
    private boolean keyBindingChecked;
    private boolean openingRequested;
    private CasinoScreen openingScreen;
    private int openingScreenTicks;

    @Override
    public void onInitializeClient() {
        TropimonSelfUpdater.start(LOGGER);
        KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tropimon_casino.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_APOSTROPHE,
                "category.tropimon_casino"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            migrateLegacyKeyBinding(client, open);
            boolean requested = false;
            while (open.wasPressed()) {
                requested = true;
            }
            if (requested && client.currentScreen == null) openingRequested = true;
            if (openingRequested && !open.isPressed()) {
                openingRequested = false;
                if (client.currentScreen == null) openCasino(client);
            }
            observeOpening(client);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("casino").executes(context -> {
                    openCasino(context.getSource().getClient());
                    return 1;
                })));
    }

    private void migrateLegacyKeyBinding(MinecraftClient client, KeyBinding open) {
        if (keyBindingChecked) return;
        keyBindingChecked = true;
        if (CONFIG.keyBindingVersion >= KEY_BINDING_VERSION) return;

        if ("key.keyboard.k".equals(open.getBoundKeyTranslationKey())) {
            open.setBoundKey(InputUtil.fromTranslationKey("key.keyboard.apostrophe"));
            KeyBinding.updateKeysByCode();
            client.options.write();
        }
        CONFIG.keyBindingVersion = KEY_BINDING_VERSION;
        CONFIG.save();
    }

    private void openCasino(MinecraftClient client) {
        if (!(client.currentScreen instanceof CasinoScreen)) {
            CasinoScreen screen = new CasinoScreen(API);
            client.setScreen(screen);
            openingScreen = screen;
            openingScreenTicks = 0;
            LOGGER.info("Casino screen opened after the key was released.");
        }
    }

    private void observeOpening(MinecraftClient client) {
        if (openingScreen == null) return;
        if (client.currentScreen != openingScreen) {
            Screen replacement = client.currentScreen;
            LOGGER.warn("Casino screen replaced after {} tick(s) by {}.", openingScreenTicks,
                    replacement == null ? "the game" : replacement.getClass().getName());
            openingScreen = null;
            return;
        }
        openingScreenTicks++;
        if (openingScreenTicks == 20) {
            LOGGER.info("Casino screen remained active for its first 20 ticks.");
            openingScreen = null;
        }
    }
}

