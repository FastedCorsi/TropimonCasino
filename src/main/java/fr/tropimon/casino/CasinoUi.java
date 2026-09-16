package fr.tropimon.casino;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

final class CasinoUi {
    private static final float SCALE = 0.82F;
    static final int WIDTH = 600;
    static final int HEIGHT = 470;
    static final int DISPLAY_WIDTH = scale(WIDTH);
    static final int DISPLAY_HEIGHT = scale(HEIGHT);
    static final int PANEL_X = 117;
    static final int PANEL_Y = 119;
    static final int PANEL_WIDTH = 354;
    static final int PANEL_HEIGHT = 262;
    static final int LEFT_BUTTON_X = 195;
    static final int CENTER_BUTTON_X = 298;
    static final int RIGHT_BUTTON_X = 401;
    static final int BUTTON_Y = 433;
    static final int REEL_WIDTH = 78;
    static final int REEL_HEIGHT = 150;

    static final Identifier FRAME = Identifier.of("tropimon_casino", "textures/gui/tropimon_cyan_frame.png");
    static final Identifier MACHINE = Identifier.of("tropimon_casino", "textures/gui/casino_machine.png");
    static final int GOLD = 0xFFFFB51B;
    static final int BROWN = 0xFF542000;
    static final int CREAM = 0xFFFFF1D6;
    static final int GREEN = 0xFF16843A;
    private static final int CYAN = 0xFF18C8D7;

    private CasinoUi() {}

    static int left(int width) { return (width - DISPLAY_WIDTH) / 2; }
    static int top(int height) { return (height - DISPLAY_HEIGHT) / 2; }
    static int scale(int value) { return Math.round(value * SCALE); }
    static int x(int left, int offset) { return left + scale(offset); }
    static int y(int top, int offset) { return top + scale(offset); }

    static void begin(DrawContext context, int left, int top) {
        context.getMatrices().push();
        context.getMatrices().translate(left, top, 0);
        context.getMatrices().scale(SCALE, SCALE, 1.0F);
        context.getMatrices().translate(-left, -top, 0);
    }

    static void end(DrawContext context) {
        context.getMatrices().pop();
    }

    static void background(DrawContext context, int left, int top) {
        background(context, left, top, 0);
    }

    static void background(DrawContext context, int left, int top, int activeIndicator, long elapsed) {
        context.enableScissor(left + scale(16), top + scale(14),
                left + DISPLAY_WIDTH - scale(16), top + DISPLAY_HEIGHT - scale(12));
        try {
            scaledTexture(context, MACHINE, left - 13, top, 626, 470,
                    0, 0, 1448, 1086, 1448, 1086);
        } finally {
            context.disableScissor();
        }
        if (activeIndicator >= 1 && activeIndicator <= 3) {
            glowingIndicator(context, left, top, activeIndicator, elapsed);
        }
        frameBorder(context, left, top);
    }

    static void background(DrawContext context, int left, int top, int activeIndicator) {
        background(context, left, top, activeIndicator, 0L);
    }

    static void frame(DrawContext context, int left, int top) {
        background(context, left, top);
    }

    private static void frameBorder(DrawContext context, int left, int top) {
        int horizontalWidth = WIDTH - 32;
        int verticalHeight = HEIGHT - 26;

        scaledTexture(context, FRAME, left + 16, top, horizontalWidth, 14,
                16, 0, 313, 14, 345, 205);
        scaledTexture(context, FRAME, left + 16, top + HEIGHT - 12, horizontalWidth, 12,
                16, 193, 313, 12, 345, 205);
        scaledTexture(context, FRAME, left, top + 14, 16, verticalHeight,
                0, 14, 16, 179, 345, 205);
        scaledTexture(context, FRAME, left + WIDTH - 16, top + 14, 16, verticalHeight,
                329, 14, 16, 179, 345, 205);
        scaledTexture(context, FRAME, left, top, 16, 14,
                0, 0, 16, 14, 345, 205);
        scaledTexture(context, FRAME, left + WIDTH - 16, top, 16, 14,
                329, 0, 16, 14, 345, 205);
        scaledTexture(context, FRAME, left, top + HEIGHT - 12, 16, 12,
                0, 193, 16, 12, 345, 205);
        scaledTexture(context, FRAME, left + WIDTH - 16, top + HEIGHT - 12, 16, 12,
                329, 193, 16, 12, 345, 205);
    }

    private static void scaledTexture(DrawContext context, Identifier texture,
                                      int x, int y, int width, int height,
                                      int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                                      int textureWidth, int textureHeight) {
        context.getMatrices().push();
        try {
            context.getMatrices().translate(x, y, 0);
            context.getMatrices().scale(width / (float) sourceWidth, height / (float) sourceHeight, 1.0f);
            context.drawTexture(texture, 0, 0, sourceX, sourceY, sourceWidth, sourceHeight, textureWidth, textureHeight);
        } finally {
            context.getMatrices().pop();
        }
    }

    static void title(DrawContext context, TextRenderer renderer, int left, int top, net.minecraft.text.Text title) {
        context.drawCenteredTextWithShadow(renderer, title.copy().formatted(Formatting.BOLD),
                left + WIDTH / 2, top + 45, 0xFFFFFFFF);
    }

    static void meter(DrawContext context, TextRenderer renderer, int x, int y, net.minecraft.text.Text title, long value) {
        meter(context, renderer, x, y, title, CasinoText.literal(value));
    }

    static void meter(DrawContext context, TextRenderer renderer, int x, int y,
                      net.minecraft.text.Text title, net.minecraft.text.Text value) {
        context.fill(x, y, x + 96, y + 30, 0xD9FFE2A8);
        border(context, x, y, 96, 30, 0xFF9A4B00);
        context.drawCenteredTextWithShadow(renderer, title, x + 48, y + 3, BROWN);
        context.drawCenteredTextWithShadow(renderer, value, x + 48, y + 16, GREEN);
    }

