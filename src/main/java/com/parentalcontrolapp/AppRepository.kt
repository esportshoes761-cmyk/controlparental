package com.parentalcontrolapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

object AppRepository {
    private const val PREFS_NAME = "parental_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ROLE = "user_role"
    private const val KEY_EVENTS = "notification_events"
    private const val KEY_CONTACTS_COUNT = "contacts_count"

    private val gson = Gson()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDeviceId(context: Context): String {
        val prefs = prefs(context)
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun saveRole(context: Context, role: String) {
        prefs(context).edit().putString(KEY_ROLE, role).apply()
    }

    fun getRole(context: Context): String {
        return prefs(context).getString(KEY_ROLE, "child") ?: "child"
    }

    fun addNotificationEvent(context: Context, event: String) {
        val prefs = prefs(context)
        val events = getNotificationEvents(context).toMutableList()
        events.add(0, event)
        if (events.size > 50) events.removeLast()
        prefs.edit().putString(KEY_EVENTS, gson.toJson(events)).apply()
    }

    fun getNotificationEvents(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            gson.fromJson(raw, object : TypeToken<List<String>>() {}.type)
        } catch (ex: Exception) {
            emptyList()
        }
    }

    fun saveContactsCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_CONTACTS_COUNT, count).apply()
    }

    fun getContactsCount(context: Context): Int {
        return prefs(context).getInt(KEY_CONTACTS_COUNT, 0)
    }
}
