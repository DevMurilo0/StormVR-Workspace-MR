package com.stormvr.app.vrroom

import android.content.SharedPreferences

/** Separate namespace so restoring interaction defaults never overwrites layouts or Moonlight. */
class InteractionSettings {
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
        pinchThreshold=p.getFloat("pinch",.38f).coerceIn(.25f,.5f)
        pointerSmoothing=p.getFloat("pointer",.5f).coerceIn(0f,1f)
        dwellMs=p.getInt("dwellMs",1000).coerceIn(800,1200)
        dominantHand=p.getInt("dominant",1).coerceIn(0,1)
        confirmImportant=p.getBoolean("confirm",true)
        handStyle=p.getInt("style",0).coerceIn(0,2)
        keyboardScale=p.getFloat("kbScale",1.15f).coerceIn(.7f,1.8f)
        keyboardDistance=p.getFloat("kbDistance",.75f).coerceIn(.45f,1.5f)
        indicatorIntensity=p.getFloat("indicators",.8f).coerceIn(.2f,1f)
        diagnostics=p.getBoolean("diagnostics",false)
    }
    fun save(p: SharedPreferences) { p.edit().putFloat("pinch",pinchThreshold).putFloat("pointer",pointerSmoothing)
        .putInt("dwellMs",dwellMs).putInt("dominant",dominantHand).putBoolean("confirm",confirmImportant)
        .putInt("style",handStyle).putFloat("kbScale",keyboardScale).putFloat("kbDistance",keyboardDistance)
        .putFloat("indicators",indicatorIntensity).putBoolean("diagnostics",diagnostics).apply() }
}
