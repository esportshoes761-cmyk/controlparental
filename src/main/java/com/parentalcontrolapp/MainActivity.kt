package com.parentalcontrolapp

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var btnRequestPermissions: Button
    private lateinit var btnOpenNotificationSettings: Button
    private lateinit var roleSelectionLayout: LinearLayout
    private lateinit var btnTutor: Button
    private lateinit var btnChild: Button

    private val permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val allGranted = results.all { it.value }
        if (allGranted) {
            checkNotificationAccess()
        } else {
            tvStatus.text = "Se requieren permisos para continuar. Por favor acepta todos los permisos."
            showPermissionButtons(true)
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { intent ->
                startChildServiceWithCapture(intent)
            }
        } else {
            tvStatus.text = "Permiso de captura de pantalla rechazado. El modo Hijo no estará completo sin este permiso."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        BackendClient.init(this)
        AppRepository.getDeviceId(this)

        tvStatus = findViewById(R.id.tvStatus)
        btnRequestPermissions = findViewById(R.id.btnRequestPermissions)
        btnOpenNotificationSettings = findViewById(R.id.btnOpenNotificationSettings)
        roleSelectionLayout = findViewById(R.id.roleSelectionLayout)
        btnTutor = findViewById(R.id.btnTutor)
        btnChild = findViewById(R.id.btnChild)

        btnRequestPermissions.setOnClickListener { requestPermissionsIfNeeded() }
        btnOpenNotificationSettings.setOnClickListener { openNotificationAccessSettings() }
        btnTutor.setOnClickListener { registerAndOpenTutor() }
        btnChild.setOnClickListener { registerAndStartChild() }

        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        if (allPermissionsGranted()) {
            checkNotificationAccess()
            loadAndSendContacts()
            return
        }
        val permissions = mutableListOf(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun allPermissionsGranted(): Boolean {
        val contactOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PermissionChecker.PERMISSION_GRANTED
        val notificationOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PermissionChecker.PERMISSION_GRANTED
        } else true
        return contactOk && notificationOk
    }

    private fun checkNotificationAccess() {
        val enabled = isNotificationListenerEnabled(this)
        if (!enabled) {
            tvStatus.text = "Activa el acceso a notificaciones para que el tutor reciba los eventos en tiempo real."
            btnOpenNotificationSettings.visibility = Button.VISIBLE
            roleSelectionLayout.visibility = LinearLayout.GONE
        } else {
            tvStatus.text = "Permisos aceptados. Selecciona el rol Tutor o Hijo."
            btnOpenNotificationSettings.visibility = Button.GONE
            roleSelectionLayout.visibility = LinearLayout.VISIBLE
        }
        showPermissionButtons(false)
    }

    private fun showPermissionButtons(show: Boolean) {
        btnRequestPermissions.visibility = if (show) Button.VISIBLE else Button.GONE
    }

    private fun openNotificationAccessSettings() {
        startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    private fun registerAndOpenTutor() {
        AppRepository.saveRole(this, "tutor")
        BackendClient.registerDevice("tutor", "Tutor Device")
        loadAndSendContacts()
        startActivity(Intent(this, MonitorActivity::class.java))
    }

    private fun registerAndStartChild() {
        AppRepository.saveRole(this, "child")
        BackendClient.registerDevice("child", "Child Device")
        loadAndSendContacts()
        requestScreenCapturePermission()
    }

    private fun loadAndSendContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PermissionChecker.PERMISSION_GRANTED) return
        val contacts = mutableListOf<ContactSummary>()
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val phone = it.getString(1)
                contacts.add(ContactSummary(name = name, phone = phone))
                if (contacts.size >= 50) break
            }
        }
        AppRepository.saveContactsCount(this, contacts.size)
        BackendClient.sendContacts(ContactsPayload(AppRepository.getDeviceId(this), contacts.size, contacts))
    }

    private fun requestScreenCapturePermission() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = manager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun startChildServiceWithCapture(data: Intent) {
        hideAppIcon()
        val intent = Intent(this, ChildForegroundService::class.java).apply {
            putExtra("childMode", true)
            putExtra("projectionData", data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        tvStatus.text = "Modo Hijo activo. La app continuará en segundo plano con monitoreo continuo."
        finish()
    }

    private fun hideAppIcon() {
        val componentName = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    companion object {
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat?.contains(pkgName) == true
        }
    }
}
