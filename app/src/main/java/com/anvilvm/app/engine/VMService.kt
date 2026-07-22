package com.anvilvm.app.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VMService : Service() {

    @Inject lateinit var qemuEngine: QemuEngine

    private val binder = VMBinder()
    private var currentConfig: VMConfig? = null

    inner class VMBinder : Binder() {
        fun getService(): VMService = this@VMService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("AnvilVM: Virtual machine ready")
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    fun startVM(config: VMConfig): Int {
        currentConfig = config
        updateNotification("AnvilVM: Running ${config.name}")
        return qemuEngine.startVM(config)
    }

    fun stopVM() {
        qemuEngine.stopVM()
        currentConfig = null
        updateNotification("AnvilVM: Virtual machine stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun isVMRunning(): Boolean = qemuEngine.isRunning()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AnvilVM Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the virtual machine running in the background"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AnvilVM")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val CHANNEL_ID = "anvilvm_service"
        const val NOTIFICATION_ID = 1001
    }
}
