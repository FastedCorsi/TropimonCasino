package fr.tropimon.casino;

final class ReelAnimation {
    private static final long QUEUED_STOP_DELAY = 140L;
    private static final long QUEUED_STOP_STAGGER = 180L;
    private static final long COUNTDOWN_STEP = 280L;

    private ReelAnimation() {}

    static int activeIndicator(boolean spinning, long elapsed, long[] stops) {
        if (!spinning) return 0;
        if (stops.length != 3) throw new IllegalArgumentException("Trois rouleaux requis");
        boolean aReelIsTurning = false;
        for (long stop : stops) aReelIsTurning |= turning(elapsed, stop);
        if (!aReelIsTurning) return 0;
        return 3 - (int) Math.floorMod(elapsed / COUNTDOWN_STEP, 3L);
    }

    static long queuedStopElapsed(int reel, long resultReadyElapsed) {
        if (reel < 0 || reel > 2) throw new IndexOutOfBoundsException(reel);
        return resultReadyElapsed + QUEUED_STOP_DELAY + reel * QUEUED_STOP_STAGGER;
    }

    static boolean turning(long elapsed, long stopElapsed) {
        return stopElapsed < 0 || elapsed < stopElapsed;
    }

    static boolean finished(long elapsed, long[] stops) {
        if (stops.length != 3) throw new IllegalArgumentException("Trois rouleaux requis");
        long latest = Math.max(stops[0], Math.max(stops[1], stops[2]));
        return stops[0] >= 0 && stops[1] >= 0 && stops[2] >= 0 && elapsed >= latest + 100L;
    }
}
