package com.stormvr.app.vrroom

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.*

/** Content is never inferred from a window's position: each extra screen owns a display. */
data class VirtualScreen(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Monitor",
    var source: String = "https://www.google.com",
    val pose: VrState = VrState(),
    var visible: Boolean = true,
    var locked: Boolean = false,
    @Volatile var focused: Boolean = false,
    @Volatile var connection: String = "Aguardando superfície"
)

class WorkspaceManager(private val context: Context, private val primary: VrState) {
    @Volatile var enabled = false
    @Volatile var screens: List<VirtualScreen> = emptyList()
        private set
    @Volatile var selectedId: String? = null
        set(value) { field=value; screens.forEach { it.focused=it.id==value } }
    @Volatile var hoverId: String? = null
    @Volatile var hoverS=0f
    @Volatile var hoverZ=0f
    var uiOpacity = 0.94f
    val selected: VirtualScreen? get() = screens.firstOrNull { it.id == selectedId }
    fun add(source: String = "https://www.google.com"): VirtualScreen {
        check(screens.size < 2) { "Limite inicial: três telas, incluindo a principal" }
        val screen = VirtualScreen(name = "Monitor ${screens.size + 2}", source = source)
        screen.pose.apply { width = 2.2f; distance = 2.6f; wz = .3f; scrAz = if (screens.isEmpty()) -55f else 55f; curved = false; updateGeom() }
        screens = screens + screen; selectedId = screen.id
        return screen
    }
    fun remove(id: String) { screens = screens.filterNot { it.id == id }; if (selectedId == id) selectedId = null }
    fun preset(kind: Int) {
        if(kind==3) return
        while (screens.size < if (kind == 0) 0 else 2) add()
        if (kind == 0) screens = emptyList()
        primary.apply { wx = 0f; wz = .3f; distance = 2.6f; width = 2.2f; scrAz = 0f; scrEl = 0f; scrYaw = 0f; scrPitch = 0f; scrRoll = 0f; updateGeom() }
        if (kind != 3) screens.forEachIndexed { i, screen -> screen.pose.apply {
            wx = 0f; wz = .3f; distance = 2.6f; width = 2.2f
            scrAz = (if (i == 0) -1 else 1) * (if (kind == 2) 52f else 60f)
            scrEl = 0f; scrYaw = 0f; scrPitch = 0f; scrRoll = 0f; curved = kind == 2; updateGeom()
        } }
    }
    fun center(screen: VirtualScreen) { screen.pose.apply {
        scrAz = Math.toDegrees(atan2(primary.gaze[0], primary.gaze[1]).toDouble()).toFloat()
        scrEl = Math.toDegrees(atan2(primary.gaze[2], hypot(primary.gaze[0], primary.gaze[1])).toDouble()).toFloat().coerceIn(-75f,75f)
        wx = 0f; wz = 0f; updateGeom()
    } }
    private fun encodePose(p: VrState) = JSONObject().apply {
        put("width", p.width); put("distance", p.distance); put("x", p.wx); put("z", p.wz)
        put("az", p.scrAz); put("el", p.scrEl); put("yaw", p.scrYaw); put("pitch", p.scrPitch); put("roll", p.scrRoll)
        put("curved", p.curved); put("curve", p.workspaceCurve)
    }
    private fun decodePose(j: JSONObject, p: VrState) { p.apply {
        fun f(k: String, d: Float, lo: Float, hi: Float): Float = j.optDouble(k,d.toDouble()).toFloat().let { if(it.isFinite()) it.coerceIn(lo,hi) else d }
        width = f("width",2.2f,.6f,7.5f); distance = f("distance",2.6f,1f,6f)
        wx = f("x",0f,-8f,8f); wz = f("z",.3f,-4f,4f)
        scrAz = f("az",0f,-180f,180f); scrEl = f("el",0f,-80f,80f)
        scrYaw = f("yaw",0f,-180f,180f); scrPitch = f("pitch",0f,-180f,180f); scrRoll = f("roll",0f,-180f,180f)
        curved = j.optBoolean("curved",false); workspaceCurve = f("curve",1f,.1f,1.5f); updateGeom()
    } }
    fun save(): Boolean {
        val list = JSONArray(); screens.forEach { s -> list.put(JSONObject().apply {
            put("id",s.id); put("name",s.name.take(64)); put("source",s.source)
            put("visible",s.visible); put("locked",s.locked); put("pose",encodePose(s.pose))
        }) }
        return context.getSharedPreferences("workspace_mr",0).edit().putString("layout",JSONObject().apply {
            put("version",1); put("selected",selectedId ?: ""); put("primary",encodePose(primary)); put("screens",list); put("opacity",uiOpacity)
        }.toString()).commit()
    }
    fun load(): Boolean = try {
        val raw = context.getSharedPreferences("workspace_mr",0).getString("layout",null)
        if (raw == null) false else {
            val j = JSONObject(raw); require(j.getInt("version") == 1)
            val list = j.getJSONArray("screens"); require(list.length() <= 2)
            val loaded = (0 until list.length()).map { i -> val o=list.getJSONObject(i)
                VirtualScreen(id=o.getString("id"),name=o.getString("name").take(64),source=o.getString("source"),visible=o.optBoolean("visible",true),locked=o.optBoolean("locked",false)).also {
                    require(it.source.startsWith("https://") || it.source == "about:blank")
                    decodePose(o.getJSONObject("pose"),it.pose)
                }
            }
            require(loaded.map { it.id }.distinct().size == loaded.size)
            decodePose(j.getJSONObject("primary"),primary); screens=loaded; selectedId=j.optString("selected","").takeIf { id -> loaded.any { it.id==id } } ?: loaded.firstOrNull()?.id
            uiOpacity=j.optDouble("opacity",.94).toFloat().coerceIn(.7f,1f); true
        }
    } catch (_: Exception) { false }
}
