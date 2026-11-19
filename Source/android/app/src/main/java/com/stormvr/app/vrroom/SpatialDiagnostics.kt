package com.stormvr.app.vrroom

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Opt-in, one snapshot/second. CPU is whole-process core-equivalent usage; PSS collection has overhead. */
class SpatialDiagnostics {
    private var lastWall=0L
    private var lastCpu=0L
    fun tick(state: VrState,tracker: HandTracker?) {
        if(!state.interaction.diagnostics) { lastWall=0; return }
        val wall=SystemClock.elapsedRealtime()
        if(wall-lastWall<1000) return
        val cpu=Process.getElapsedCpuTime()
        val percent=if(lastWall>0) (cpu-lastCpu)*100.0/(wall-lastWall) else JSONObject.NULL
        lastWall=wall; lastCpu=cpu
        val frame=tracker?.latest
        val hands=JSONArray()
        synchronized(state.handLock) {
            for(h in 0..1) {
                val s=state.handSpatial[h]
                hands.put(JSONObject().put("hand",h).put("valid",state.handValid[h]).put("observed",state.handObserved[h])
                    .put("confidence",s?.confidence ?: JSONObject.NULL).put("distance_m",s?.distance ?: JSONObject.NULL)
                    .put("depth",s?.source?.name ?: "UNKNOWN").put("physical_depth",s?.depthReliable ?: false)
                    .put("phase",state.fingertipPhase[h].name).put("target",state.fingertipTargetId[h] ?: JSONObject.NULL))
            }
        }
        val row=JSONObject().put("time_ms",wall).put("engine",state.interaction.spatialEngine)
            .put("gesture_mode",state.interaction.handMode).put("render_fps",state.glFps).put("tracking_fps",tracker?.procFps ?: 0)
            .put("processing_ms",tracker?.lastMs ?: 0).put("pipeline_ms",(frame?.latNs ?: 0)/1e6)
            .put("cpu_core_percent",percent).put("pss_kb",Debug.getPss()).put("hands",hands)
        Log.i("StormVRSpatial",row.toString())
    }
}
