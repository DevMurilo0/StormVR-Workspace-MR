package com.stormvr.app.vrroom

import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.*

/** Shares the existing filtered hand/gamepad rays, not a second camera or MediaPipe instance. */
class WorkspaceInteraction(private val workspace: WorkspaceManager,private val state: VrState,private val displays: WorkspaceDisplays) {
    private data class Action(val screen: VirtualScreen,val kind: Int,val s: Float,val z: Float,val width: Float,val x: Float,val height: Float,val started: Long,val hand: Boolean)
    private val actions=arrayOfNulls<Action>(2)
    private var scaleScreen: String?=null
    private var span0=0f
    private var depth0=0f
    private var width0=0f
    private var distance0=0f
    private val wasDown=BooleanArray(2)
    private val hit=FloatArray(2)
    private val point=FloatArray(3)
    var feedback="Aponte para uma tela"
        private set
    fun cancel() { scaleScreen=null; for(i in 0..1) { actions[i]?.let { send(it,MotionEvent.ACTION_CANCEL,it.s,it.z) }; actions[i]=null; wasDown[i]=false } }
    private fun send(a: Action,kind: Int,s: Float,z: Float) {
        val g=a.screen.pose.win
        val x=((s-g.cx)/g.w+.5f).coerceIn(0f,1f)*VrState.TEX_W
        val y=(.5f-(z-g.cz)/g.h).coerceIn(0f,1f)*VrState.TEX_H
        val ev=MotionEvent.obtain(a.started,SystemClock.uptimeMillis(),kind,x,y,0)
        displays.touch(a.screen.id,ev); ev.recycle()
    }
    fun tick() {
        if(!workspace.enabled) return
        val selected=workspace.selected
        val dual=state.twoHands && state.handValid[0] && state.handValid[1] && state.handPinch[0] && state.handPinch[1] &&
            selected!=null && !selected.locked && (actions.any { it?.screen?.id == selected.id } || scaleScreen==selected.id)
        if(dual && selected!=null) {
            val left=state.ball[0]; val right=state.ball[1]
            val span=sqrt((left[0]-right[0]).pow(2)+(left[1]-right[1]).pow(2)+(left[2]-right[2]).pow(2))
            val depth=(left[1]+right[1])*.5f-state.headY
            if(span>.08f) {
                if(scaleScreen!=selected.id) {
                    cancel(); scaleScreen=selected.id; span0=span; depth0=depth; width0=selected.pose.width; distance0=selected.pose.distance
                }
                selected.pose.width=(width0*span/span0).coerceIn(.6f,7.5f)
                selected.pose.distance=(distance0+(depth-depth0)*2f).coerceIn(1f,6f)
                selected.pose.updateGeom(); feedback="Duas mãos: tamanho e distância"
                state.pressed.fill(false); wasDown.fill(true); return
            }
        }
        if(scaleScreen!=null) { scaleScreen=null; wasDown.fill(true) }

        workspace.hoverId=null
        for(i in 0..1) {
            val down=state.pressed[i]
            state.surfPoint(state.ax[i],state.az[i],0f,point)
            val ox=state.rox[i]; val oy=state.roy[i]; val oz=state.roz[i]
            val dx=point[0]-ox; val dy=point[1]-oy; val dz=point[2]-oz
            var action=actions[i]
            if(action==null) {
                var best: VirtualScreen?=null; var bestDistance=Float.MAX_VALUE; var bs=0f; var bz=0f
                for(screen in workspace.screens) {
                    val p=screen.pose; if(!screen.visible) continue
                    if(!p.intersectSurface(ox,oy,oz,dx,dy,dz,hit)) continue
                    val g=p.win; val margin=p.width*.055f
                    if(abs(hit[0]-g.cx)>g.w/2+margin || abs(hit[1]-g.cz)>g.h/2+margin) continue
                    p.surfPoint(hit[0],hit[1],0f,point)
                    val dist=(point[0]-ox).pow(2)+(point[1]-oy).pow(2)+(point[2]-oz).pow(2)
                    if(dist<bestDistance) { best=screen; bestDistance=dist; bs=hit[0]; bz=hit[1] }
                }
                if(best!=null) { workspace.hoverId=best.id; workspace.hoverS=bs; workspace.hoverZ=bz }
                best?.takeIf { down && !wasDown[i] }?.let { screen ->
                    workspace.selectedId=screen.id
                    val p=screen.pose; val g=p.win
                    val corner=abs(bs-g.cx)>g.w*.43f && abs(bz-g.cz)>g.h*.39f
                    val kind=if(screen.locked) 0 else if(corner) 2 else if(bz>g.cz+g.h/2f) 1 else 0
                    action=Action(screen,kind,bs,bz,p.width,p.wx,p.wz,SystemClock.uptimeMillis(),state.handsOn && state.handValid[i])
                    actions[i]=action
                    if(kind==0) send(action!!,MotionEvent.ACTION_DOWN,bs,bz)
                    feedback=when(kind) { 1 -> "Movendo ${screen.name}"; 2 -> "Redimensionando ${screen.name}"; else -> "Interagindo com ${screen.name}" }
                }
            }
            action?.let { a ->
                val p=a.screen.pose
                workspace.hoverId=a.screen.id
                val intersects=p.intersectSurface(ox,oy,oz,dx,dy,dz,hit)
                if(intersects) { workspace.hoverS=hit[0]; workspace.hoverZ=hit[1] }
                if(!down || a.screen !in workspace.screens || !a.screen.visible) {
                    if(a.kind==0) send(a,if(intersects && (!a.hand || state.handValid[i])) MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL,if(intersects) hit[0] else a.s,if(intersects) hit[1] else a.z)
                    actions[i]=null; feedback="Gesto concluído"
                } else if(intersects) {
                    if(a.kind==1 && !a.screen.locked) { p.wx=a.x+hit[0]-a.s; p.wz=a.height+hit[1]-a.z }
                    if(a.kind==2 && !a.screen.locked) p.width=(a.width+2f*(hit[0]-a.s)*sign(a.s-p.win.cx)).coerceIn(.6f,7.5f)
                    if(a.kind==0 && SystemClock.uptimeMillis()-a.started>state.clickFreezeMs) send(a,MotionEvent.ACTION_MOVE,hit[0],hit[1])
                    p.updateGeom()
                }
                // Other input methods remain active, but this ray must not click through the screen.
                state.pressed[i]=false
            }
            wasDown[i]=down
        }
    }
}
