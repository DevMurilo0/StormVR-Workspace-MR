package com.stormvr.app.vrroom

import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteOrder

/** GL-thread only: close both images before returning; no ARCore resources cross into inference. */
class RawHandDepthReader {
    private var lastTimestamp=0L
    var status="Raw: aguardando"; private set
    fun acquire(frame: Frame): RawHandDepth? {
        try {
            frame.acquireRawDepthImage16Bits().use { depth ->
                frame.acquireRawDepthConfidenceImage().use { confidence ->
                    if(depth.timestamp<=lastTimestamp || depth.timestamp!=confidence.timestamp ||
                        depth.width!=confidence.width || depth.height!=confidence.height) { status="Raw repetida/desalinhada · monocular"; return null }
                    lastTimestamp=depth.timestamp
                    if(kotlin.math.abs(frame.timestamp-depth.timestamp)>35_000_000L) { status="Raw antiga · monocular"; return null }
                    val w=depth.width; val h=depth.height
                    val dp=depth.planes[0]; val cp=confidence.planes[0]
                    val db=dp.buffer.order(ByteOrder.nativeOrder()); val cb=cp.buffer
                    val z=IntArray(w*h); val c=IntArray(w*h)
                    for(y in 0 until h) for(x in 0 until w) {
                        z[y*w+x]=db.getShort(y*dp.rowStride+x*dp.pixelStride).toInt() and 0xffff
                        c[y*w+x]=cb.get(y*cp.rowStride+x*cp.pixelStride).toInt() and 0xff
                    }
                    val uv=FloatArray(6)
                    frame.transformCoordinates2d(Coordinates2d.IMAGE_NORMALIZED,floatArrayOf(0f,0f,1f,0f,0f,1f),Coordinates2d.TEXTURE_NORMALIZED,uv)
                    status="Raw recente · validação por mão"
                    return RawHandDepth(w,h,z,c,depth.timestamp,uv)
                }
            }
        } catch(_: com.google.ar.core.exceptions.NotYetAvailableException) { status="Raw indisponível · monocular"; return null }
          catch(_: com.google.ar.core.exceptions.DeadlineExceededException) { return null }
          catch(_: com.google.ar.core.exceptions.ResourceExhaustedException) { return null }
          catch(_: IllegalStateException) { status="Raw desligada · monocular"; return null }
          catch(_: RuntimeException) { status="Raw falhou · monocular"; return null }
    }
}
