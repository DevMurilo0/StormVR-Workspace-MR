package com.stormvr.app.vrroom

import kotlin.math.*

/** UI intent only. Never changes MediaPipe detection, filtering or pinch recognition. */
class FingertipGesture {
    enum class Phase { IDLE, HOVER, ARMED, DRAGGING, RESIZING, RELEASED }
    enum class Kind { CLICK, MOVE, RESIZE }
    private val spatial=SpatialGesture()
    val ownsTarget get()=captured || spatial.ownsTarget
    var phase=Phase.IDLE; private set
    var target: String?=null; private set
    var progress=0f; private set
    private var kind=Kind.CLICK
    private var since=0L
    private var missingSince=-1L
    private var releaseSince=-1L
    private var x=0f; private var y=0f
    val captured get()=phase==Phase.DRAGGING || phase==Phase.RESIZING
    fun reset() { spatial.reset(); phase=Phase.IDLE; target=null; progress=0f; missingSince=-1; releaseSince=-1 }
    fun release(now: Long) { spatial.release(); phase=Phase.RELEASED; since=now; progress=0f }

    fun updateSpatial(id: String?,action: Kind,px: Float,py: Float,observed: Boolean,
                      pointing: Boolean,grip: Boolean,blocked: Boolean,now: Long,tolerance: Float,
                      dwellMs: Long=1000L): Boolean {
        val click=spatial.update(id,action,px,py,observed,pointing,grip,blocked,now,tolerance,true,dwellMs)
        phase=spatial.phase; target=spatial.target; progress=spatial.progress
        return click
    }

    /** Returns true once for a click; capture phases are continuous until curl/loss/cancellation. */
    fun update(id: String?, action: Kind, px: Float, py: Float, observed: Boolean, pointing: Boolean,
               blocked: Boolean, now: Long, activationMs: Int, clickMs: Int, tolerance: Float): Boolean {
        if(blocked) { reset(); return false }
        if(!observed) {
            if(missingSince<0) missingSince=now
            if(captured && now-missingSince>220) release(now)
            else if(!captured) reset()
            return false
        }
        missingSince=-1
        if(!pointing) {
            if(releaseSince<0) releaseSince=now
            if(captured && now-releaseSince>=100) release(now)
            else if(!captured) reset()
            return false
        }
        releaseSince=-1
        // Captured motion no longer depends on the original bar/corner being under the ray.
        if(captured) return false
        val moved=hypot(px-x,py-y)>tolerance
        if(phase==Phase.RELEASED) {
            if(id==target && !moved) return false
            reset()
        }
        if(id==null) { reset(); return false }
        if(target!=id || moved || phase==Phase.IDLE) {
            target=id; kind=action; x=px; y=py; since=now; phase=Phase.HOVER; progress=0f
            return false
        }
        val duration=if(kind==Kind.CLICK) clickMs.coerceIn(800,1200) else activationMs.coerceIn(300,1200)
        progress=((now-since).toFloat()/duration).coerceIn(0f,1f)
        if(progress>=.55f) phase=Phase.ARMED
        if(progress<1f) return false
        phase=when(kind) { Kind.MOVE->Phase.DRAGGING; Kind.RESIZE->Phase.RESIZING; else->Phase.RELEASED }
        progress=0f
        return kind==Kind.CLICK
    }
}

object FingertipPose {
    /** MCP/PIP/TIP geometry in scene coordinates, with separate enter/leave thresholds. */
    fun extended(p: FloatArray, wasExtended: Boolean): Boolean {
        if(p.size<63 || (15..26).any { !p[it].isFinite() }) return false
        val ax=p[18]-p[15]; val ay=p[19]-p[16]; val az=p[20]-p[17]
        val bx=p[24]-p[18]; val by=p[25]-p[19]; val bz=p[26]-p[20]
        val length=sqrt((ax*ax+ay*ay+az*az)*(bx*bx+by*by+bz*bz))
        return length>1e-6f && (ax*bx+ay*by+az*bz)/length > if(wasExtended) .35f else .70f
    }
}