    static void reel(DrawContext context, TextRenderer renderer, int left, int top, int x, int y,
                     SlotSymbol selected, int reel, boolean turning, long elapsed) {
        context.fill(x - 3, y - 3, x + REEL_WIDTH + 3, y + REEL_HEIGHT + 3, 0xFF9A4B00);
        context.fill(x, y, x + REEL_WIDTH, y + REEL_HEIGHT, 0xFFF7F2E8);
        context.enableScissor(left + scale(x - left), top + scale(y - top),
                left + scale(x - left + REEL_WIDTH), top + scale(y - top + REEL_HEIGHT));
        try {
            int step = 50;
            long period = 72L + reel * 9L;
            int centerIndex = turning
                    ? Math.floorMod((int) (elapsed / period) + reel * 2, SlotSymbol.values().length)
                    : selected.ordinal();
            float phase = turning ? (elapsed % period) / (float) period * step : 0.0f;
            for (int row = -2; row <= 2; row++) {
                SlotSymbol symbol = SlotSymbol.values()[Math.floorMod(centerIndex + row, SlotSymbol.values().length)];
                int centerY = Math.round(y + REEL_HEIGHT / 2.0f + row * step + phase);
                drawSymbol(context, symbol, x + REEL_WIDTH / 2, centerY, 42);
            }
        } finally {
            context.disableScissor();
        }
        context.fill(x, y + 49, x + REEL_WIDTH, y + 51, 0xFFCEB989);
        context.fill(x, y + 99, x + REEL_WIDTH, y + 101, 0xFFCEB989);
        border(context, x + 2, y + 50, REEL_WIDTH - 4, 50, CYAN);
        context.drawCenteredTextWithShadow(renderer, turning ? CasinoText.literal("•••") : selected.label(),
                x + REEL_WIDTH / 2, y + REEL_HEIGHT + 5, turning ? 0xFF9A4B00 : BROWN);
    }

    static void panelHeading(DrawContext context, TextRenderer renderer, int left, int top, net.minecraft.text.Text heading) {
        context.drawCenteredTextWithShadow(renderer, heading, left + PANEL_X + PANEL_WIDTH / 2,
                top + PANEL_Y + 9, BROWN);
    }

    static void panelStatus(DrawContext context, TextRenderer renderer, int left, int top,
                            net.minecraft.text.Text status, int color) {
        if (!status.getString().isBlank()) {
            context.drawCenteredTextWithShadow(renderer, status, left + PANEL_X + PANEL_WIDTH / 2,
                    top + PANEL_Y + PANEL_HEIGHT - 20, color);
        }
    }

    static void symbol(DrawContext context, SlotSymbol symbol, int centerX, int centerY, int size) {
        drawSymbol(context, symbol, centerX, centerY, size);
    }

    private static void glowingIndicator(DrawContext context, int left, int top, int number, long elapsed) {
        int[] rows = switch (number) {
            case 3 -> new int[]{134, 363};
            case 2 -> new int[]{189, 310};
            default -> new int[]{251};
        };
        boolean bright = Math.floorMod(elapsed / 110L, 4L) < 2L;
        for (int y : rows) {
            glowingDigit(context, left + 76, top + y, number, bright);
            glowingDigit(context, left + 523, top + y, number, bright);
        }
    }

    private static void glowingDigit(DrawContext context, int centerX, int centerY, int number, boolean bright) {
        String[] pixels = switch (number) {
            case 1 -> new String[]{"01100", "11100", "01100", "01100", "01100", "01100", "11111"};
            case 2 -> new String[]{"11110", "00011", "00011", "11110", "11000", "11000", "11111"};
            default -> new String[]{"11110", "00011", "00011", "01110", "00011", "00011", "11110"};
        };
        int pixelSize = 3;
        int startX = centerX - pixels[0].length() * pixelSize / 2;
        int startY = centerY - pixels.length * pixelSize / 2;
        int glow = bright ? 0x99FFF09A : 0x55FFD35A;
        int core = bright ? 0xFFFFFFFF : 0xFFFFF1B8;
        for (int row = 0; row < pixels.length; row++) {
            for (int column = 0; column < pixels[row].length(); column++) {
                if (pixels[row].charAt(column) != '1') continue;
                int x = startX + column * pixelSize;
                int y = startY + row * pixelSize;
                context.fill(x - 1, y - 1, x + pixelSize + 1, y + pixelSize + 1, glow);
                context.fill(x, y, x + pixelSize, y + pixelSize, core);
            }
        }
    }

    private static void drawSymbol(DrawContext context, SlotSymbol symbol, int centerX, int centerY, int size) {
        if (CasinoPokemonRenderer.draw(context, symbol, centerX - size / 2, centerY - size / 2, size)) return;
        float scale = size / (float) symbol.cropSize();
        context.getMatrices().push();
        try {
            context.getMatrices().translate(centerX - size / 2.0f, centerY - size / 2.0f, 0);
            context.getMatrices().scale(scale, scale, 1.0f);
            context.drawTexture(symbol.texture(), 0, 0, symbol.cropX(), symbol.cropY(), symbol.cropSize(), symbol.cropSize(),
                    symbol.textureSize(), symbol.textureSize());
        } finally {
            context.getMatrices().pop();
        }
    }

    private static void border(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 2, color);
        context.fill(x, y + height - 2, x + width, y + height, color);
        context.fill(x, y, x + 2, y + height, color);
        context.fill(x + width - 2, y, x + width, y + height, color);
    }
}
