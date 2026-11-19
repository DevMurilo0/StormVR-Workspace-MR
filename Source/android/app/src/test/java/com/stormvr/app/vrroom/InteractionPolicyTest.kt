package com.stormvr.app.vrroom

import org.junit.Assert.*
import org.junit.Test

class InteractionPolicyTest {
    @Test fun physicalCameraAxesAreInvariantUnderImageRotation() {
        val x=.2f; val y=.3f
        for(rotation in listOf(0,90,180,270)) {
            val imageX=when(rotation) { 90->-y; 180->-x; 270->y; else->x }
            val imageY=when(rotation) { 90->x; 180->-y; 270->-x; else->y }
            assertEquals(x,HandSamplePolicy.cameraX(imageX,imageY,rotation),.0001f)
            assertEquals(-y,HandSamplePolicy.cameraY(imageX,imageY,rotation),.0001f)
        }
    }
    @Test fun oneEuroSmoothsStationaryNoiseButResetDoesNotReuseOldPose() {
        val f=OneEuro(1,1.6f,8f,1f)
        var p=floatArrayOf(0f); f.apply(p,0f)
        p=floatArrayOf(.01f); f.apply(p,.03f)
        assertTrue(p[0]>0f && p[0]<.01f)
        f.reset(); p=floatArrayOf(2f); f.apply(p,1f)
        assertEquals(2f,p[0],0f)
    }
    @Test fun pinchRequiresOpenThenDebounceAndHysteresis() {
        val p=PinchLatch()
        assertFalse(p.update(.1f,true,0))
        p.update(.9f,true,10)
        assertFalse(p.update(.3f,true,20))
        assertFalse(p.update(.3f,true,64))
        assertTrue(p.update(.3f,true,65))
        assertTrue(p.update(.48f,true,80))
        assertTrue(p.update(.8f,true,100))
        assertFalse(p.update(.8f,true,135))
    }
    @Test fun briefNoiseDoesNotPressAndLossRequiresRearming() {
        val p=PinchLatch(); p.update(.9f,true,0)
        p.update(.1f,true,10); p.update(.8f,true,30)
        assertFalse(p.update(.1f,true,40))
        assertTrue(p.update(.1f,true,85))
        assertFalse(p.update(.1f,false,90))
        assertFalse(p.update(.1f,true,200))
        p.update(.9f,true,220); p.update(.1f,true,230)
        assertTrue(p.update(.1f,true,280))
    }
    @Test fun staleFutureAndZeroSamplesAreInvalidPerHand() {
        val now=1_000_000_000L
        assertFalse(HandSamplePolicy.fresh(now,0))
        assertFalse(HandSamplePolicy.fresh(now,now+1))
        assertFalse(HandSamplePolicy.fresh(now,now-230_000_000))
        assertTrue(HandSamplePolicy.fresh(now,now-20_000_000))
        val samples=longArrayOf(now-300_000_000,now-10_000_000)
        assertFalse(HandSamplePolicy.fresh(now,samples[0])); assertTrue(HandSamplePolicy.fresh(now,samples[1]))
    }
    @Test fun predictionIsBoundedAndDisabledWhenUncertain() {
        val now=1_000_000_000L
        assertEquals(.025f,HandSamplePolicy.predictionSeconds(now,now-20_000_000,30_000_000),.0001f)
        assertEquals(0f,HandSamplePolicy.predictionSeconds(now,now-90_000_000,20_000_000),0f)
        assertEquals(0f,HandSamplePolicy.predictionSeconds(now,now-10_000_000,150_000_000),0f)
    }
    @Test fun crossedHandsUseGlobalContinuityInsteadOfImageOrder() {
        val left=floatArrayOf(.05f,0f,0f); val right=floatArrayOf(-.05f,0f,0f)
        assertFalse(HandSamplePolicy.assignment(floatArrayOf(-.06f,0f,0f),floatArrayOf(.06f,0f,0f),left,right))
    }
    @Test fun invalidGeometryAndWristTeleportsAreRejected() {
        val p=FloatArray(63)
        assertTrue(HandSamplePolicy.plausible(p,null,.03f))
        p[1]=Float.NaN; assertFalse(HandSamplePolicy.plausible(p,null,.03f)); p[1]=0f
        p[0]=1f; assertFalse(HandSamplePolicy.plausible(p,FloatArray(63),.03f)); p[0]=0f
        p[24]=.5f; assertFalse(HandSamplePolicy.plausible(p,null,.03f))
    }
    @Test fun targetRemainsCapturedUntilReleaseEvenOnRayMiss() {
        val c=ClickCapture()
        assertEquals(ClickCapture.Event.DOWN,c.update(true,true,true,10f,20f,3f))
        assertEquals(ClickCapture.Event.NONE,c.update(true,true,true,12f,21f,3f))
        assertEquals(ClickCapture.Event.UP,c.update(false,true,false,999f,999f,3f))
        assertEquals(10f,c.x,0f); assertEquals(20f,c.y,0f)
        assertEquals(ClickCapture.Event.NONE,c.update(false,true,true,10f,20f,3f))
    }
    @Test fun dragSequenceAndCancellationNeverEmitUpOnLoss() {
        val c=ClickCapture()
        val events=listOf(c.update(true,true,true,0f,0f,3f),c.update(true,true,true,8f,0f,3f),c.update(true,false,false,0f,0f,3f),c.cancel())
        assertEquals(listOf(ClickCapture.Event.DOWN,ClickCapture.Event.MOVE,ClickCapture.Event.CANCEL,ClickCapture.Event.NONE),events)
    }
    @Test fun dwellFiresOnceWithJitterAndShortMissThenRearms() {
        val d=DwellSelector()
        assertFalse(d.update("a",0f,0f,0,1000,.03f))
        assertFalse(d.update("a",.01f,0f,950,1000,.03f))
        assertFalse(d.update(null,0f,0f,980,1000,.03f))
        assertTrue(d.update("a",.01f,0f,1000,1000,.03f))
        assertFalse(d.update("a",.01f,0f,3000,1000,.03f))
        d.update(null,0f,0f,3200,1000,.03f)
        assertFalse(d.update("a",0f,0f,3300,1000,.03f))
        assertTrue(d.update("a",0f,0f,4300,1000,.03f))
    }
    @Test fun coordinatesMapCornersCenterAndClampToLastPixel() {
        assertEquals(0f,SurfaceCoordinates.pixel(-2f,0f,4f,1920),0f)
        assertEquals(1919f,SurfaceCoordinates.pixel(2f,0f,4f,1920),0f)
        assertEquals(959.5f,SurfaceCoordinates.pixel(0f,0f,4f,1920),0f)
        assertEquals(0f,SurfaceCoordinates.pixel(1f,0f,2f,1080,true),0f)
        assertEquals(1079f,SurfaceCoordinates.pixel(-8f,0f,2f,1080,true),0f)
    }
}
