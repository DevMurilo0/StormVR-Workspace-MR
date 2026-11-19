package com.stormvr.app.vrroom

import android.content.SharedPreferences

/** Separate namespace so restoring interaction defaults never overwrites layouts or Moonlight. */
class InteractionSettings {
    companion object { const val INDICATOR=0; const val PINCH=1; const val DWELL=2; const val SPATIAL=3 }
    @Volatile var handMode=INDICATOR
    @Volatile var spatialEngine=0 // 0 baseline, 1 spatial monocular, 2 experimental Raw Depth
    @Volatile var palmWidthMm=80 // measured index MCP to little MCP; no automatic physical scale claim
    @Volatile var dragActivationMs=600
    @Volatile var fingertipSensitivity=.5f
    @Volatile var cameraZoom=1f
    @Volatile var pinchThreshold=.38f
    @Volatile var pointerSmoothing=.5f
    @Volatile var dwellMs=1000
    @Volatile var dominantHand=1
    @Volatile var confirmImportant=true
    @Volatile var handStyle=0
    @Volatile var keyboardScale=1.15f
    @Volatile var keyboardDistance=.75f
    @Volatile var indicatorIntensity=.8f
    @Volatile var diagnostics=false
    fun load(p: SharedPreferences) {
        handMode=p.getInt("handMode",INDICATOR).coerceIn(INDICATOR,SPATIAL)
        spatialEngine=p.getInt("spatialEngine",0).coerceIn(0,2)
        palmWidthMm=p.getInt("palmWidthMm",80).coerceIn(45,115)
        dragActivationMs=p.getInt("dragActivationMs",600).coerceIn(300,1200)
        fingertipSensitivity=p.getFloat("fingertipSensitivity",.5f).coerceIn(0f,1f)
        cameraZoom=CameraZoom.clamp(p.getFloat("cameraZoom",1f))
        pinchThreshold=p.getFloat("pinch",.38f).coerceIn(.25f,.5f)
        pointerSmoothing=p.getFloat("pointer",.5f).coerceIn(0f,1f)
        dwellMs=p.getInt("dwellMs",1000).coerceIn(1000,2500)
        dominantHand=p.getInt("dominant",1).coerceIn(0,1)
        confirmImportant=p.getBoolean("confirm",true)
        handStyle=p.getInt("style",0).coerceIn(0,2)
        keyboardScale=p.getFloat("kbScale",1.15f).coerceIn(.7f,1.8f)
        keyboardDistance=p.getFloat("kbDistance",.75f).coerceIn(.45f,1.5f)
        indicatorIntensity=p.getFloat("indicators",.8f).coerceIn(.2f,1f)
        diagnostics=p.getBoolean("diagnostics",false)
    }
    fun save(p: SharedPreferences) { p.edit().putInt("spatialEngine",spatialEngine).putInt("palmWidthMm",palmWidthMm).putInt("handMode",handMode).putInt("dragActivationMs",dragActivationMs)
        .putFloat("fingertipSensitivity",fingertipSensitivity).putFloat("cameraZoom",CameraZoom.clamp(cameraZoom)).putFloat("pinch",pinchThreshold).putFloat("pointer",pointerSmoothing)
        .putInt("dwellMs",dwellMs).putInt("dominant",dominantHand).putBoolean("confirm",confirmImportant)
        .putInt("style",handStyle).putFloat("kbScale",keyboardScale).putFloat("kbDistance",keyboardDistance)
        .putFloat("indicators",indicatorIntensity).putBoolean("diagnostics",diagnostics).apply() }
}
