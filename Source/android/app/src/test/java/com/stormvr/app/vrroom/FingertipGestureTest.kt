package com.stormvr.app.vrroom

import org.junit.Assert.*
import org.junit.Test

class FingertipGestureTest {
    private fun update(g: FingertipGesture,t: Long,id: String?="bar",kind: FingertipGesture.Kind=FingertipGesture.Kind.MOVE,
                       observed: Boolean=true,pointing: Boolean=true,blocked: Boolean=false,x: Float=0f): Boolean =
        g.update(id,kind,x,0f,observed,pointing,blocked,t,600,1000,.025f)

    @Test fun intentRequiresStableHoverThenArmedBeforeDragging() {
        val g=FingertipGesture()
        update(g,0); assertEquals(FingertipGesture.Phase.HOVER,g.phase)
        update(g,340); assertEquals(FingertipGesture.Phase.ARMED,g.phase)
        update(g,599); assertFalse(g.captured)
        update(g,600); assertEquals(FingertipGesture.Phase.DRAGGING,g.phase)
        update(g,700,id=null,x=8f); assertTrue(g.captured)
    }
    @Test fun jitterIsToleratedButLargeMovementBeforeIntentRestartsTimer() {
        val g=FingertipGesture()
        update(g,0); update(g,300,x=.01f); update(g,500,x=.08f)
        update(g,700,x=.08f); assertFalse(g.captured)
        update(g,1100,x=.08f); assertTrue(g.captured)
    }
    @Test fun curlHasHysteresisAndReleasesWithoutASecondConfirmation() {
        val g=FingertipGesture(); update(g,0); update(g,600)
        update(g,650,pointing=false); update(g,710); assertTrue(g.captured)
        update(g,800,pointing=false); update(g,900,pointing=false)
        assertEquals(FingertipGesture.Phase.RELEASED,g.phase)
    }
    @Test fun briefTrackingLossKeepsCaptureAndLongLossReleasesWithoutClick() {
        val g=FingertipGesture(); update(g,0); update(g,600)
        assertFalse(update(g,650,observed=false)); update(g,800,observed=false); assertTrue(g.captured)
        update(g,810); assertTrue(g.captured)
        update(g,900,observed=false); update(g,1121,observed=false)
        assertEquals(FingertipGesture.Phase.RELEASED,g.phase)
    }
    @Test fun gamepadOrModeOwnershipCancelsImmediately() {
        val g=FingertipGesture(); update(g,0); update(g,600)
        update(g,610,blocked=true); assertEquals(FingertipGesture.Phase.IDLE,g.phase)
        update(g,700); assertFalse(g.captured)
    }
    @Test fun contentClicksOnceAndCanNeverBecomeScreenDrag() {
        val g=FingertipGesture()
        assertFalse(update(g,0,kind=FingertipGesture.Kind.CLICK))
        assertFalse(update(g,600,kind=FingertipGesture.Kind.CLICK))
        assertTrue(update(g,1000,kind=FingertipGesture.Kind.CLICK))
        assertFalse(update(g,2500,kind=FingertipGesture.Kind.CLICK))
        assertFalse(g.captured)
        update(g,2600,id=null)
        update(g,2700,kind=FingertipGesture.Kind.CLICK)
        assertTrue(update(g,3700,kind=FingertipGesture.Kind.CLICK))
    }
    @Test fun indexPoseUsesTipAndNotThumbPinch() {
        val p=FloatArray(63)
        p[16]=.02f; p[19]=.06f; p[25]=.11f // MCP/PIP/TIP aligned
        assertTrue(FingertipPose.extended(p,false))
        p[12]=100f // thumb has no influence
        assertTrue(FingertipPose.extended(p,false))
        p[25]=.025f
        assertFalse(FingertipPose.extended(p,true))
    }
    @Test fun initialGrabAndRecoveryNeverJumpAndLongMovesAreBounded() {
        val p=VrState().apply { curved=false; wx=.4f; wz=.3f; distance=2f; updateGeom() }
        val m=FingertipManipulation(); m.begin(p,.5f,.4f,0,0)
        m.update(p,false,0f,0f,0f,.5f,2f,.4f,16)
        assertEquals(.4f,p.wx,.0001f); assertEquals(.3f,p.wz,.0001f)
        m.update(p,false,0f,0f,0f,.8f,2f,.4f,32)
        assertTrue(p.wx>.4f && p.wx<=.44f)
        val before=p.wx
        m.hold(); m.update(p,false,0f,0f,0f,1.5f,2f,.4f,200)
        assertEquals(before,p.wx,.0001f)
        m.update(p,false,0f,0f,0f,1.5f,2f,.4f,216)
        assertEquals(before,p.wx,.0001f)
    }
    @Test fun resizingPreservesAspectAndBoundsWithoutTranslating() {
        val p=VrState().apply { curved=false; width=2f; updateGeom() }
        val m=FingertipManipulation(); m.begin(p,1f,1f,1,0)
        for(t in 16L..1000L step 16) m.update(p,true,0f,0f,0f,8f,3f,8f,t)
        assertTrue(p.width>2f && p.width<=7.5f)
        assertEquals(VrState.TEX_H.toFloat()/VrState.TEX_W,p.win.h/p.win.w,.0001f)
        assertEquals(0f,p.wx,0f)
    }
    @Test fun cornerAndBarTargetsStayOutsideContent() {
        val p=VrState().apply { updateGeom() }
        assertEquals(-1,ScreenControls.cornerAt(p,p.wx+p.win.w/2-.001f,p.wz+p.win.h/2-.001f))
        assertEquals(0,ScreenControls.cornerAt(p,p.cornerS(0),p.cornerZ(0)))
        assertTrue(ScreenControls.inBar(p,p.bar.cx,p.bar.cz))
        assertTrue(p.bar.cz-p.bar.h/2>p.win.cz+p.win.h/2)
    }
    @Test fun tiltedWindowFollowsFrozenLocalAxesRatherThanNewWindowGeometry() {
        val p=VrState().apply { curved=false; scrAz=35f; scrYaw=25f; scrPitch=15f; updateGeom() }
        val m=FingertipManipulation(); val start=FloatArray(3); val next=FloatArray(3)
        p.surfPoint(.2f,.4f,0f,start); p.surfPoint(.5f,.4f,0f,next)
        m.begin(p,.2f,.4f,0,0)
        m.update(p,false,0f,0f,0f,start[0],start[1],start[2],16)
        assertEquals(0f,p.wx,.0001f)
        m.update(p,false,0f,0f,0f,next[0],next[1],next[2],32)
        assertTrue(p.wx>0f)
        assertTrue(p.distance>3f) // yaw tilts the local horizontal axis into depth
    }
}
