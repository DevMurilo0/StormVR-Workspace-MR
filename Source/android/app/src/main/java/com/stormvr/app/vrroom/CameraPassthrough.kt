package com.stormvr.app.vrroom

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface

/** Camera2 fallback provides monocular video and IMU rotation, never position tracking. */
class CameraPassthrough(private val context: Context,private val status: (String)->Unit) {
    private val thread=HandlerThread("Workspace-Camera2").apply { start() }
    private val handler=Handler(thread.looper)
    private var camera: CameraDevice?=null
    private var session: CameraCaptureSession?=null
    private var surface: Surface?=null
    var aspect=16f/9f
        private set
    @Volatile private var generation=0
    @SuppressLint("MissingPermission")
    fun start(texture: SurfaceTexture): Int {
        stop()
        val epoch=generation
        val manager=context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id=manager.cameraIdList.first { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_BACK }
        val chars=manager.getCameraCharacteristics(id)
        val sizes=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!.getOutputSizes(SurfaceTexture::class.java)
        val size=sizes.filter { it.width<=1280 && it.height<=720 }.maxByOrNull { it.width*it.height } ?: sizes.minBy { it.width*it.height }
        aspect=size.width.toFloat()/size.height
        texture.setDefaultBufferSize(size.width,size.height)
        val output=Surface(texture); surface=output
        manager.openCamera(id,object: CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                if(epoch!=generation) { device.close(); return }; camera=device
                @Suppress("DEPRECATION")
                device.createCaptureSession(listOf(output),object: CameraCaptureSession.StateCallback() {
                    override fun onConfigured(capture: CameraCaptureSession) {
                        if(epoch!=generation) { capture.close(); return }; session=capture
                        try {
                            val request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(output); set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                            }.build()
                            capture.setRepeatingRequest(request,null,handler); status("Camera2 · 3DoF · sem mãos neste fallback")
                        } catch(e: Exception) { status("Câmera: ${e.javaClass.simpleName}") }
                    }
                    override fun onConfigureFailed(capture: CameraCaptureSession) { status("Falha ao configurar Camera2") }
                },handler)
            }
            override fun onDisconnected(device: CameraDevice) { device.close(); status("Câmera desconectada") }
            override fun onError(device: CameraDevice,error: Int) { device.close(); status("Erro Camera2: $error") }
        },handler)
        return chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    }
    fun stop() { generation++; session?.close(); session=null; camera?.close(); camera=null; surface?.release(); surface=null }
    fun close() { stop(); thread.quitSafely() }
}
