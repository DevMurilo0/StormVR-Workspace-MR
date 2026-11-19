package com.stormvr.app.vrroom

import org.junit.Assert.*
import org.junit.Test

class SpatialDualGestureTest {
    private fun frame(g: SpatialGesture, ms: Long, id: String? = "button", kind: FingertipGesture.Kind = FingertipGesture.Kind.CLICK,
                      pinch: Boolean = false, pointing: Boolean = true, seen: Boolean = true, x: Float = 0f) =
        g.update(id,kind,x,0f,seen,pointing,pinch,false,ms,.04f,allowDwell=true,dwellMs=1000)

    @Test fun indexStaysOneSecondThenEntersOnlyOnceUntilMovement() {
        val g=SpatialGesture()
        assertFalse(frame(g,0))
        for(t in 100L..900L step 100) assertFalse(frame(g,t))
        assertFalse(frame(g,999))
        assertTrue(frame(g,1000))
        for(t in 1100L..1500L step 100) assertFalse(frame(g,t))
        assertFalse(frame(g,1700,x=.15f))
        for(t in 1800L..2600L step 100) assertFalse(frame(g,t,x=.15f))
        assertFalse(frame(g,2699,x=.15f))
        assertTrue(frame(g,2700,x=.15f))
    }

    @Test fun fingerHoverOnMoveBarBecomesDragAfterOneSecond() {
        val g=SpatialGesture()
        frame(g,0,kind=FingertipGesture.Kind.MOVE)
        for(t in 100L..1000L step 100) frame(g,t,kind=FingertipGesture.Kind.MOVE)
        assertTrue(g.captured)
        assertEquals(FingertipGesture.Phase.DRAGGING,g.phase)
        frame(g,1040,kind=FingertipGesture.Kind.MOVE,x=.5f)
        assertTrue(g.captured)
        frame(g,1100,kind=FingertipGesture.Kind.MOVE,pointing=false)
        frame(g,1221,kind=FingertipGesture.Kind.MOVE,pointing=false)
        assertFalse(g.captured)
    }

    @Test fun fingerHoverOnCornerResizesAndPinchOnCornerAlsoResizes() {
        val kind=FingertipGesture.Kind.RESIZE
        val d=SpatialGesture()
        frame(d,0,kind=kind)
        for(t in 100L..1100L step 100) frame(d,t,kind=kind)
        assertEquals(FingertipGesture.Phase.RESIZING,d.phase)
        val p=SpatialGesture()
        frame(p,0,kind=kind)
        frame(p,100,kind=kind,pinch=true,pointing=false)
        frame(p,181,kind=kind,pinch=true,pointing=false)
        assertTrue(p.captured)
        frame(p,230,kind=kind,pinch=false,pointing=false)
        frame(p,311,kind=kind,pinch=false,pointing=false)
        assertFalse(p.captured)
    }

    @Test fun pinchAfterHoverConfirmsOnlyOnRelease() {
        val g=SpatialGesture()
        frame(g,0)
        assertFalse(frame(g,100,pinch=true,pointing=false))
        assertFalse(frame(g,170,pinch=true,pointing=false))
        assertFalse(frame(g,230,pinch=false,pointing=false))
        assertTrue(frame(g,310,pinch=false,pointing=false))
        assertFalse(frame(g,330,pinch=false))
    }

    @Test fun lostTrackingCannotConfirmAWaitingDwell() {
        val g=SpatialGesture()
        frame(g,0)
        frame(g,500,seen=false)
        assertFalse(frame(g,1100))
        assertFalse(frame(g,1500))
    }

    @Test fun pinchAtStartupDoesNotSelectRandomTarget() {
        val g=SpatialGesture()
        frame(g,0,pinch=true)
        frame(g,140,pinch=true)
        assertFalse(g.ownsTarget)
    }
    @Test fun dwellThenPinchCannotConfirmTheSameHoverTwice() {
        val g=SpatialGesture()
        var clicks=0
        for(t in 0L..1000L step 100) if(frame(g,t)) clicks++
        assertEquals(1,clicks)
        for(t in 1100L..1400L step 100) if(frame(g,t,pinch=true)) clicks++
        for(t in 1500L..2800L step 100) if(frame(g,t)) clicks++
        assertEquals(1,clicks)
        frame(g,2900,x=.2f)
        for(t in 3000L..3900L step 100) if(frame(g,t,x=.2f)) clicks++
        assertEquals(2,clicks)
    }

    @Test fun briefTrackingLossCannotRearmAnAlreadyConfirmedTarget() {
        val g=SpatialGesture()
        var clicks=0
        for(t in 0L..1000L step 100) if(frame(g,t)) clicks++
        assertFalse(frame(g,1050,seen=false))
        for(t in 1100L..2400L step 100) if(frame(g,t)) clicks++
        assertEquals(1,clicks)
    }

    @Test fun pinchThenDwellCannotConfirmTheSameHoverTwice() {
        val g=SpatialGesture()
        frame(g,0)
        assertFalse(frame(g,100,pinch=true))
        assertTrue(frame(g,180,pinch=true))
        for(t in 200L..500L step 100) assertFalse(frame(g,t,pinch=true))
        for(t in 600L..2100L step 100) assertFalse(frame(g,t))
    }

}
