package com.stormvr.app.vrroom

import android.content.Context
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*

/** A compact operational panel, also usable inside a stereoscopic Presentation. */
class WorkspacePanel(ctx: Context,private val manager: WorkspaceManager,private val state: VrState,
    private val changed: () -> Unit,private val performance: (Int) -> Unit,private val enter: () -> Unit,private val leave: (() -> Unit)? = null): ScrollView(ctx) {
    private val body=LinearLayout(ctx).apply { orientation=LinearLayout.VERTICAL; setPadding(24,20,24,24) }
    private val ink=Color.rgb(231,245,250)
    init { setBackgroundColor(Color.rgb(11,22,30)); addView(body); refresh() }
    private fun label(text0: String)=TextView(context).apply { text=text0; setTextColor(ink); textSize=24f; setPadding(8,14,8,8) }
    private fun button(text0: String,action: () -> Unit)=Button(context).apply {
        text=text0; isAllCaps=false; setTextColor(ink); textSize=24f; minHeight=64
        background=GradientDrawable().apply { setColor(Color.rgb(26,48,60)); cornerRadius=12f; setStroke(1,Color.rgb(65,104,120)) }
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { setMargins(4,4,4,4) }
        setOnClickListener { action() }
    }
    private fun slider(name: String,value: Float,lo: Float,hi: Float,set: (Float)->Unit) {
        val l=label("$name: ${"%.2f".format(value)}"); body.addView(l)
        body.addView(SeekBar(context).apply { max=100; progress=((value-lo)/(hi-lo)*100).toInt().coerceIn(0,100)
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?,p: Int,user: Boolean) { if(user) { val v=lo+(hi-lo)*p/100; set(v); l.text="$name: ${"%.2f".format(v)}"; changed() } }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
    }
    fun refresh() {
        body.removeAllViews()
        body.addView(label("StormVR Workspace MR").apply { textSize=36f })
        body.addView(label("Câmera traseira monocular · ${if(state.headPosOk) "ARCore 6DoF" else "3DoF / posição não rastreada"}\nMonitor principal: Moonlight ou apps da Sala VR. Telas adicionais: fontes HTTPS independentes."))
        body.addView(button("Entrar no Workspace MR") { enter() })
        leave?.let { body.addView(button("Voltar à Sala VR") { it() }) }
        val row=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL }
        listOf("Simples","Triplo","Panorâmico","Livre").forEachIndexed { i,n -> row.addView(button(n) { manager.preset(i); changed(); refresh() },LinearLayout.LayoutParams(0,-2,1f)) }
        body.addView(row)
        body.addView(button("Adicionar tela independente (${manager.screens.size+1}/3)") { if(manager.screens.size<2) { manager.add(); changed(); refresh() } }.apply { isEnabled=manager.screens.size<2 })
        body.addView(button("Salvar layout") { val ok=manager.save(); Toast.makeText(context,if(ok) "Layout salvo" else "Falha ao salvar",Toast.LENGTH_SHORT).show() })
        body.addView(button("Carregar layout") { val ok=manager.load(); changed(); refresh(); Toast.makeText(context,if(ok) "Layout restaurado" else "Nenhum layout válido",Toast.LENGTH_SHORT).show() })
        for(screen in manager.screens) body.addView(button("${if(manager.selectedId==screen.id) "Selecionada · " else ""}${screen.name} · ${screen.connection}") { manager.selectedId=screen.id; refresh() })
        manager.selected?.let { screen ->
            val name=EditText(context).apply { setTextColor(ink); textSize=24f; setSingleLine(); setText(screen.name); hint="Nome da tela"; contentDescription="Nome da tela" }
            val source=EditText(context).apply { setTextColor(ink); textSize=24f; setSingleLine(); setText(screen.source); hint="https://computador:8443/view/..."; contentDescription="Fonte HTTPS"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI }
            body.addView(name); body.addView(source)
            body.addView(button("Aplicar nome e fonte") {
                val url=source.text.toString().trim()
                if(!url.startsWith("https://") && url!="about:blank") Toast.makeText(context,"Use uma fonte HTTPS",Toast.LENGTH_SHORT).show()
                else { screen.name=name.text.toString().trim().take(64).ifEmpty { "Monitor" }; screen.source=url; changed(); refresh() }
            })
            body.addView(button("Centralizar na direção do olhar") { manager.center(screen); changed() })
            body.addView(button(if(screen.locked) "Liberar posição" else "Fixar posição") { screen.locked=!screen.locked; refresh() })
            body.addView(button(if(screen.visible) "Ocultar tela" else "Mostrar tela") { screen.visible=!screen.visible; changed(); refresh() })
            val p=screen.pose
            slider("Distância (m)",p.distance,1f,6f) { if(!screen.locked) p.distance=it }
            slider("Largura (m)",p.width,.6f,7.5f) { if(!screen.locked) p.width=it }
            slider("Esquerda / direita (°)",p.scrAz,-180f,180f) { if(!screen.locked) p.scrAz=it }
            slider("Abaixo / acima (°)",p.scrEl,-80f,80f) { if(!screen.locked) p.scrEl=it }
            slider("Rotação (°)",p.scrYaw,-180f,180f) { if(!screen.locked) p.scrYaw=it }
            body.addView(button(if(p.curved) "Usar tela plana" else "Usar tela curva") { p.curved=!p.curved; changed(); refresh() })
            slider("Curvatura",p.workspaceCurve,.1f,1.5f) { p.workspaceCurve=it }
            body.addView(button("Remover ${screen.name}") {
                if(state.interaction.confirmImportant) AlertDialog.Builder(context).setMessage("Remover ${screen.name} do layout?").setPositiveButton("Confirmar") { _,_ -> manager.remove(screen.id); changed(); refresh() }.setNegativeButton("Cancelar",null).show()
                else { manager.remove(screen.id); changed(); refresh() }
            })
        }
        slider("Opacidade da interface",manager.uiOpacity,.7f,1f) { manager.uiOpacity=it }
        body.addView(label("Desempenho · alterações de vídeo valem na próxima conexão"))
        listOf("Economia · 720p / 20 fps","Equilibrado · 720p / 30 fps","Qualidade · 1080p / 30 fps").forEachIndexed { i,n -> body.addView(button(n) { performance(i) }) }
        body.addView(CheckBox(context).apply { text="Rastreamento das mãos"; setTextColor(ink); isChecked=state.handsOn; setOnCheckedChangeListener { _,v -> state.handsOn=v; changed() } })
        body.addView(CheckBox(context).apply { text="Duas mãos (maior consumo)"; setTextColor(ink); isChecked=state.twoHands; setOnCheckedChangeListener { _,v -> state.twoHands=v; changed() } })
        val settings=state.interaction
        body.addView(label("Interação VR"))
        body.addView(HandInteractionControls(context,settings,changed))
        slider("Sensibilidade da pinça",settings.pinchThreshold,.25f,.5f) { settings.pinchThreshold=it }
        slider("Suavização do ponteiro",settings.pointerSmoothing,0f,1f) { settings.pointerSmoothing=it }
        slider("Permanência (segundos)",settings.dwellMs/1000f,.8f,1.2f) { settings.dwellMs=(it*1000).toInt() }
        body.addView(button("Mão dominante: ${if(settings.dominantHand==1) "direita" else "esquerda"}") { settings.dominantHand=1-settings.dominantHand; changed(); refresh() })
        body.addView(button("Visual das mãos: ${listOf("completas","simplificadas","ponteiros")[settings.handStyle]}") { settings.handStyle=(settings.handStyle+1)%3; changed(); refresh() })
        slider("Tamanho do teclado",settings.keyboardScale,.7f,1.8f) { settings.keyboardScale=it }
        slider("Distância do teclado (m)",settings.keyboardDistance,.45f,1.5f) { settings.keyboardDistance=it }
        slider("Intensidade dos indicadores",settings.indicatorIntensity,.2f,1f) { settings.indicatorIntensity=it }
        body.addView(CheckBox(context).apply { text="Confirmar ações importantes"; setTextColor(ink); isChecked=settings.confirmImportant; setOnCheckedChangeListener { _,v -> settings.confirmImportant=v; changed() } })
        body.addView(CheckBox(context).apply { text="Diagnóstico: FPS, mãos, pinça e alvo"; setTextColor(ink); isChecked=settings.diagnostics; setOnCheckedChangeListener { _,v -> settings.diagnostics=v; changed() } })
        body.addView(button("Restaurar padrões de interação") { context.getSharedPreferences("vr_interaction",0).edit().clear().apply(); settings.load(context.getSharedPreferences("vr_interaction",0)); state.dwell=false; changed(); refresh() })
        slider("Suavização da pinça",state.pinchSmooth,0f,1f) { state.pinchSmooth=it }
        slider("Escala das mãos",state.handScale,.3f,3f) { state.handScale=it }
        body.addView(CheckBox(context).apply { text="Clique por permanência (mão ou olhar)"; setTextColor(ink); isChecked=state.dwell; setOnCheckedChangeListener { _,v -> state.dwell=v; changed() } })
        body.addView(label("Indicador: mantenha na barra ou no canto para mover ou redimensionar; dobre para soltar. Pinça clássica disponível nas preferências. Use o menu para distância e posição. Mouse e teclado do PC continuam locais ao Linux."))
    }
}
