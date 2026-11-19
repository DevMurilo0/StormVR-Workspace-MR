package com.stormvr.app.vrroom

import android.content.Context
import android.graphics.Color
import android.widget.*
import java.util.Locale

class CameraZoomControls(context: Context,private val state: VrState,private val changed: ()->Unit): LinearLayout(context) {
    private val value=TextView(context).apply { textSize=24f; setTextColor(Color.WHITE); setPadding(8,8,8,8) }
    private val note=TextView(context).apply { textSize=16f; setTextColor(Color.LTGRAY); setPadding(8,8,8,16) }
    private val slider=SeekBar(context).apply { max=160; contentDescription="Zoom da câmera" }
    private fun button(name: String,action: ()->Unit)=Button(context).apply { text=name; textSize=22f; isAllCaps=false; minHeight=(56*resources.displayMetrics.density).toInt(); contentDescription=name; setOnClickListener { action() } }
    private val minus=button("−") { setZoom(state.interaction.cameraZoom-.1f) }
    private val plus=button("+") { setZoom(state.interaction.cameraZoom+.1f) }
    private val reset=button("Restaurar 1.0x") { setZoom(1f) }
    init {
        orientation=VERTICAL
        addView(TextView(context).apply { text="Zoom da câmera"; textSize=22f; setTextColor(Color.WHITE); setPadding(8,20,8,8) })
        addView(LinearLayout(context).apply {
            orientation=HORIZONTAL
            addView(minus,LayoutParams(0,-2,1f)); addView(value,LayoutParams(0,-2,2f)); addView(plus,LayoutParams(0,-2,1f))
        })
        addView(slider); addView(reset); addView(note)
        slider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,progress: Int,user: Boolean) { if(user) setZoom(CameraZoom.MIN+progress/100f) }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        refresh()
    }
    private fun setZoom(zoom: Float) { state.interaction.cameraZoom=CameraZoom.clamp(zoom); changed(); refresh() }
    fun refresh() {
        val zoom=CameraZoom.clamp(state.interaction.cameraZoom)
        val supported=state.passthrough && !state.fullscreen
        value.text=String.format(Locale.ROOT,"%.1fx",zoom)
        slider.progress=((zoom-CameraZoom.MIN)*100).toInt().coerceIn(0,slider.max)
        slider.isEnabled=supported; minus.isEnabled=supported && zoom>1f; plus.isEnabled=supported && zoom<2f; reset.isEnabled=supported
        note.text=if(supported) "0.4x–0.9x: afastamento VISUAL, com bordas vazias (não amplia o campo capturado pela lente). 1.0x–2.0x: zoom digital. A cena 3D acompanha a imagem; o reconhecimento de mãos usa a imagem original. Não altera WebView ou FOV."
            else "Ative Modo câmera para ajustar. Sem passthrough ou em tela cheia, o zoom da câmera não é aplicado."
    }
}
