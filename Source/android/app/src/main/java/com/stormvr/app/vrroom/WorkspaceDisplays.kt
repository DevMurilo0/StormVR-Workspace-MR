package com.stormvr.app.vrroom

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.MotionEvent
import android.view.Surface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color

/** Every window has its own compositor, WebView and source URL. No cloned desktop textures. */
class WorkspaceDisplays(private val activity: Activity) {
    private data class Display(val virtual: VirtualDisplay,val presentation: Presentation,val web: WebView,val status: TextView,val surface: Surface,var source: String)
    private var lastBudget=0L
    private val displays=mutableMapOf<String,Display>()
    @SuppressLint("SetJavaScriptEnabled")
    fun attach(screen: VirtualScreen,surface: Surface) {
        remove(screen.id)
        if(activity.isDestroyed || !surface.isValid) return
        val manager=activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val vd=manager.createVirtualDisplay("Workspace-${screen.id}",VrState.TEX_W,VrState.TEX_H,160,surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY) ?: return
        try {
            val p=Presentation(activity,vd.display)
            val status=TextView(p.context).apply { setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(12,25,34)); textSize=18f; setPadding(18,8,18,8); text=screen.name }
            val w=WebView(p.context).apply {
                settings.javaScriptEnabled=true; settings.domStorageEnabled=true
                settings.mediaPlaybackRequiresUserGesture=false
                webViewClient=object: WebViewClient() {
                    override fun onPageFinished(view: WebView,url: String) { screen.connection="Página carregada"; status.text="${screen.name} · ${screen.connection}" }
                }
            }
            val box=LinearLayout(p.context).apply { orientation=LinearLayout.VERTICAL; addView(status); addView(w,LinearLayout.LayoutParams(-1,0,1f)) }
            p.setContentView(box); p.show(); p.window?.setLayout(-1,-1); displays[screen.id]=Display(vd,p,w,status,surface,screen.source)
            screen.connection="Conectando"; w.loadUrl(screen.source)
        } catch(e: Exception) { vd.release(); screen.connection="Erro: ${e.javaClass.simpleName}" }
    }
    fun navigate(screen: VirtualScreen) { displays[screen.id]?.let { it.status.text=screen.name; if(it.source!=screen.source) { it.source=screen.source; it.web.loadUrl(screen.source) } } }
    fun touch(id: String,ev: MotionEvent) { displays[id]?.presentation?.window?.decorView?.dispatchTouchEvent(ev) }
    fun retain(ids: Set<String>) { displays.keys.toList().filterNot { it in ids }.forEach { remove(it) } }
    fun budget(screens: List<VirtualScreen>,state: VrState) {
        val now=android.os.SystemClock.uptimeMillis()
        if(now-lastBudget<1000) return
        lastBudget=now
        val c=FloatArray(3)
        for(screen in screens) {
            screen.pose.screenCenter(c)
            val x=c[0]-state.headX; val y=c[1]-state.headY; val z=c[2]-state.headZ
            val length=kotlin.math.sqrt(x*x+y*y+z*z).coerceAtLeast(.001f)
            val facing=(x*state.gaze[0]+y*state.gaze[1]+z*state.gaze[2])/length
            val pause=!screen.visible || facing<.05f
            displays[screen.id]?.web?.evaluateJavascript("window.stormvrPaused="+pause,null)
        }
    }
    fun resume() { displays.values.forEach { if(it.surface.isValid) it.virtual.surface=it.surface; it.web.onResume(); it.web.evaluateJavascript("window.stormvrPaused=false",null) } }
    fun pause() { displays.values.forEach { it.web.evaluateJavascript("window.stormvrPaused=true",null); it.web.onPause(); it.virtual.surface=null } }
    fun remove(id: String) { displays.remove(id)?.let { it.web.stopLoading(); it.presentation.dismiss(); it.web.destroy(); it.virtual.release() } }
    fun close() { displays.keys.toList().forEach { remove(it) } }
}
