package com.stormvr.app.vrroom

import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.*

/** One owner for indicator intent on primary/additional windows and the existing keyboard. */
class FingertipInteraction(
    private val state: VrState,
    private val workspace: WorkspaceManager,
    private val displays: WorkspaceDisplays,
    private val legacy: WorkspaceInteraction,
    private val primaryClick: (Int,Int,Int,Float,Float)->Boolean
) {
    private data class Target(val id: String,val pose: VrState,val kind: FingertipGesture.Kind,
        val hitKind: Int,val index: Int,val s: Float,val z: Float,val screen: VirtualScreen?=null)
    private val gestures=Array(2) { FingertipGesture() }
    private val grip=BooleanArray(2)
    private val stableHover=arrayOfNulls<Target>(2)
    private val stableHoverAt=LongArray(2)
    private val motion=Array(2) { FingertipManipulation() }
    private val hoveredPose=arrayOfNulls<VrState>(2)
    private val claimedKey=arrayOfNulls<String>(2)
    private val captured=arrayOfNulls<Target>(2)
    private val missed=LongArray(2) { -1 }
    private val hit=FloatArray(2)
    private val point=FloatArray(3)
    private val ray=FloatArray(3)
    var feedback="Estenda o indicador · dobre para soltar"
        private set
    fun cancel() {
        grip.fill(false)
        stableHover.fill(null); stableHoverAt.fill(0L)
        for(i in 0..1) { gestures[i].reset(); motion[i].end(); captured[i]=null; hoveredPose[i]=null; claimedKey[i]=null; missed[i]=-1 }
        state.fingertipOwned.fill(false); state.fingertipTargetId.fill(null)
        state.fingertipPhase.fill(FingertipGesture.Phase.IDLE)
    }
    private fun available(pose: VrState,i: Int): Boolean = !legacy.owns(pose) &&
        captured[1-i]?.pose !== pose && hoveredPose[1-i] !== pose && !(pose===state && state.inputCaptured[1-i])

    private fun target(i: Int): Target? {
        val ox=state.rox[i]; val oy=state.roy[i]; val oz=state.roz[i]
        var best: Target?=null
        var distance=Float.MAX_VALUE
        val hk=state.hoverKind[i]; val idx=state.hoverIdx[i]
        if(hk!=Hit.NONE) {
            val kind=when(hk) { Hit.BAR->FingertipGesture.Kind.MOVE; Hit.CORNER->FingertipGesture.Kind.RESIZE; else->FingertipGesture.Kind.CLICK }
            best=Target("main:$hk:$idx",state,kind,hk,idx,state.ax[i],state.az[i])
            distance=(state.hitX[i]-ox).pow(2)+(state.hitY[i]-oy).pow(2)+(state.hitZ[i]-oz).pow(2)
        }
        if(workspace.enabled) for(screen in workspace.screens) {
            val p=screen.pose
            if(!screen.visible) continue
            val surface=SpatialRaycast.hit(p,ox,oy,oz,ray[0],ray[1],ray[2],screen.locked) ?: continue
            hit[0]=surface.s; hit[1]=surface.z
            val corner=surface.corner; val bar=surface.bar; val d=surface.distanceSquared
            if(d<distance) {
                val kind=if(corner>=0) FingertipGesture.Kind.RESIZE else if(bar) FingertipGesture.Kind.MOVE else FingertipGesture.Kind.CLICK
                best=Target("${screen.id}:$kind:$corner",p,kind,Hit.WIN,corner,hit[0],hit[1],screen)
                distance=d
            }
        }
        // Pick the nearest visible surface before checking ownership: an unavailable surface still occludes.
        return best?.takeIf {
            available(it.pose,i) && claimedKey[1-i]!=it.id && it.hitKind!=Hit.KBBAR &&
                ((it.hitKind!=Hit.KB && it.hitKind!=Hit.TB) || it.index>=0)
        }
    }
    private fun send(t: Target,action: Int,started: Long): Boolean {
        val p=t.pose
        val ev=MotionEvent.obtain(started,SystemClock.uptimeMillis(),action,
            SurfaceCoordinates.pixel(t.s,p.wx,p.win.w,VrState.TEX_W),
            SurfaceCoordinates.pixel(t.z,p.wz,p.win.h,VrState.TEX_H,true),0)
        return try { displays.touch(t.screen!!.id,ev) } finally { ev.recycle() }
    }
    fun tick(now: Long) {
        workspace.hoverId=null
        for(order in 0..1) {
            val i=if(order==0) state.interaction.dominantHand else 1-state.interaction.dominantHand
            val g=gestures[i]
            val hardware=if(i==0) state.l2 else state.r2
            val enabled=state.interaction.handMode!=InteractionSettings.PINCH && state.handSource==0 &&
                state.handsOn && (state.laserHand[i] || g.ownsTarget) && hardware<.2f
            state.fingertipOwned[i]=enabled
            if(!enabled) {
                g.reset(); motion[i].end(); captured[i]=null; hoveredPose[i]=null; claimedKey[i]=null; stableHover[i]=null; state.fingertipTargetId[i]=null
                state.fingertipPhase[i]=g.phase
                continue
            }
            // Only this path can activate hand clicks; pinch and legacy dwell cannot run in parallel.
            state.pressed[i]=false
            state.surfPoint(state.ax[i],state.az[i],0f,point)
            ray[0]=point[0]-state.rox[i]; ray[1]=point[1]-state.roy[i]; ray[2]=point[2]-state.roz[i]
            val observed=state.handValid[i] && state.handObserved[i]
            val spatialMode=state.interaction.handMode==InteractionSettings.SPATIAL
            // A deliberate thumb-index pinch is Enter or grab; fist detection is not a click.
            grip[i]=observed && state.handPinch[i]
            val pointing=observed && state.fingertipPointing[i]
            val current=captured[i] ?: target(i)
            if(observed && pointing && !grip[i] && !g.ownsTarget && current!=null) {
                stableHover[i]=current
                stableHoverAt[i]=now
            }
            // Pinching bends the index and can move its ray: lock the last hovered target.
            var t=captured[i] ?: if(grip[i] && !g.ownsTarget && now-stableHoverAt[i] in 0..400) stableHover[i] ?: current else current
            val candidate=t
            val allowed=candidate?.screen?.let { it in workspace.screens && it.visible && (candidate.kind==FingertipGesture.Kind.CLICK || !it.locked) }
                ?: (candidate==null || candidate.hitKind==Hit.KB || candidate.hitKind==Hit.TB || !state.screenHidden)
            val before=g.captured
            // Both gestures work on content, move bars and resize handles.
            hoveredPose[i]=if((observed && pointing || g.captured) && t!=null && t.hitKind!=Hit.KB && t.hitKind!=Hit.TB) t.pose else null
            claimedKey[i]=if(observed && pointing) t?.id else null
            val click=g.updateSpatial(t?.id,t?.kind ?: FingertipGesture.Kind.CLICK,
                (t?.s ?: 0f)/(t?.pose?.width ?: 1f),(t?.z ?: 0f)/(t?.pose?.width ?: 1f),
                observed,pointing,grip[i],!allowed,now,
                .012f+state.interaction.fingertipSensitivity*.025f,
                maxOf(1000,state.interaction.dwellMs).toLong())
            if(g.ownsTarget && captured[i]==null && t!=null) captured[i]=t
            if(!before && g.captured && t!=null) {
                captured[i]=t
                val anchor=FloatArray(2)
                val anchored=spatialMode && t.pose.intersectSurface(state.rox[i],state.roy[i],state.roz[i],ray[0],ray[1],ray[2],anchor)
                motion[i].begin(t.pose,if(anchored) anchor[0] else t.s,if(anchored) anchor[1] else t.z,t.index,now)
                t.screen?.let { workspace.selectedId=it.id }
            } else if(g.captured && t!=null) {
                if(!observed) motion[i].hold()
                else {
                    val ok=motion[i].update(t.pose,g.phase==FingertipGesture.Phase.RESIZING,state.rox[i],state.roy[i],state.roz[i],ray[0],ray[1],ray[2],now)
                    if(ok && spatialMode && g.phase!=FingertipGesture.Phase.RESIZING) motion[i].updateDepth(t.pose,state.handSpatial[i],now)
                    if(ok) missed[i]=-1 else if(missed[i]<0) missed[i]=now else if(now-missed[i]>350) g.release(now)
                }
            }
            if((before && !g.captured) || (spatialMode && !g.ownsTarget)) { motion[i].end(); captured[i]=null; hoveredPose[i]=null; claimedKey[i]=null; missed[i]=-1 }
            state.fingertipTargetId[i]=if(g.ownsTarget) t?.screen?.id ?: "main" else null
            state.fingertipPhase[i]=g.phase
            state.dwellProgress[i]=g.progress
            t?.let {
                var cursorS=it.s; var cursorZ=it.z
                if(g.captured && it.pose.intersectSurface(state.rox[i],state.roy[i],state.roz[i],ray[0],ray[1],ray[2],hit)) { cursorS=hit[0]; cursorZ=hit[1] }
                if(it.screen!=null) {
                    workspace.hoverId=it.screen.id; workspace.hoverS=cursorS; workspace.hoverZ=cursorZ
                    state.hoverKind[i]=Hit.NONE
                    if(observed && !g.captured) send(it,MotionEvent.ACTION_HOVER_MOVE,now)
                }
                it.pose.surfPoint(cursorS,cursorZ,.015f,point)
                // Keyboard's plane intersection was already computed by pick().
                if(it.hitKind!=Hit.KB) { state.hitX[i]=point[0]; state.hitY[i]=point[1]; state.hitZ[i]=point[2] }
                if(click) {
                    val sent=if(it.screen==null) primaryClick(i,it.hitKind,it.index,it.s,it.z)
                        else if(send(it,MotionEvent.ACTION_DOWN,now)) send(it,MotionEvent.ACTION_UP,now) else false
                    if(sent) state.clickSentAt[i]=now
                    feedback=if(sent) "Clique enviado" else "Destino não recebeu o clique"
                } else feedback=when(g.phase) {
                    FingertipGesture.Phase.DRAGGING->"Movendo · solte a pinça ou dobre o indicador"
                    FingertipGesture.Phase.RESIZING->"Redimensionando · solte a pinça ou dobre o indicador"
                    FingertipGesture.Phase.ARMED->"Pronto · mantenha o indicador estável"
                    FingertipGesture.Phase.HOVER->"Alvo identificado · mantenha o indicador"
                    FingertipGesture.Phase.RELEASED->"Liberado"
                    else->"Estenda o indicador"
                }
            }
        }
    }
}
