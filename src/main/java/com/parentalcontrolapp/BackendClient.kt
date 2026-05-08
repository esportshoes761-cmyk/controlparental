package com.parentalcontrolapp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response as RetrofitResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

private const val BASE_URL = "http://10.0.2.2:3000/"
private const val WS_URL = "ws://10.0.2.2:3000/updates"

interface BackendApi {
    @POST("api/register")
    fun register(@Body request: DeviceRegisterRequest): Call<Void>

    @POST("api/notification")
    fun sendNotification(@Body request: NotificationPayload): Call<Void>

    @POST("api/contacts")
    fun sendContacts(@Body request: ContactsPayload): Call<Void>

    @POST("api/screen")
    fun sendScreen(@Body request: ScreenPayload): Call<Void>

    @GET("api/devices")
    fun getDevices(): Call<List<DeviceInfo>>

    @GET("api/ping")
    fun ping(): Call<PingResponse>
}

data class DeviceInfo(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("role") val role: String,
    @SerializedName("name") val name: String,
    @SerializedName("platform") val platform: String?,
    @SerializedName("updatedAt") val updatedAt: String?,
    @SerializedName("lastNotification") val lastNotification: NotificationPayload?,
    @SerializedName("lastContacts") val lastContacts: ContactsPayload?
)

data class DeviceRegisterRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("role") val role: String,
    @SerializedName("name") val name: String,
    @SerializedName("platform") val platform: String = "android"
)

data class NotificationPayload(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("packageName") val packageName: String,
    @SerializedName("title") val title: String,
    @SerializedName("text") val text: String,
    @SerializedName("timestamp") val timestamp: Long
)

data class ContactSummary(
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String?
)

data class ContactsPayload(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("count") val count: Int,
    @SerializedName("contacts") val contacts: List<ContactSummary>
)

data class ScreenPayload(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("imageBase64") val imageBase64: String
)

data class PingResponse(
    @SerializedName("status") val status: String
)

object BackendClient {
    private var appContext: Context? = null
    private val gson = Gson()
    private lateinit var api: BackendApi
    private var webSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder().build()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(BackendApi::class.java)
    }

    fun registerDevice(role: String, name: String) {
        val context = appContext ?: return
        val payload = DeviceRegisterRequest(
            deviceId = AppRepository.getDeviceId(context),
            role = role,
            name = name
        )
        api.register(payload).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: RetrofitResponse<Void>) {
                Log.i("BackendClient", "Registro completado: ${response.code()}")
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("BackendClient", "Error al registrar dispositivo", t)
            }
        })
    }

    fun sendNotification(payload: NotificationPayload) {
        val context = appContext ?: return
        api.sendNotification(payload).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: RetrofitResponse<Void>) {
                Log.i("BackendClient", "Notificación enviada: ${response.code()}")
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("BackendClient", "Error al enviar notificación", t)
            }
        })
    }

    fun sendContacts(payload: ContactsPayload) {
        api.sendContacts(payload).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: RetrofitResponse<Void>) {
                Log.i("BackendClient", "Contactos enviados: ${response.code()}")
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("BackendClient", "Error al enviar contactos", t)
            }
        })
    }

    fun sendScreenCapture(payload: ScreenPayload) {
        api.sendScreen(payload).enqueue(object : Callback<Void> {
            override fun onResponse(call: Call<Void>, response: RetrofitResponse<Void>) {
                Log.i("BackendClient", "Captura de pantalla enviada: ${response.code()}")
            }
            override fun onFailure(call: Call<Void>, t: Throwable) {
                Log.e("BackendClient", "Error al enviar captura de pantalla", t)
            }
        })
    }

    fun connectWebSocket(onMessage: (String) -> Unit, onOpen: () -> Unit, onFailure: (String) -> Unit) {
        val request = Request.Builder().url(WS_URL).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessage(text)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t.localizedMessage ?: "Error de socket")
            }
        })
    }

    fun fetchDevices(onComplete: (List<DeviceInfo>) -> Unit, onError: (String) -> Unit) {
        api.getDevices().enqueue(object : Callback<List<DeviceInfo>> {
            override fun onResponse(call: Call<List<DeviceInfo>>, response: RetrofitResponse<List<DeviceInfo>>) {
                if (response.isSuccessful) {
                    onComplete(response.body() ?: emptyList())
                } else {
                    onError("Error al obtener dispositivos: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<List<DeviceInfo>>, t: Throwable) {
                onError(t.localizedMessage ?: "Error de red")
            }
        })
    }

    fun closeWebSocket() {
        webSocket?.close(1000, "Cerrando")
        webSocket = null
    }
}
