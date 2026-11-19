package com.stormvr.app.vrroom

/** Digital crop in clip space, paired with the same transform on virtual geometry. No camera API mutation. */
object CameraZoom {
    const val MIN=.4f
    const val MAX=2f
    fun clamp(value: Float)=if(value.isFinite()) value.coerceIn(MIN,MAX) else 1f
    fun effective(value: Float,passthrough: Boolean,fullscreen: Boolean)=if(passthrough && !fullscreen) clamp(value) else 1f
    fun applyProjection(matrix: FloatArray,zoom: Float) {
        val z=clamp(zoom)
        // Column-major: scale the complete X/Y rows, including the off-center principal point.
        for(column in 0..3) { matrix[column*4]*=z; matrix[column*4+1]*=z }
    }
}
