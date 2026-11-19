package com.stormvr.app.vrroom

import org.junit.Assert.*
import org.junit.Test

class SpatialRaycastTest {
    @Test fun foregroundOccludesRearAndLockedContentStillOccludes() {
        val front=VrState().apply { curved=false; distance=1f; updateGeom() }
        val rear=VrState().apply { curved=false; distance=3f; updateGeom() }
        val a=SpatialRaycast.hit(front,0f,0f,0f,0f,1f,0f,true)!!
        val b=SpatialRaycast.hit(rear,0f,0f,0f,0f,1f,0f)!!
        assertTrue(a.distanceSquared<b.distanceSquared)
        assertNull(SpatialRaycast.hit(front,0f,0f,0f,0f,-1f,0f))
    }
    @Test fun rayMagnitudeDoesNotChangeIntersection() {
        val p=VrState().apply { curved=false; updateGeom() }
        val a=SpatialRaycast.hit(p,0f,0f,0f,0f,1f,0f)!!
        val b=SpatialRaycast.hit(p,0f,0f,0f,0f,.001f,0f)!!
        assertEquals(a.distanceSquared,b.distanceSquared,.0001f)
        assertNull(SpatialRaycast.hit(p,0f,0f,0f,0f,0f,0f))
        assertNull(SpatialRaycast.hit(p,0f,0f,0f,Float.NaN,1f,0f))
    }
    @Test fun enlargedControlsDoNotCoverContentAndLockedHandlesAreNotTargets() {
        val p=VrState().apply { curved=false; updateGeom() }
        assertTrue(p.handle()>=.12f)
        val a=SpatialRaycast.hit(p,0f,0f,0f,p.cornerS(0),p.distance,p.cornerZ(0))!!
        assertEquals(0,a.corner)
        assertNull(SpatialRaycast.hit(p,0f,0f,0f,p.cornerS(0),p.distance,p.cornerZ(0),true))
    }
    @Test fun physicalDepthUsesSceneMotionAndRebasesAfterUncertainty() {
        val pose=VrState().apply { curved=false; distance=3f; updateGeom() }
        val m=FingertipManipulation(); m.begin(pose,0f,0f,0,0)
        fun sample(y: Float,z: Float)=SpatialHandEngine.Sample(floatArrayOf(0f,y,0f),FloatArray(15),FloatArray(63),
            FloatArray(3),.9f,z,SpatialHandEngine.Source.RAW_DEPTH,true,1_000_000_000)
        fun tick(time: Long,s: SpatialHandEngine.Sample?) {
            assertTrue(m.update(pose,false,0f,0f,0f,0f,1f,0f,time))
            m.updateDepth(pose,s,time)
        }
        tick(16,sample(.5f,.5f)); tick(32,sample(.5f,.4f))
        assertEquals(3f,pose.distance,.0001f) // camera moved; hand stayed fixed in scene
        for(t in 48L..320L step 16) tick(t,sample(.6f,.5f))
        assertTrue(pose.distance>3f && pose.distance<3.21f)
        val before=pose.distance
        tick(336,null); assertEquals(before,pose.distance,.0001f)
        tick(352,sample(.9f,.8f)); assertEquals(before,pose.distance,.0001f)
        m.hold(); tick(500,sample(1f,.9f)); assertEquals(before,pose.distance,.0001f)
    }

}
