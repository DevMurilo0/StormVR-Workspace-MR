package com.stormvr.app.vrroom

import kotlin.math.hypot

/** Exclusive temporal intent. Point -> press -> click OR capture -> release; loss never clicks. */
class SpatialGesture {
    var phase=FingertipGesture.Phase.IDLE; private set
    var target: String?=null; private set
    private var kind=FingertipGesture.Kind.CLICK
    private var armed=false
    private var downSince=-1L
    private var openSince=-1L
    private var lastSeen=-1L
    private var x=0f; private var y=0f
    private var cancelledClick=false
    private var clickFired=false
    private var hoverSince=-1L
    private var dwellX=0f; private var dwellY=0f
    private var dwellCaptured=false
    private var dwellLatchedId: String?=null
    private var notPointingSince=-1L
    var progress=0f; private set
    val captured get()=phase==FingertipGesture.Phase.DRAGGING || phase==FingertipGesture.Phase.RESIZING
    val ownsTarget get()=captured || downSince>=0
    fun reset() {
        phase=FingertipGesture.Phase.IDLE; target=null; armed=false; downSince=-1
        openSince=-1; lastSeen=-1; hoverSince=-1; dwellCaptured=false; dwellLatchedId=null
        notPointingSince=-1; progress=0f
    }
    fun release() {
        phase=FingertipGesture.Phase.RELEASED; armed=false; downSince=-1
        openSince=-1; dwellCaptured=false; hoverSince=-1; notPointingSince=-1; progress=0f
        // A completed pinch must not immediately become a second dwell click on the same target.
        dwellLatchedId=target
    }
    fun update(id: String?,action: FingertipGesture.Kind,px: Float,py: Float,observed: Boolean,
               pointing: Boolean,grip: Boolean,blocked: Boolean,now: Long,tolerance: Float,
               allowDwell: Boolean=false,dwellMs: Long=1000L): Boolean {
        if(blocked) { reset(); return false }
        if(!observed) {
            cancelledClick=true
            if(lastSeen>=0 && now-lastSeen>220) release()
            if(!ownsTarget) { armed=false; target=null; phase=FingertipGesture.Phase.IDLE; hoverSince=-1; progress=0f }
            return false
        }
        if(lastSeen>=0 && now-lastSeen>220) release()
        lastSeen=now
        if(dwellCaptured) {
            // Hover capture is released by folding the index, not by making a pinch.
            if(!pointing) {
                if(notPointingSince<0) notPointingSince=now
                if(now-notPointingSince>=120) release()
            } else notPointingSince=-1
            return false
        }
        if(downSince>=0) {
            if(hypot(px-x,py-y)>tolerance) cancelledClick=true
            if(!grip) {
                if(openSince<0) openSince=now
                if(now-openSince>=80) {
                    // In the combined interaction mode, Enter fires while the fingers touch.
                    // Legacy spatial tests/mode continue to click on release.
                    val click=kind==FingertipGesture.Kind.CLICK && !clickFired && !cancelledClick &&
                        now-downSince in 80..500
                    release(); return click
                }
            } else openSince=-1
            if(allowDwell && grip && kind==FingertipGesture.Kind.CLICK &&
                !clickFired && !cancelledClick && now-downSince>=80) {
                clickFired=true
                return true // pinch is Enter, once; holding the fingers together never repeats
            }
            if(grip && now-downSince>=80 && !captured && kind!=FingertipGesture.Kind.CLICK) {
                phase=if(kind==FingertipGesture.Kind.RESIZE) FingertipGesture.Phase.RESIZING else FingertipGesture.Phase.DRAGGING
            }
            return false
        }
        if(grip) {
            // A pinch only starts against a target previously hovered by an extended index.
            if(armed && id==target) {
                downSince=now; openSince=-1; cancelledClick=false; clickFired=false; hoverSince=-1
                progress=0f; phase=FingertipGesture.Phase.ARMED
            } else release()
            return false
        }
        if(!pointing || id==null) {
            if(!pointing && armed && id!=null) {
                if(notPointingSince<0) notPointingSince=now
                if(now-notPointingSince<120) return false
            }
            armed=false; target=null; hoverSince=-1; notPointingSince=-1
            dwellLatchedId=null; progress=0f; phase=FingertipGesture.Phase.IDLE
            return false
        }
        notPointingSince=-1
        val moved=target!=id || hypot(px-x,py-y)>tolerance
        if(moved || !armed) {
            target=id; kind=action; x=px; y=py; armed=true
            hoverSince=now; progress=0f
            if(moved) dwellLatchedId=null
            phase=FingertipGesture.Phase.HOVER
            return false
        }
        if(allowDwell && dwellLatchedId!=id) {
            if(hoverSince<0) hoverSince=now
            val duration=dwellMs.coerceIn(1000L,2500L)
            progress=((now-hoverSince).toFloat()/duration).coerceIn(0f,1f)
            if(progress>=.55f) phase=FingertipGesture.Phase.ARMED
            if(now-hoverSince>=duration) {
                progress=0f; dwellLatchedId=id
                if(kind==FingertipGesture.Kind.CLICK) {
                    phase=FingertipGesture.Phase.RELEASED
                    return true // Enter exactly once per stable pointing session
                }
                dwellCaptured=true
                phase=if(kind==FingertipGesture.Kind.RESIZE) FingertipGesture.Phase.RESIZING else FingertipGesture.Phase.DRAGGING
            }
        } else if(dwellLatchedId==id) phase=FingertipGesture.Phase.RELEASED
        else phase=FingertipGesture.Phase.HOVER
        return false
    }
}

object SpatialPose {
    /** Three curled fingers provide grip evidence. Thumb/index pinch remains supported. */
    fun gripping(p: FloatArray,wasGripping: Boolean,pinch: Boolean): Boolean {
        if(p.size!=63 || p.any { !it.isFinite() }) return false
        if(pinch) return true
        val palm=SpatialHandEngine.length(p,0,9)
        if(palm<.025f) return false
        return intArrayOf(12,16,20).count { tip ->
            SpatialHandEngine.length(p,tip,0)/palm < if(wasGripping) 1.28f else 1.08f
        }>=3
    }
}
