package fr.tropimon.casino;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReelAnimationTest {
    @Test void countdownRunsOnlyWhileAReelIsActuallyTurning() {
        assertEquals(0, ReelAnimation.activeIndicator(false, 0, new long[]{-1, -1, -1}));
        assertEquals(3, ReelAnimation.activeIndicator(true, 0, new long[]{-1, -1, -1}));
        assertEquals(2, ReelAnimation.activeIndicator(true, 280, new long[]{-1, -1, -1}));
        assertEquals(1, ReelAnimation.activeIndicator(true, 560, new long[]{-1, -1, -1}));
        assertEquals(3, ReelAnimation.activeIndicator(true, 840, new long[]{-1, -1, -1}));
        assertEquals(0, ReelAnimation.activeIndicator(true, 900, new long[]{100, 200, 300}));
    }

    @Test void reelsNeverStopWithoutAPlayerStop() {
        long afterSeveralDays = Long.MAX_VALUE / 2;
        assertTrue(ReelAnimation.turning(afterSeveralDays, -1));
        assertFalse(ReelAnimation.finished(afterSeveralDays, new long[]{-1, -1, -1}));
        assertFalse(ReelAnimation.finished(afterSeveralDays, new long[]{100, 200, -1}));
    }

    @Test void reelsTurnUntilAResultExistsAndStopOneAfterAnother() {
        long ready = 400;
        long first = ReelAnimation.queuedStopElapsed(0, ready);
        long second = ReelAnimation.queuedStopElapsed(1, ready);
        long third = ReelAnimation.queuedStopElapsed(2, ready);
        assertTrue(first < second && second < third);
        assertFalse(ReelAnimation.turning(first, first));
        assertTrue(ReelAnimation.turning(first, second));
        assertTrue(ReelAnimation.finished(third + 100, new long[]{first, second, third}));
    }
}