/** Grab against a frozen surface; changing the real window never moves the reference plane. */
class FingertipManipulation {
    private val reference=VrState()
    private val hit=FloatArray(2)
    private var sx=0f; private var sz=0f
    private var width=0f; private var wx=0f; private var wz=0f
    private var filteredX=0f; private var filteredZ=0f
    private var last=0L
    private var recovering=false
    private var corner=0
    private val translation=FloatArray(9)
    private var distance=0f
    private var startDistance=0f
    private var startX=0f; private var startZ=0f
    private val depthAxis=FloatArray(3)
    private var depthAnchor: Float?=null
    private var depthOffset=0f
    private var depthApplied=0f
    private var depthLast=0L
    var depthMotion="Mover"; private set
    var active=false; private set
    fun begin(pose: VrState, x: Float,z: Float,cornerIndex: Int, now: Long) {
        reference.apply {
            this.width=pose.width; distance=pose.distance; this.wx=pose.wx; this.wz=pose.wz
            scrAz=pose.scrAz; scrEl=pose.scrEl; scrYaw=pose.scrYaw; scrPitch=pose.scrPitch; scrRoll=pose.scrRoll
            curved=pose.curved; workspaceCurve=pose.workspaceCurve; updateGeom()
        }
        depthAnchor=null; depthOffset=0f; depthApplied=0f; depthLast=now; depthMotion="Mover"
        sx=x; sz=z; width=pose.width; wx=pose.wx; wz=pose.wz; distance=pose.distance; startDistance=pose.distance; startX=pose.wx; startZ=pose.wz
        val a=FloatArray(9); val b=FloatArray(3); val orbit=FloatArray(9)
        reference.screenXf(a,b)
        val yaw=reference.scrYaw; val pitch=reference.scrPitch; val roll=reference.scrRoll
        reference.scrYaw=0f; reference.scrPitch=0f; reference.scrRoll=0f
        reference.screenXf(orbit,b)
        for(c in 0..2) depthAxis[c]=orbit[c*3+1]
        reference.scrYaw=yaw; reference.scrPitch=pitch; reference.scrRoll=roll
        for(r in 0..2) for(c in 0..2) {
            translation[r*3+c]=0f
            for(k in 0..2) translation[r*3+c]+=orbit[k*3+r]*a[k*3+c]
        }
        filteredX=0f; filteredZ=0f; last=now; recovering=false; corner=cornerIndex; active=true
    }
    fun hold() { recovering=true; depthAnchor=null }
    fun end() { active=false }
    fun updateDepth(pose: VrState,sample: SpatialHandEngine.Sample?,now: Long) {
        if(!active) return
        val dt=((now-depthLast)/1000f).coerceIn(0f,.05f); depthLast=now
        if(sample==null || !sample.depthReliable) { depthAnchor=null; depthMotion="Mover · profundidade incerta"; pose.distance=(pose.distance+depthOffset).coerceIn(.5f,6f); pose.updateGeom(); return }
        val coordinate=(0..2).sumOf { (sample.palm[it]*depthAxis[it]).toDouble() }.toFloat()
        val anchor=depthAnchor
        if(anchor==null) { depthAnchor=coordinate; depthApplied=depthOffset }
        val goal=depthApplied+(coordinate-(depthAnchor ?: coordinate))*2f
        depthOffset+=((goal-depthOffset)*(1f-exp(-dt*18f))).coerceIn(-dt*.7f,dt*.7f)
        pose.distance=(pose.distance+depthOffset).coerceIn(.5f,6f)
        depthMotion=if(goal-depthApplied<-.025f) "Puxar" else if(goal-depthApplied>.025f) "Empurrar" else "Mover"
        pose.updateGeom()
    }
    fun update(pose: VrState, resizing: Boolean, ox: Float,oy: Float,oz: Float,dx: Float,dy: Float,dz: Float, now: Long): Boolean {
        if(!active || !reference.intersectSurface(ox,oy,oz,dx,dy,dz,hit)) { hold(); return false }
        if(recovering) {
            sx=hit[0]; sz=hit[1]; wx=pose.wx; wz=pose.wz; width=pose.width; distance=pose.distance
            filteredX=0f; filteredZ=0f; depthOffset=0f; depthApplied=0f; recovering=false; last=now
            return true
        }
        val dt=((now-last)/1000f).coerceIn(.001f,.05f); last=now
        // Bound physical window speed and filter tremor, independent of render frame rate.
        val alpha=1f-exp(-dt*18f)
        val maxStep=dt*2.5f
        filteredX+=((hit[0]-sx-filteredX)*alpha).coerceIn(-maxStep,maxStep)
        filteredZ+=((hit[1]-sz-filteredZ)*alpha).coerceIn(-maxStep,maxStep)
        if(resizing) {
            val side=if(corner%2==0) -1f else 1f
            val vertical=if(corner<2) 1f else -1f
            val ratio=VrState.TEX_H.toFloat()/VrState.TEX_W
            val horizontal=filteredX*side
            val diagonal=filteredZ*vertical/ratio
            val delta=if(abs(diagonal)>abs(horizontal)) diagonal else horizontal
            pose.width=(width+2f*delta).coerceIn(.6f,7.5f)
        } else {
            pose.wx=(wx+translation[0]*filteredX+translation[2]*filteredZ).coerceIn(startX-4f,startX+4f)
            pose.distance=(distance+translation[3]*filteredX+translation[5]*filteredZ).coerceIn(maxOf(.5f,startDistance-3f),startDistance+3f)
            pose.wz=(wz+translation[6]*filteredX+translation[8]*filteredZ).coerceIn(startZ-3f,startZ+3f)
        }
        pose.updateGeom(); return true
    }
}

object ScreenControls {
    fun cornerAt(p: VrState,s: Float,z: Float): Int {
        // Controls are outside the content: clicking a page's corner must not resize it.
        if(abs(s-p.wx)<=p.win.w/2 && abs(z-p.wz)<=p.win.h/2) return -1
        for(i in 0..3) if(abs(s-p.cornerS(i))<=p.handle() && abs(z-p.cornerZ(i))<=p.handle()) return i
        return -1
    }
    fun inBar(p: VrState,s: Float,z: Float)=abs(s-p.bar.cx)<=p.bar.w/2 && abs(z-p.bar.cz)<=p.bar.h/2
}
