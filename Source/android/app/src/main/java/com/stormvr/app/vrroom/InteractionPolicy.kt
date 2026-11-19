package com.stormvr.app.vrroom

import kotlin.math.*

/** Pure policies shared by the camera, render and UI threads; all times are monotonic. */
object HandSamplePolicy {
    fun cameraX(x: Float,y: Float,rotation: Int) = when(rotation) { 90->y; 180->-x; 270->-y; else->x }
    fun cameraY(x: Float,y: Float,rotation: Int) = when(rotation) { 90->x; 180->y; 270->-x; else->-y }
    const val MAX_AGE_NS = 220_000_000L
    fun fresh(now: Long, sample: Long) = sample > 0L && now >= sample && now - sample <= MAX_AGE_NS
    fun predictionSeconds(now: Long, sample: Long, latency: Long): Float {
        if (!fresh(now, sample) || now - sample > 80_000_000L || latency > 100_000_000L) return 0f
        return ((now - sample) / 1e9f).coerceIn(0f, .025f)
    }
    fun plausible(points: FloatArray, previous: FloatArray?, dt: Float): Boolean {
        if (points.size != 63 || points.any { !it.isFinite() }) return false
        if (previous != null) {
            var d = 0f
            for (c in 0..2) d += (points[c] - previous[c]).pow(2)
            if (sqrt(d) > .06f + 3f * dt.coerceIn(0f, .1f)) return false
        }
        for (finger in 0..4) for (joint in 1..3) {
            val a = (1 + finger * 4 + joint) * 3; val b = a - 3
            var d = 0f
            for (c in 0..2) d += (points[a+c] - points[b+c]).pow(2)
            if (d > .12f * .12f) return false
        }
        return true
    }
    fun assignment(a: FloatArray,b: FloatArray,left: FloatArray,right: FloatArray): Boolean {
        fun cost(p: FloatArray,q: FloatArray): Float = (0..2).sumOf { (p[it]-q[it]).toDouble().pow(2) }.toFloat()
        return cost(a,left)+cost(b,right) <= cost(a,right)+cost(b,left)
    }
}

/** Hysteresis with time debounce, independent of inference frame rate. Loss disarms until open. */
class PinchLatch {
    var down = false; private set
    private var candidate = -1L
    private var armed = false
    fun reset() { down=false; candidate=-1; armed=false }
    fun update(ratio: Float, valid: Boolean, nowMs: Long, threshold: Float = .38f): Boolean {
        if (!valid || !ratio.isFinite()) { reset(); return false }
        val on = threshold.coerceIn(.25f,.5f); val off = on + .18f
        if (!armed) { if (ratio >= off) armed=true; return false }
        val change = if (down) ratio > off else ratio < on
        if (!change) candidate=-1
        else if (candidate < 0) candidate=nowMs
        else if (nowMs-candidate >= if(down) 35L else 45L) { down=!down; candidate=-1 }
        return down
    }
}

/** One stable target per dwell. Small excursions have a 100ms grace; rearm requires leaving. */
class DwellSelector {
    private var target: String? = null
    private var since = 0L
    private var lastInside = 0L
    private var fired = false
    private var x = 0f; private var y = 0f
    var progress = 0f; private set
    fun reset() { target=null; fired=false; progress=0f }
    fun update(id: String?, px: Float, py: Float, now: Long, duration: Long, tolerance: Float): Boolean {
        if (id == null) { if (now-lastInside > 100) reset(); return false }
        if (target != id || hypot(px-x,py-y) > tolerance) {
            target=id; since=now; fired=false; x=px; y=py
        }
        lastInside=now
        progress=if(fired) 0f else ((now-since).toFloat()/duration.coerceIn(800,1200)).coerceIn(0f,1f)
        if (!fired && progress >= 1f) { fired=true; return true }
        return false
    }
}

/** Captured target survives ray misses at release; tracking loss always cancels. */
class ClickCapture {
    enum class Phase { IDLE, PRESSED, DRAGGING }
    enum class Event { NONE, DOWN, MOVE, UP, CANCEL }
    var phase=Phase.IDLE; private set
    var x=0f; private set
    var y=0f; private set
    private var anchorX=0f; private var anchorY=0f
    fun update(down: Boolean, valid: Boolean, hit: Boolean, px: Float, py: Float, slop: Float): Event {
        if (phase != Phase.IDLE && !valid) { phase=Phase.IDLE; return Event.CANCEL }
        if (phase == Phase.IDLE) {
            if (!down || !valid || !hit) return Event.NONE
            x=px; y=py; anchorX=px; anchorY=py; phase=Phase.PRESSED; return Event.DOWN
        }
        if (!down) { phase=Phase.IDLE; return Event.UP }
        if (hit && (phase==Phase.DRAGGING || hypot(px-anchorX,py-anchorY)>slop)) {
            phase=Phase.DRAGGING; x=px; y=py; return Event.MOVE
        }
        return Event.NONE
    }
    fun cancel(): Event { val e=if(phase==Phase.IDLE) Event.NONE else Event.CANCEL; phase=Phase.IDLE; return e }
}

object SurfaceCoordinates {
    fun pixel(value: Float, center: Float, size: Float, pixels: Int, invert: Boolean=false): Float {
        val u = if(invert) .5f-(value-center)/size else .5f+(value-center)/size
        return u.coerceIn(0f,1f)*(pixels-1).coerceAtLeast(0)
    }
}
