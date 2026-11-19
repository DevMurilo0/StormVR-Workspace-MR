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
}
