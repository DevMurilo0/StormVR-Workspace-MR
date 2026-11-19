package com.stormvr.app.vrroom

import org.junit.Assert.*
import org.junit.Test

class WorkspaceGeometryTest {
    @Test fun windowsHaveIndependentIdentityAndPose() {
        val a=VirtualScreen(); val b=VirtualScreen()
        assertNotEquals(a.id,b.id)
        a.pose.width=5f; a.pose.distance=4f
        assertEquals(3.6f,b.pose.width,.0001f)
        assertEquals(3f,b.pose.distance,.0001f)
    }
    @Test fun planarRayHitsCenterAndRejectsBackwardRay() {
        val p=VrState().apply { curved=false; distance=2f; wz=0f; updateGeom() }
        val hit=FloatArray(2)
        assertTrue(p.intersectSurface(0f,0f,0f,0f,1f,0f,hit))
        assertEquals(0f,hit[0],.0001f); assertEquals(0f,hit[1],.0001f)
        assertFalse(p.intersectSurface(0f,0f,0f,0f,-1f,0f,hit))
    }
    @Test fun orbitMovesWindowOutOfPrimaryPlane() {
        val p=VrState().apply { curved=false; wx=0f; wz=0f; distance=2f; scrAz=90f; updateGeom() }
        val center=FloatArray(3);p.screenCenter(center)
        assertEquals(2f,center[0],.0001f);assertEquals(0f,center[1],.0001f)
        val hit=FloatArray(2)
        assertTrue(p.intersectSurface(0f,0f,0f,1f,0f,0f,hit))
    }
    @Test fun curvedSurfaceRoundTripsThroughRaycast() {
        val p=VrState().apply { curved=true; wx=.2f; distance=3f; scrAz=-45f; scrEl=20f; scrYaw=10f; updateGeom() }
        val point=FloatArray(3); val hit=FloatArray(2)
        p.surfPoint(.6f,.4f,0f,point)
        assertTrue(p.intersectSurface(0f,0f,0f,point[0],point[1],point[2],hit))
        assertEquals(.6f,hit[0],.001f);assertEquals(.4f,hit[1],.001f)
    }
    @Test fun curvatureAndSizeRemainPerScreen() {
        val a=VirtualScreen(); val b=VirtualScreen()
        a.pose.workspaceCurve=.2f; a.pose.width=2f;a.pose.updateGeom()
        b.pose.workspaceCurve=1.2f;b.pose.width=4f;b.pose.updateGeom()
        assertTrue(a.pose.curveK()<b.pose.curveK())
        assertEquals(2f,a.pose.win.w,.0001f);assertEquals(4f,b.pose.win.w,.0001f)
    }
}
