package com.stormvr.app.vrroom

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SpatialHandEngineTest {
    private fun world()=FloatArray(63).also { p ->
        for(i in 0..20) { p[i*3]=(i%5-2)*.02f; p[i*3+1]=(i/5)*.025f }
        p[15]=-.04f; p[16]=.05f; p[51]=.04f; p[52]=.05f
    }
    private fun projected(w: FloatArray,z: Float,fx: Float=600f,fy: Float=450f)=FloatArray(63) { c ->
        when(c%3) { 0->.5f+w[c]*fx/z/640f; 1->.5f+w[c]*fy/z/480f; else->w[c] }
    }
    private fun estimate(w: FloatArray,n: FloatArray=projected(w,.5f))=
        SpatialHandEngine().estimate(n,w,640,480,600f,450f,320f,240f,.08f)
    @Test fun recoversCameraDepthWithUnequalFocalLengths() {
        val e=estimate(world())!!
        assertEquals(.5f,e.distance,.002f)
        assertEquals(SpatialHandEngine.Source.MONOCULAR,e.source)
        assertFalse(e.depthReliable)
        assertTrue(e.confidence in 0f..1f)
    }
    @Test fun rotationAndFingerCurlDoNotChangePalmScale() {
        val w=world()
        for(i in 0..20) { val x=w[i*3]; val y=w[i*3+1]; w[i*3]=-y; w[i*3+1]=x }
        val a=estimate(w)!!
        for(i in intArrayOf(4,8,12,16,20)) { w[i*3]=0f; w[i*3+1]=0f }
        assertEquals(a.distance,estimate(w)!!.distance,.002f)
    }
    @Test fun invalidAndDegenerateObservationsReturnNoPose() {
        val w=world(); val n=projected(w,.5f)
        n[0]=Float.NaN; assertNull(estimate(w,n))
        assertNull(estimate(FloatArray(63)))
        val outside=projected(w,.5f); outside[24]=1.1f
        assertNull(estimate(w,outside))
    }
    private fun raw(z: Int=500,c: Int=255,time: Long=1_000_000_000L,uv: FloatArray=floatArrayOf(0f,0f,1f,0f,0f,1f))=
        RawHandDepth(40,40,IntArray(1600) { z },IntArray(1600) { c },time,uv)
    @Test fun depthRejectsAgeConfidenceMissingBackgroundAndMisalignment() {
        val p=projected(world(),.5f); val ts=1_000_000_000L
        assertEquals(.5f,raw().palm(p,0,ts,.5f)!!,.001f)
        assertNull(raw(time=ts-36_000_000).palm(p,0,ts,.5f))
        assertNull(raw(c=199).palm(p,0,ts,.5f))
        assertNull(raw(z=0).palm(p,0,ts,.5f))
        assertNull(raw(z=1200).palm(p,0,ts,.5f))
        assertNull(raw(uv=floatArrayOf(2f,2f,3f,2f,2f,3f)).palm(p,0,ts,.5f))
    }
    @Test fun allImageRotationsMapToNativeDepthPixels() {
        val ts=1_000_000_000L
        val z=IntArray(1600) { 0 }; val conf=IntArray(1600) { 255 }
        for(y in 9..15) for(x in 5..11) z[y*40+x]=500
        val r=RawHandDepth(40,40,z,conf,ts,floatArrayOf(0f,0f,1f,0f,0f,1f))
        for(rot in intArrayOf(0,90,180,270)) {
            val p=FloatArray(63)
            for(i in 0..20) {
                p[i*3]=when(rot) { 90->.7f;180->.8f;270->.3f;else->.2f }
                p[i*3+1]=when(rot) { 90->.2f;180->.7f;270->.8f;else->.3f }
            }
            assertEquals("rotation $rot",.5f,r.palm(p,rot,ts,.5f)!!,.001f)
        }
    }
    @Test fun sampleHasPalmTipsAxesAndRejectsNonMonotonicOrTeleport() {
        val engine=SpatialHandEngine(); val w=world(); val e=estimate(w)!!
        val first=engine.sample(0,w,e,1_000_000_000L)!!
        assertEquals(15,first.tips.size); assertEquals(63,first.directions.size)
        assertNull(engine.sample(0,w,e,1_000_000_000L))
        val moved=w.mapIndexed { i,v -> if(i%3==0) v+1f else v }.toFloatArray()
        assertNull(engine.sample(0,moved,e,1_020_000_000L))
        assertFalse(first.depthReliable)
    }
    @Test fun depthFusionNeedsConsecutiveSlowEvidenceForPhysicalMotion() {
        val engine=SpatialHandEngine(); val w=world(); val base=estimate(w)!!
        val raw=base.copy(source=SpatialHandEngine.Source.RAW_DEPTH,depthReliable=true)
        assertFalse(engine.sample(0,w,raw,1_000_000_000L)!!.depthReliable)
        assertTrue(engine.sample(0,w,raw,1_030_000_000L)!!.depthReliable)
        val moved=w.mapIndexed { i,v -> if(i%3==0) v+.05f else v }.toFloatArray()
        assertFalse(engine.sample(0,moved,raw,1_060_000_000L)!!.depthReliable)
    }
}
