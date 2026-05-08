package com.parentalcontrolapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MonitorActivity : AppCompatActivity() {
    private lateinit var tvConnectedStatus: TextView
    private lateinit var tvContactsCount: TextView
    private lateinit var tvNotificationCount: TextView
    private lateinit var tvDeviceStatus: TextView
    private lateinit var tvEvents: TextView
    private lateinit var btnRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)

        BackendClient.init(this)

        tvConnectedStatus = findViewById(R.id.tvConnectedStatus)
        tvContactsCount = findViewById(R.id.tvContactsCount)
        tvNotificationCount = findViewById(R.id.tvNotificationCount)
        tvDeviceStatus = findViewById(R.id.tvDeviceStatus)
        tvEvents = findViewById(R.id.tvEvents)
        btnRefresh = findViewById(R.id.btnRefresh)

        btnRefresh.setOnClickListener { refreshStatus() }
        refreshStatus()

        BackendClient.connectWebSocket(
            onMessage = { message -> runOnUiThread { appendEvent(message) } },
            onOpen = { runOnUiThread { tvConnectedStatus.text = getString(R.string.server_status_connected) } },
            onFailure = { error -> runOnUiThread { tvConnectedStatus.text = "Conexión fallida: $error" } }
        )
    }

    private fun refreshStatus() {
        tvContactsCount.text = "Contactos registrados: ${AppRepository.getContactsCount(this)}"
        tvNotificationCount.text = "Notificaciones recibidas: ${AppRepository.getNotificationEvents(this).size}"
        val events = AppRepository.getNotificationEvents(this).joinToString(separator = "\n\n")
        tvEvents.text = if (events.isBlank()) "No hay eventos en tiempo real aún." else events

        BackendClient.fetchDevices(
            onComplete = { devices ->
                runOnUiThread {
                    tvDeviceStatus.text = "Dispositivos activos: ${devices.size}\n" + devices.joinToString(separator = "\n") { it.name + " (" + it.role + ")" }
                }
            },
            onError = { error ->
                runOnUiThread {
                    tvDeviceStatus.text = "No se pudo recuperar dispositivos: $error"
                }
            }
        )
    }

    private fun appendEvent(message: String) {
        AppRepository.addNotificationEvent(this, message)
        tvEvents.text = "${message}\n\n${tvEvents.text}"
        tvNotificationCount.text = "Notificaciones recibidas: ${AppRepository.getNotificationEvents(this).size}"
    }

    override fun onDestroy() {
        BackendClient.closeWebSocket()
        super.onDestroy()
    }
}
