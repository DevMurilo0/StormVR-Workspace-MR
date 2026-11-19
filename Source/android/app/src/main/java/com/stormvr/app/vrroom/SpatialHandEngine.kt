package com.stormvr.app.vrroom

import kotlin.math.*

/** Camera-space geometry, independent of Android, MediaPipe and UI intent. No inferred pose on loss. */
class SpatialHandEngine {
    enum class Source { MONOCULAR, RAW_DEPTH }
    data class Estimate(val points: FloatArray, val distance: Float, val confidence: Float,
                        val source: Source, val depthReliable: Boolean)
    data class Sample(val palm: FloatArray, val tips: FloatArray, val directions: FloatArray,
                      val velocity: FloatArray, val confidence: Float, val distance: Float,
                      val source: Source, val depthReliable: Boolean, val timestampNs: Long)
    private val previous=arrayOfNulls<Sample>(2)
    fun reset() { previous.fill(null) }

    /** Both arrays use the upright inference image. World landmarks are relative, never camera positions. */
    fun estimate(normalized: FloatArray, world: FloatArray, width: Int, height: Int,
                 fx: Float, fy: Float, cx: Float, cy: Float, palmWidth: Float,
                 depth: RawHandDepth? = null, imageTimestamp: Long = 0, rotation: Int = 0): Estimate? {
        if(normalized.size!=63 || world.size!=63 || normalized.any { !it.isFinite() } || world.any { !it.isFinite() } ||
            fx<=0 || fy<=0 || !fx.isFinite() || !fy.isFinite() || !cx.isFinite() || !cy.isFinite()) return null
        val modelWidth=length(world,5,17)
        if(modelWidth !in .025f.. .14f || palmWidth !in .045f.. .115f) return null
        val scale=palmWidth/modelWidth
        val ids=intArrayOf(0,5,9,13,17)
        val depths=ArrayList<Float>()
        for(a in ids.indices) for(b in a+1 until ids.size) {
            val i=ids[a]*3; val j=ids[b]*3
            val wx=(world[i]-world[j])*scale; val wy=(world[i+1]-world[j+1])*scale
            val projected=hypot(wx,wy)
            val full=length(world,ids[a],ids[b])*scale
            val angular=hypot((normalized[i]-normalized[j])*width/fx,(normalized[i+1]-normalized[j+1])*height/fy)
            if(projected> .015f && projected/full>.45f && angular>.015f) depths.add(projected/angular)
        }
        if(depths.size<4) return null
        depths.sort(); val mono=depths[depths.size/2]
        val disagreement=depths.map { abs(it-mono) }.sorted()[depths.size/2]/mono
        if(mono !in .12f..1.4f || disagreement>.30f) return null
        val raw=depth?.palm(normalized,rotation,imageTimestamp,mono)
        val z=if(raw!=null) mono*.25f+raw*.75f else mono
        val rootZ=ids.map { world[it*3+2] }.average().toFloat()
        val out=FloatArray(63)
        for(i in 0..20) {
            val zi=z+(world[i*3+2]-rootZ)*scale
            if(zi !in .08f..1.6f || normalized[i*3] !in 0f..1f || normalized[i*3+1] !in 0f..1f) return null
            out[i*3]=(normalized[i*3]*width-cx)/fx*zi
            out[i*3+1]=(normalized[i*3+1]*height-cy)/fy*zi
            out[i*3+2]=zi
        }
        return Estimate(out,z,((1f-disagreement*2f)*if(raw!=null) .9f else .7f).coerceIn(0f,1f),
            if(raw!=null) Source.RAW_DEPTH else Source.MONOCULAR,raw!=null)
    }
    /** Scene-space sample. Directions are unit bone axes, not unobservable joint twist rotations. */
    fun sample(hand: Int, points: FloatArray, estimate: Estimate, timestampNs: Long): Sample? {
        if(!HandSamplePolicy.plausible(points,null,0f)) return null
        val palm=FloatArray(3) { c -> intArrayOf(0,5,9,13,17).map { points[it*3+c] }.average().toFloat() }
        val before=previous[hand]; val dt=if(before!=null) (timestampNs-before.timestampNs)/1e9f else 0f
        if(before!=null && dt<=0f) return null
        val velocity=FloatArray(3)
        if(before!=null && dt<.15f) for(c in 0..2) velocity[c]=(palm[c]-before.palm[c])/dt
        if(velocity.any { !it.isFinite() || abs(it)>4f }) return null
        // Fast motion + depth inferred from a static scene is not evidence of moving-hand depth.
        val reliable=estimate.depthReliable && before!=null && dt<.1f && sqrt(velocity.sumOf { (it*it).toDouble() }.toFloat())<.65f &&
            before.source==Source.RAW_DEPTH && abs(estimate.distance-before.distance)<.025f
        val axes=FloatArray(63)
        for(i in 1..20) {
            val parent=if(i%4==1) 0 else i-1
            val len=length(points,i,parent)
            if(len>1e-5f) for(c in 0..2) axes[i*3+c]=(points[i*3+c]-points[parent*3+c])/len
        }
        return Sample(palm,FloatArray(15) { points[(it/3*4+4)*3+it%3] },axes,velocity,
            estimate.confidence,estimate.distance,estimate.source,reliable,timestampNs).also { previous[hand]=it }
    }
    companion object {
        fun length(p: FloatArray,a: Int,b: Int)=sqrt((0..2).sumOf { (p[a*3+it]-p[b*3+it]).toDouble().pow(2) }.toFloat())
    }
}

/** Immutable CPU copy. UV affine transform maps native IMAGE_NORMALIZED to TEXTURE_NORMALIZED.
 * Raw depth is in native GPU texture orientation, not display/rotated bitmap orientation. */
data class RawHandDepth(val width: Int,val height: Int,val millimeters: IntArray,val confidence: IntArray,
                        val timestampNs: Long,val uv: FloatArray) {
    fun palm(p: FloatArray,rotation: Int,imageTimestamp: Long,mono: Float): Float? {
        if(width<3 || height<3 || millimeters.size!=width*height || confidence.size!=width*height || uv.size!=6 ||
            timestampNs<=0 || abs(imageTimestamp-timestampNs)>35_000_000L) return null
        val values=ArrayList<Float>()
        for(i in intArrayOf(0,5,9,13,17)) {
            val x=p[i*3]; val y=p[i*3+1]
            val u=when(rotation) { 90->y;180->1-x;270->1-y;else->x }
            val v=when(rotation) { 90->1-x;180->1-y;270->x;else->y }
            val tx=uv[0]+u*(uv[2]-uv[0])+v*(uv[4]-uv[0])
            val ty=uv[1]+u*(uv[3]-uv[1])+v*(uv[5]-uv[1])
            if(!tx.isFinite() || !ty.isFinite() || tx !in 0f..1f || ty !in 0f..1f) continue
            val px=(tx*(width-1)).roundToInt(); val py=(ty*(height-1)).roundToInt()
            val patch=ArrayList<Int>()
            for(dy in -1..1) for(dx in -1..1) {
                val xx=px+dx; val yy=py+dy
                if(xx !in 0 until width || yy !in 0 until height) continue
                val n=yy*width+xx; val z=millimeters[n]
                if(z in 120..1400 && confidence[n]>=200) patch.add(z)
            }
            if(patch.size>=5) {
                patch.sort()
                if(patch.last()-patch.first()<45) values.add(patch[patch.size/2]/1000f)
            }
        }
        if(values.size<4) return null
        values.sort(); val z=values[values.size/2]
        if(values.last()-values.first()>.07f || abs(z-mono)>max(.06f,mono*.18f)) return null
        return z
    }
}
