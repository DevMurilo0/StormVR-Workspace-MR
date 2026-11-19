package com.stormvr.app.vrroom

/** Preserve only visual interpolation state across brief detector dropouts.
 * Input validity and clicks still follow HandSamplePolicy, never this helper. */
object HandRenderRecovery {
    const val MAX_BLEND_GAP_NS = 220_000_000L
    fun shouldBlend(lastObservedNs: Long, nowNs: Long): Boolean =
        lastObservedNs > 0 && nowNs >= lastObservedNs && nowNs - lastObservedNs <= MAX_BLEND_GAP_NS
}
