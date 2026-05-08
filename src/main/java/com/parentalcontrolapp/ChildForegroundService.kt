package com.parentalcontrolapp

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

class ChildForegroundService : Service() {
    private var isRunning = false
    private lateinit var handler: Handler
    private var imageReader: ImageReader? = null
    private var projectionData: Intent? = null

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("ChildServiceThread")
        thread.start()
        handler = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        BackendClient.init(this)
        startForegroundNotification()
        projectionData = intent?.getParcelableExtra("projectionData")
        startMonitoringLoop()
        projectionData?.let { startScreenCapture(it) }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "child_monitor_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Monitoreo activo", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Parental Control activo")
            .setContentText("El modo hijo está funcionando en segundo plano.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(1001, notification)
    }

    private fun startMonitoringLoop() {
        if (isRunning) return
        isRunning = true
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return
                sendHeartbeat()
                handler.postDelayed(this, 30000)
            }
        })
    }

    private fun sendHeartbeat() {
        val deviceId = AppRepository.getDeviceId(this)
        val contactsCount = AppRepository.getContactsCount(this)
        val contactsPayload = ContactsPayload(deviceId = deviceId, count = contactsCount, contacts = emptyList())
        BackendClient.sendContacts(contactsPayload)
    }

    private fun startScreenCapture(data: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(Activity.RESULT_OK, data) ?: return
        val metrics = resources.displayMetrics
        val reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        projection.createVirtualDisplay(
            "screen_capture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            0,
            reader.surface,
            null,
            handler
        )
        reader.setOnImageAvailableListener({ readerAvailable ->
            val image = readerAvailable.acquireLatestImage() ?: return@setOnImageAvailableListener
            val buffer = image.planes[0].buffer
            val width = image.width
            val height = image.height
            val pixelStride = image.planes[0].pixelStride
            val rowStride = image.planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width
            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            sendScreenCapture(cropped)
            bitmap.recycle()
            cropped.recycle()
        }, handler)
    }

    private fun sendScreenCapture(bitmap: Bitmap) {
        try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos)
            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            BackendClient.sendScreenCapture(ScreenPayload(
                deviceId = AppRepository.getDeviceId(this),
                timestamp = System.currentTimeMillis(),
                imageBase64 = encoded
            ))
        } catch (ex: Exception) {
            Log.e("ChildForegroundService", "Error al enviar captura", ex)
        }
    }

    override fun onDestroy() {
        isRunning = false
        imageReader?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
