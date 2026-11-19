package com.stormvr.app.vrroom

import android.os.SystemClock
import android.view.MotionEvent
import kotlin.math.*

/** Shares the existing filtered hand/gamepad rays, not a second camera or MediaPipe instance. */
class WorkspaceInteraction(private val workspace: WorkspaceManager,private val state: VrState,private val displays: WorkspaceDisplays) {
    private data class Action(val screen: VirtualScreen,val kind: Int,val s: Float,val z: Float,val width: Float,val x: Float,val height: Float,val started: Long,val hand: Boolean)
    private val manipulation=Array(2) { FingertipManipulation() }
    private val captures=Array(2) { ClickCapture() }
    private val dwell=Array(2) { DwellSelector() }
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
    fun cancel() { scaleScreen=null; for(i in 0..1) { actions[i]?.takeIf { it.kind==0 }?.let { send(it,MotionEvent.ACTION_CANCEL,captures[i].x,captures[i].y) }; captures[i].cancel(); manipulation[i].end(); dwell[i].reset(); actions[i]=null; wasDown[i]=false } }
    private fun send(a: Action,kind: Int,s: Float,z: Float): Boolean {
        val g=a.screen.pose.win
        val x=SurfaceCoordinates.pixel(s,g.cx,g.w,VrState.TEX_W)
        val y=SurfaceCoordinates.pixel(z,g.cz,g.h,VrState.TEX_H,true)
        val ev=MotionEvent.obtain(a.started,SystemClock.uptimeMillis(),kind,x,y,0)
        return try { displays.touch(a.screen.id,ev) } finally { ev.recycle() }
    }
    fun owns(pose: VrState)=actions.any { it?.screen?.pose === pose }
    fun tick() {
        if(!workspace.enabled) return
        val selected=workspace.selected
        val dual=state.interaction.handMode==InteractionSettings.PINCH && state.twoHands && state.handValid[0] && state.handValid[1] && state.handPinch[0] && state.handPinch[1] &&
            selected!=null && !selected.locked && (actions.any { it?.screen?.id == selected.id && it.kind != 0 } || scaleScreen==selected.id)
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

        if(!state.fingertipOwned.any { it }) workspace.hoverId=null
        for(order in 0..1) {
            val i=if(order==0) state.interaction.dominantHand else 1-state.interaction.dominantHand
            if(state.fingertipOwned[i]) { wasDown[i]=false; continue }
            val down=state.pressed[i]
            state.surfPoint(state.ax[i],state.az[i],0f,point)
            val ox=state.rox[i]; val oy=state.roy[i]; val oz=state.roz[i]
            val dx=point[0]-ox; val dy=point[1]-oy; val dz=point[2]-oz
            var action=actions[i]
            if(action==null) {
                // Foreground keyboard and toolbar own their rays; never press through them.
                if(state.inputCaptured[i] || state.hoverKind[i]==Hit.KB || state.hoverKind[i]==Hit.KBBAR || state.hoverKind[i]==Hit.TB) {
                    wasDown[i]=down; dwell[i].reset(); continue
                }
                var best: VirtualScreen?=null; var bestDistance=Float.MAX_VALUE; var bs=0f; var bz=0f
                for(screen in workspace.screens) {
                    val p=screen.pose; if(!screen.visible) continue
                    val surface=SpatialRaycast.hit(p,ox,oy,oz,dx,dy,dz,screen.locked) ?: continue
                    hit[0]=surface.s; hit[1]=surface.z; val dist=surface.distanceSquared
                    if(dist<bestDistance) { best=screen; bestDistance=dist; bs=hit[0]; bz=hit[1] }
                }
                // Compare the main visible monitor too, so a rear display cannot intercept it.
                if(best!=null && !state.screenHidden) {
                    val main=SpatialRaycast.hit(state,ox,oy,oz,dx,dy,dz)
                    if(main!=null && main.distanceSquared<=bestDistance) best=null
                }
                if(best!=null) {
                    best.pose.surfPoint(bs,bz,.014f,point)
                    state.hitX[i]=point[0]; state.hitY[i]=point[1]; state.hitZ[i]=point[2]
                    if(!down) send(Action(best,0,bs,bz,best.pose.width,0f,0f,SystemClock.uptimeMillis(),false),MotionEvent.ACTION_HOVER_MOVE,bs,bz); state.hoverKind[i]=Hit.NONE; workspace.hoverId=best.id; workspace.hoverS=bs; workspace.hoverZ=bz }
                val dwellClick=!down && (state.dwell || state.interaction.handMode==InteractionSettings.DWELL) && i==state.interaction.dominantHand && dwell[i].update(best?.id,bs,bz,
                    SystemClock.uptimeMillis(),state.interaction.dwellMs.toLong(),.025f)
                state.dwellProgress[i]=dwell[i].progress
                if(dwellClick && best!=null && actions.none { it?.screen?.id==best?.id }) {
                    val a=Action(best,0,bs,bz,best.pose.width,best.pose.wx,best.pose.wz,SystemClock.uptimeMillis(),false)
                    if(send(a,MotionEvent.ACTION_DOWN,bs,bz)) {
                        val accepted=send(a,MotionEvent.ACTION_UP,bs,bz)
                        if(accepted) state.clickSentAt[i]=SystemClock.uptimeMillis()
                        feedback=if(accepted) "Clique enviado" else "Destino recusou o clique"
                    }
                }
                best?.takeIf { down && !wasDown[i] && actions.none { a -> a?.screen?.id==it.id } && state.fingertipTargetId.none { id -> id==it.id } }?.let { screen ->
                    workspace.selectedId=screen.id
                    val p=screen.pose
                    val corner=ScreenControls.cornerAt(p,bs,bz)>=0
                    val kind=if(screen.locked) 0 else if(corner) 2 else if(ScreenControls.inBar(p,bs,bz)) 1 else 0
                    action=Action(screen,kind,bs,bz,p.width,p.wx,p.wz,SystemClock.uptimeMillis(),state.handsOn && state.handValid[i])
                    actions[i]=action
                    if(kind!=0) manipulation[i].begin(p,bs,bz,ScreenControls.cornerAt(p,bs,bz),SystemClock.uptimeMillis())
                    if(kind==0) {
                        captures[i].update(true,true,true,bs,bz,p.width*.012f)
                        if(!send(action!!,MotionEvent.ACTION_DOWN,bs,bz)) {
                            captures[i].cancel(); actions[i]=null; action=null
                            feedback="Destino indisponível"; wasDown[i]=down; state.pressed[i]=false; return@let
                        }
                    }
                    feedback=when(kind) { 1 -> "Movendo ${screen.name}"; 2 -> "Redimensionando ${screen.name}"; else -> "Interagindo com ${screen.name}" }
                }
            }
            action?.let { a ->
                val p=a.screen.pose
                workspace.hoverId=a.screen.id
                val intersects=p.intersectSurface(ox,oy,oz,dx,dy,dz,hit)
                if(intersects) { workspace.hoverS=hit[0]; workspace.hoverZ=hit[1] }
                if(!down || (a.hand && !state.handValid[i]) || a.screen !in workspace.screens || !a.screen.visible) {
                    if(a.kind==0) {
                        val valid=a.screen in workspace.screens && a.screen.visible && (!a.hand || state.handValid[i])
                        val e=captures[i].update(false,valid,intersects,hit[0],hit[1],p.width*.012f)
                        val sent=send(a,if(e==ClickCapture.Event.CANCEL) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,captures[i].x,captures[i].y)
                        if(sent && e!=ClickCapture.Event.CANCEL) state.clickSentAt[i]=SystemClock.uptimeMillis()
                        feedback=if(e==ClickCapture.Event.CANCEL) "Rastreamento perdido · cancelado" else if(sent) "Clique enviado" else "Destino recusou o clique"
                    } else feedback="Posição atualizada"
                    actions[i]=null; manipulation[i].end()
                } else if(intersects) {
                    if(a.kind!=0 && !a.screen.locked) {
                        if(a.hand && !state.handObserved[i]) manipulation[i].hold()
                        else manipulation[i].update(p,a.kind==2,ox,oy,oz,dx,dy,dz,SystemClock.uptimeMillis())
                    }
                    if(a.kind==0 && captures[i].update(true,true,true,hit[0],hit[1],p.width*.012f)==ClickCapture.Event.MOVE) {
                        send(a,MotionEvent.ACTION_MOVE,captures[i].x,captures[i].y); feedback="Arrastando ${a.screen.name}"
                    }
                    p.updateGeom()
                }
                // Other input methods remain active, but this ray must not click through the screen.
                state.hoverKind[i]=Hit.NONE
                state.pressed[i]=false
            }
            wasDown[i]=down
        }
    }
}
