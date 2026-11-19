package com.stormvr.app.vrroom

import org.junit.Assert.*
import org.junit.Test

class HandRenderRecoveryTest {
    @Test fun briefDropoutKeepsVisualInterpolation() {
        assertTrue(HandRenderRecovery.shouldBlend(1_000_000_000L, 1_080_000_000L))
        assertTrue(HandRenderRecovery.shouldBlend(1_000_000_000L, 1_220_000_000L))
    }

    @Test fun staleOrInvalidObservationsResetFilter() {
        assertFalse(HandRenderRecovery.shouldBlend(0L, 1_000_000_000L))
        assertFalse(HandRenderRecovery.shouldBlend(1_000_000_000L, 1_220_000_001L))
        assertFalse(HandRenderRecovery.shouldBlend(1_000_000_000L, 999_999_999L))
    }
}
