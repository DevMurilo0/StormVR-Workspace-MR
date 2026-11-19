package com.stormvr.app.vrroom

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.EditText
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=android.app.Application::class)
class AndroidInteractionTest {
    @Test fun settingsPersistAndResetWithoutAffectingOtherPreferences() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs=activity.getSharedPreferences("vr_interaction",Context.MODE_PRIVATE)
        val s=InteractionSettings().apply { handStyle=2; keyboardScale=1.5f; dwellMs=900; dominantHand=0; pinchThreshold=.3f; diagnostics=true }
        s.save(prefs)
        val loaded=InteractionSettings(); loaded.load(prefs)
        assertEquals(2,loaded.handStyle); assertEquals(900,loaded.dwellMs); assertEquals(0,loaded.dominantHand)
        assertEquals(.3f,loaded.pinchThreshold,0f); assertEquals(1.5f,loaded.keyboardScale,0f); assertTrue(loaded.diagnostics)
        prefs.edit().clear().commit(); loaded.load(prefs)
        assertEquals(1000,loaded.dwellMs); assertEquals(1,loaded.dominantHand); assertEquals(0,loaded.handStyle)
    }
    @Test fun unicodeAndBackspaceUseFocusedNativeEditorWithoutStealingFocus() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val root=LinearLayout(activity)
        val first=EditText(activity); val second=EditText(activity)
        root.addView(first); root.addView(second); activity.setContentView(root)
        second.requestFocus()
        assertTrue(VrTextInput.commit(root,"ação"))
        assertEquals("ação",second.text.toString()); assertEquals("",first.text.toString())
        assertTrue(second.hasFocus())
        VrTextInput.key(root,KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))
        VrTextInput.key(root,KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL))
        assertEquals("açã",second.text.toString())
    }
    @Test fun keyboardModesHaveLargeSeparateKeysAndBrazilianCharacters() {
        val ui=VrUi()
        assertTrue(ui.keys.any { it.lower=="ç" })
        assertFalse(ui.keys.any { it.lower=="F1" })
        val normalHeight=ui.keys.first().r.height()
        assertTrue(normalHeight>70f)
        ui.switchKeyboard(3); assertTrue(ui.keys.any { it.lower=="F12" })
        ui.switchKeyboard(4); assertTrue(ui.keys.any { it.lower=="ã" })
        ui.switchKeyboard(0)
        for((index,key) in ui.keys.withIndex()) assertEquals(index,ui.keyAt(key.r.centerX()/VrState.KB_W,key.r.centerY()/VrState.KB_H))
    }
    @Test fun captureProducesExactlyOneAndroidDownMoveUpOrCancel() {
        val c=ClickCapture(); val time=SystemClock.uptimeMillis()
        val actions=mutableListOf<Int>()
        fun dispatch(down: Boolean,valid: Boolean,hit: Boolean,x: Float) {
            val event=c.update(down,valid,hit,x,30f,3f)
            val action=when(event) {
                ClickCapture.Event.DOWN->MotionEvent.ACTION_DOWN; ClickCapture.Event.MOVE->MotionEvent.ACTION_MOVE
                ClickCapture.Event.UP->MotionEvent.ACTION_UP; ClickCapture.Event.CANCEL->MotionEvent.ACTION_CANCEL
                else->return
            }
            val ev=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,c.x,c.y,0)
            try { actions.add(ev.actionMasked); assertEquals(time,ev.downTime); assertEquals(30f,ev.y,0f) } finally { ev.recycle() }
        }
        dispatch(true,true,true,10f); dispatch(true,true,true,11f); dispatch(true,true,true,20f)
        dispatch(false,true,false,999f); dispatch(false,true,false,999f)
        assertEquals(listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP),actions)
        actions.clear(); dispatch(true,true,true,10f); dispatch(true,false,false,999f)
        assertEquals(listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_CANCEL),actions)
    }
    @Test fun interactionModeAndZoomPersistAndRestoreDefaults() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs=activity.getSharedPreferences("finger-zoom-test",0)
        for(mode in 0..2) {
            InteractionSettings().apply { handMode=mode; dragActivationMs=750; fingertipSensitivity=.7f; cameraZoom=1.8f }.save(prefs)
            val loaded=InteractionSettings().apply { load(prefs) }
            assertEquals(mode,loaded.handMode); assertEquals(750,loaded.dragActivationMs)
            assertEquals(.7f,loaded.fingertipSensitivity,0f); assertEquals(1.8f,loaded.cameraZoom,0f)
        }
        prefs.edit().clear().commit()
        val defaults=InteractionSettings().apply { load(prefs) }
        assertEquals(InteractionSettings.INDICATOR,defaults.handMode)
        assertEquals(600,defaults.dragActivationMs); assertEquals(1f,defaults.cameraZoom,0f)
    }
    @Test fun cameraControlsEnableOnlyWithCameraAndDoNotChangeBrowserOrLensFov() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val state=VrState()
        val web=android.webkit.WebView(activity).apply { settings.textZoom=125 }
        val fov=state.fovDeg
        val controls=CameraZoomControls(activity,state) {}
        fun buttons(view: android.view.View): List<android.widget.Button> =
            if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
            else if(view is android.widget.Button) listOf(view) else emptyList()
        val plus=buttons(controls).first { it.text=="+" }
        assertFalse(plus.isEnabled)
        state.passthrough=true; controls.refresh(); assertTrue(plus.isEnabled)
        plus.performClick(); assertEquals(1.1f,state.interaction.cameraZoom,.001f)
        repeat(30) { plus.performClick() }; assertEquals(2f,state.interaction.cameraZoom,0f)
        assertEquals(fov,state.fovDeg,0f); assertEquals(125,web.settings.textZoom)
        buttons(controls).first { it.text=="Restaurar 1.0x" }.performClick()
        assertEquals(1f,state.interaction.cameraZoom,0f)
        val minus=buttons(controls).first { it.text=="−" }
        assertTrue(minus.isEnabled)
        repeat(30) { minus.performClick() }
        assertEquals(.4f,state.interaction.cameraZoom,0f)
        assertFalse(minus.isEnabled); assertTrue(plus.isEnabled)
        assertEquals(fov,state.fovDeg,0f); assertEquals(125,web.settings.textZoom)
        state.fullscreen=true; controls.refresh(); assertFalse(plus.isEnabled); assertFalse(minus.isEnabled)
        web.destroy()
    }
    @Test fun indicatorCaptureDoesNotUsePinchAndGamepadRetainsOwnership() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val s=VrState().apply { handsOn=true; handLasers=true; curved=false; updateGeom() }
        val ws=WorkspaceManager(activity,s)
        val displays=WorkspaceDisplays(activity)
        val legacy=WorkspaceInteraction(ws,s,displays)
        var clicks=0
        val finger=FingertipInteraction(s,ws,displays,legacy) { _,_,_,_,_ -> clicks++; true }
        val i=1; val point=FloatArray(3)
        fun aim() {
            s.laserHand[i]=true; s.handValid[i]=true; s.handObserved[i]=true; s.fingertipPointing[i]=true
            s.ax[i]=s.bar.cx; s.az[i]=s.bar.cz; s.hoverKind[i]=Hit.BAR
            s.surfPoint(s.ax[i],s.az[i],0f,point); s.hitX[i]=point[0]; s.hitY[i]=point[1]; s.hitZ[i]=point[2]
        }
        aim(); finger.tick(0); aim(); finger.tick(600)
        assertEquals(FingertipGesture.Phase.DRAGGING,s.fingertipPhase[i]); assertEquals(0,clicks)
        assertFalse(s.pressed[i]); assertFalse(s.handPinch[i])
        s.r2=1f; s.pressed[i]=true; finger.tick(620)
        assertFalse(s.fingertipOwned[i]); assertTrue(s.pressed[i])
        s.r2=0f; s.interaction.handMode=InteractionSettings.PINCH; s.pressed[i]=true; finger.tick(700)
        assertFalse(s.fingertipOwned[i]); assertTrue(s.pressed[i])
    }
    @Test fun nearestScreenReceivesFingerHoverAndLockedFramesCannotBeGrabbed() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val s=VrState().apply { handsOn=true; curved=false; updateGeom() }
        val ws=WorkspaceManager(activity,s).apply { enabled=true }
        val extra=ws.add().apply { pose.apply { curved=false; scrAz=0f; wx=0f; wz=s.wz; distance=2f; updateGeom() } }
        val displays=WorkspaceDisplays(activity)
        val finger=FingertipInteraction(s,ws,displays,WorkspaceInteraction(ws,s,displays)) { _,_,_,_,_ -> true }
        s.laserHand[1]=true; s.handValid[1]=true; s.handObserved[1]=true; s.fingertipPointing[1]=true
        s.ax[1]=s.wx; s.az[1]=s.wz; s.hoverKind[1]=Hit.WIN
        val point=FloatArray(3); s.surfPoint(s.wx,s.wz,0f,point)
        s.hitX[1]=point[0]; s.hitY[1]=point[1]; s.hitZ[1]=point[2]
        finger.tick(0); assertEquals(extra.id,ws.hoverId)
        finger.cancel(); extra.locked=true
        val scale=s.distance/extra.pose.distance
        s.ax[1]=extra.pose.bar.cx*scale; s.az[1]=extra.pose.bar.cz*scale
        s.hoverKind[1]=Hit.NONE
        finger.tick(100); finger.tick(800)
        assertNotEquals(FingertipGesture.Phase.DRAGGING,s.fingertipPhase[1])
    }

    @Test fun twoIndicatorsOnSameContentProduceOnlyOneClick() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val s=VrState().apply { handsOn=true; curved=false; updateGeom() }
        val ws=WorkspaceManager(activity,s); val displays=WorkspaceDisplays(activity)
        var count=0
        val finger=FingertipInteraction(s,ws,displays,WorkspaceInteraction(ws,s,displays)) { _,_,_,_,_ -> count++; true }
        val point=FloatArray(3); s.surfPoint(s.wx,s.wz,0f,point)
        for(i in 0..1) {
            s.laserHand[i]=true; s.handValid[i]=true; s.handObserved[i]=true; s.fingertipPointing[i]=true
            s.ax[i]=s.wx; s.az[i]=s.wz; s.hoverKind[i]=Hit.WIN
            s.hitX[i]=point[0]; s.hitY[i]=point[1]; s.hitZ[i]=point[2]
        }
        finger.tick(0); finger.tick(1000); finger.tick(2100)
        assertEquals(1,count)
    }

    @Test fun unavailableFrontSurfaceStillOccludesOtherWindows() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        for(extraDepth in listOf(2f,4f)) {
            val s=VrState().apply { handsOn=true; curved=false; updateGeom() }
            val ws=WorkspaceManager(activity,s).apply { enabled=true }
            val extra=ws.add().apply { pose.apply { curved=false; scrAz=0f; wx=0f; wz=s.wz; distance=extraDepth; updateGeom() } }
            val displays=WorkspaceDisplays(activity)
            var primaryClicks=0
            val finger=FingertipInteraction(s,ws,displays,WorkspaceInteraction(ws,s,displays)) { _,_,_,_,_ -> primaryClicks++; true }
            val point=FloatArray(3); s.surfPoint(s.wx,s.wz,0f,point)
            fun aim() { for(i in 0..1) {
                s.laserHand[i]=true; s.handValid[i]=true; s.handObserved[i]=true; s.fingertipPointing[i]=true
                s.ax[i]=s.wx; s.az[i]=s.wz; s.hoverKind[i]=Hit.WIN
                s.hitX[i]=point[0]; s.hitY[i]=point[1]; s.hitZ[i]=point[2]
            } }
            aim(); finger.tick(0)
            assertEquals(if(extraDepth<3f) extra.id else null,ws.hoverId)
            aim(); finger.tick(1000)
            assertEquals(if(extraDepth<3f) 0 else 1,primaryClicks)
        }
    }

    @Test fun spatialTapClicksOnceWithoutDwellOrDrag() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val s=VrState().apply { handsOn=true; curved=false; interaction.handMode=InteractionSettings.SPATIAL; updateGeom() }
        val ws=WorkspaceManager(activity,s); val displays=WorkspaceDisplays(activity)
        var clicks=0
        val finger=FingertipInteraction(s,ws,displays,WorkspaceInteraction(ws,s,displays)) { _,_,_,_,_ -> clicks++; true }
        fun tick(t: Long,pinch: Boolean) {
            s.laserHand[1]=true; s.handValid[1]=true; s.handObserved[1]=true; s.fingertipPointing[1]=true; s.handPinch[1]=pinch
            s.ax[1]=s.wx; s.az[1]=s.wz; s.hoverKind[1]=Hit.WIN
            val point=FloatArray(3); s.surfPoint(s.wx,s.wz,0f,point)
            s.hitX[1]=point[0]; s.hitY[1]=point[1]; s.hitZ[1]=point[2]
            finger.tick(t)
        }
        tick(0,false); tick(100,true); tick(180,true)
        assertEquals(0,clicks); assertEquals(FingertipGesture.Phase.ARMED,s.fingertipPhase[1])
        tick(200,false); tick(280,false); tick(350,false)
        assertEquals(1,clicks); assertFalse(s.inputCaptured[1])
    }

    @Test fun spatialCaptureKeepsWindowOnBriefLossThenReleasesWithoutClick() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val s=VrState().apply { handsOn=true; curved=false; interaction.handMode=InteractionSettings.SPATIAL; updateGeom() }
        val ws=WorkspaceManager(activity,s); val displays=WorkspaceDisplays(activity)
        var clicks=0
        val finger=FingertipInteraction(s,ws,displays,WorkspaceInteraction(ws,s,displays)) { _,_,_,_,_ -> clicks++; true }
        s.laserHand[1]=true; s.handValid[1]=true; s.handObserved[1]=true; s.fingertipPointing[1]=true
        s.ax[1]=s.bar.cx; s.az[1]=s.bar.cz; s.hoverKind[1]=Hit.BAR
        val point=FloatArray(3); s.surfPoint(s.bar.cx,s.bar.cz,0f,point)
        s.hitX[1]=point[0]; s.hitY[1]=point[1]; s.hitZ[1]=point[2]
        finger.tick(0); s.handPinch[1]=true; finger.tick(100); finger.tick(180)
        assertEquals("main",s.fingertipTargetId[1])
        val x=s.wx
        s.handObserved[1]=false; s.laserHand[1]=false
        finger.tick(250); assertEquals("main",s.fingertipTargetId[1]); assertEquals(x,s.wx,0f)
        finger.tick(401); assertNull(s.fingertipTargetId[1]); assertEquals(0,clicks)
    }

    @Test fun spatialPreferencesPersistWithoutChangingDefaultMode() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs=activity.getSharedPreferences("spatial-test",Context.MODE_PRIVATE)
        val settings=InteractionSettings().apply { spatialEngine=2; palmWidthMm=92; handMode=InteractionSettings.SPATIAL }
        settings.save(prefs)
        val restored=InteractionSettings(); restored.load(prefs)
        assertEquals(2,restored.spatialEngine); assertEquals(92,restored.palmWidthMm); assertEquals(3,restored.handMode)
        prefs.edit().clear().commit(); restored.load(prefs)
        assertEquals(0,restored.spatialEngine); assertEquals(InteractionSettings.INDICATOR,restored.handMode)
    }

}
