package com.stormvr.app.vrroom

import kotlin.math.abs

/** Distance ordering in scene space, with identical occlusion for content and manipulation controls. */
object SpatialRaycast {
    data class Hit(val s: Float,val z: Float,val distanceSquared: Float,val corner: Int,val bar: Boolean)
    fun hit(pose: VrState,ox: Float,oy: Float,oz: Float,dx: Float,dy: Float,dz: Float,locked: Boolean=false): Hit? {
        val hit=FloatArray(2)
        if(!pose.intersectSurface(ox,oy,oz,dx,dy,dz,hit)) return null
        val corner=if(locked) -1 else ScreenControls.cornerAt(pose,hit[0],hit[1])
        val bar=!locked && ScreenControls.inBar(pose,hit[0],hit[1])
        val inside=abs(hit[0]-pose.win.cx)<=pose.win.w/2 && abs(hit[1]-pose.win.cz)<=pose.win.h/2
        if(!inside && corner<0 && !bar) return null
        val point=FloatArray(3); pose.surfPoint(hit[0],hit[1],0f,point)
        var distance=0f
        for(c in 0..2) { val delta=point[c]-when(c) { 0->ox;1->oy;else->oz }; distance+=delta*delta }
        if(!distance.isFinite()) return null
        return Hit(hit[0],hit[1],distance,corner,bar)
    }
}
