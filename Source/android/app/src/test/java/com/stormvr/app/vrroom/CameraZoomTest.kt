package com.stormvr.app.vrroom

import org.junit.Assert.*
import org.junit.Test

class CameraZoomTest {
    @Test fun zoomRejectsNonFiniteValuesAndClampsToSafeDigitalRange() {
        assertEquals(1f,CameraZoom.clamp(Float.NaN),0f)
        assertEquals(1f,CameraZoom.clamp(Float.POSITIVE_INFINITY),0f)
        assertEquals(.4f,CameraZoom.clamp(-4f),0f)
        assertEquals(.4f,CameraZoom.clamp(.2f),0f)
        assertEquals(.5f,CameraZoom.clamp(.5f),0f)
        assertEquals(2f,CameraZoom.clamp(5f),0f)
        assertEquals(1.5f,CameraZoom.clamp(1.5f),0f)
    }
    @Test fun inactiveCameraAndFullscreenAlwaysUseOneEvenWhenPreferenceIsSaved() {
        assertEquals(1f,CameraZoom.effective(2f,false,false),0f)
        assertEquals(1f,CameraZoom.effective(2f,true,true),0f)
        assertEquals(2f,CameraZoom.effective(2f,true,false),0f)
        assertEquals(.5f,CameraZoom.effective(.5f,true,false),0f)
        assertEquals(1f,CameraZoom.effective(.5f,false,false),0f)
    }
    @Test fun cropAndProjectionMatchForOffCenterCamerasAndBothEyes() {
        val original=floatArrayOf(1.2f,0f,0f,0f, 0f,1.6f,0f,0f, .1f,-.07f,-1f,-1f, 0f,0f,-.1f,0f)
        for(eye in listOf(-.032f,.032f)) for(zoom in listOf(.4f,.5f,1f,1.5f,2f)) {
            val m=original.copyOf(); CameraZoom.applyProjection(m,zoom)
            val point=floatArrayOf(.2f+eye,.1f,-1.4f,1f)
            fun project(a: FloatArray,row: Int)=(0..3).sumOf { (a[it*4+row]*point[it]).toDouble() }.toFloat()
            val w=project(original,3)
            // Enlarging the already display-oriented background quad scales normalized X/Y by zoom.
            for(row in 0..1) assertEquals(project(original,row)/w*zoom,project(m,row)/project(m,3),.0001f)
            assertEquals(project(original,2),project(m,2),0f)
            assertEquals(w,project(m,3),0f)
        }
        assertArrayEquals(floatArrayOf(1.2f,0f,0f,0f,0f,1.6f,0f,0f,.1f,-.07f,-1f,-1f,0f,0f,-.1f,0f),original,0f)
    }
}
