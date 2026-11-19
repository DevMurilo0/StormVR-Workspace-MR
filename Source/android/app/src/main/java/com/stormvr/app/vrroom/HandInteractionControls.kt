package com.stormvr.app.vrroom

import android.content.Context
import android.graphics.Color
import android.widget.*

/** Shared by the initial Hands tab and the in-VR workspace preferences. */
class HandInteractionControls(context: Context,settings: InteractionSettings,changed: ()->Unit): LinearLayout(context) {
    init {
        orientation=VERTICAL
        fun label(value: String)=TextView(context).apply { text=value; textSize=20f; setTextColor(Color.WHITE); setPadding(8,12,8,8) }
        addView(label("Modo de interação das mãos"))
        val modes=RadioGroup(context)
        listOf("Indicador (recomendado)","Pinça (clássico)","Permanência","Espacial (experimental)").forEachIndexed { i,name ->
            modes.addView(RadioButton(context).apply { id=100+i; text=name; textSize=18f; setTextColor(Color.WHITE); minHeight=(52*resources.displayMetrics.density).toInt(); contentDescription=name })
        }
        modes.check(100+settings.handMode)
        modes.setOnCheckedChangeListener { _,id -> settings.handMode=(id-100).coerceIn(0,3); changed() }
        addView(modes)
        addView(label("Estimativa 3D · comparação no dispositivo"))
        val engines=RadioGroup(context)
        listOf("Padrão atual", "Spatial · monocular", "Spatial · Raw Depth experimental").forEachIndexed { i,name ->
            engines.addView(RadioButton(context).apply {
                id=200+i; text=name; textSize=18f; setTextColor(Color.WHITE)
                minHeight=(52*resources.displayMetrics.density).toInt(); contentDescription=name
            })
        }
        engines.check(200+settings.spatialEngine)
        engines.setOnCheckedChangeListener { _,id -> settings.spatialEngine=(id-200).coerceIn(0,2); changed() }
        addView(engines)
        val palm=label("Largura da palma: ${settings.palmWidthMm} mm")
        addView(palm)
        addView(SeekBar(context).apply {
            contentDescription="Largura entre as bases do indicador e do mínimo, em milímetros"
            max=70; progress=settings.palmWidthMm-45
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?,value: Int,user: Boolean) {
                    if(user) { settings.palmWidthMm=45+value; palm.text="Largura da palma: ${settings.palmWidthMm} mm"; changed() }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
        addView(label("Meça entre as bases do indicador e do mínimo com a mão aberta. A escala continua aproximada. Raw Depth exige suporte e amostras recentes; sem elas, usa monocular. No modo Espacial: aponte, faça uma pinça breve para clicar; feche sobre a barra ou canto para capturar e abra para soltar. Puxar/empurrar exige profundidade confiável.").apply { textSize=16f })
        addView(Switch(context).apply {
            text="Diagnóstico espacial"; textSize=18f; setTextColor(Color.WHITE); isChecked=settings.diagnostics
            minHeight=(52*resources.displayMetrics.density).toInt()
            setOnCheckedChangeListener { _,value -> settings.diagnostics=value; changed() }
        })
        val time=label("Ativar arraste: ${settings.dragActivationMs} ms")
        addView(time)
        addView(SeekBar(context).apply {
            contentDescription="Tempo de ativação do arraste"; max=900; progress=settings.dragActivationMs-300
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?,value: Int,user: Boolean) { if(user) { settings.dragActivationMs=300+value; time.text="Ativar arraste: ${settings.dragActivationMs} ms"; changed() } }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
        addView(label("Sensibilidade do indicador"))
        addView(SeekBar(context).apply {
            contentDescription="Sensibilidade do indicador"; max=100; progress=(settings.fingertipSensitivity*100).toInt()
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?,value: Int,user: Boolean) { if(user) { settings.fingertipSensitivity=value/100f; changed() } }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
        addView(label("Indicador: mantenha na barra superior ou no canto até ativar. Depois mova a mão; dobre o indicador para soltar. Botões e teclas usam um único clique por permanência. Permanência seleciona conteúdo; para mover telas, escolha Indicador ou Pinça.").apply { textSize=16f })
    }
}
