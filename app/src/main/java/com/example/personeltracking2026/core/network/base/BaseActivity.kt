package com.example.personeltracking2026.core.base

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import android.os.PowerManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.core.sos.SosManager
import com.example.personeltracking2026.ui.about.AboutActivity
import com.example.personeltracking2026.ui.bodycam.BodycamActivity
import com.example.personeltracking2026.ui.login.LoginActivity
import com.example.personeltracking2026.ui.settings.SettingsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var connectivityManager: ConnectivityManager
    private var noInternetSnackbar: Snackbar? = null

    // ─── SOS ─────────────────────────────────────────────────────────────────
    private var sosBlinkJob: Job? = null

    /**
     * Override di Activity yang punya toolbar dengan ID berbeda,
     * atau biarkan null kalau Activity tidak punya toolbar (Login, Main).
     * Default: cari toolbar dengan ID R.id.toolbar.
     */
    open fun getSosToolbar(): androidx.appcompat.widget.Toolbar? =
        findViewById(R.id.toolbar)

    /**
     * Set true di LoginActivity dan MainActivity agar SOS tidak aktif di sana.
     */
    open val isSosEnabled: Boolean get() = true

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { hideNoInternetBanner() }
        }

        override fun onLost(network: Network) {
            runOnUiThread { showNoInternetBanner() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (isSosEnabled) {
            observeSosState()
        }
    }

    override fun onResume() {
        super.onResume()
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        if (!isInternetAvailable()) {
            showNoInternetBanner()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) { /* Ignore */ }
    }

    override fun onDestroy() {
        super.onDestroy()
        sosBlinkJob?.cancel()
    }

    // ─── HARDWARE BUTTON ─────────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isSosEnabled && keyCode == KeyEvent.KEYCODE_F3) {
            SosManager.toggle()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // ─── SOS OBSERVER ────────────────────────────────────────────────────────

    private fun observeSosState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SosManager.isActive.collect { isActive ->
                    if (isActive) {
                        startToolbarBlink()
                        Toast.makeText(
                            this@BaseActivity,
                            "⚠ SOS ACTIVE — Press again to cancel",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        stopToolbarBlink()
                    }
                }
            }
        }
    }

    private fun startToolbarBlink() {
        sosBlinkJob?.cancel()
        sosBlinkJob = lifecycleScope.launch {
            var toggle = false
            while (SosManager.isActive.value) {
                getSosToolbar()?.setBackgroundColor(
                    if (toggle) Color.parseColor("#FF1744")
                    else Color.parseColor("#0D2138")
                )
                toggle = !toggle
                delay(500)
            }
        }
    }

    private fun stopToolbarBlink() {
        sosBlinkJob?.cancel()
        sosBlinkJob = null
        getSosToolbar()?.setBackgroundResource(R.color.secondary)
    }

    // ─── NETWORK ─────────────────────────────────────────────────────────────

    private fun isInternetAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNoInternetBanner() {
        val rootView = window.decorView.findViewById<View>(android.R.id.content)
        noInternetSnackbar = Snackbar.make(rootView, "No internet connection", Snackbar.LENGTH_INDEFINITE)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.secondary))
            .setTextColor(ContextCompat.getColor(this, R.color.white))
            .setActionTextColor(ContextCompat.getColor(this, R.color.primary))
        noInternetSnackbar?.show()
    }

    private fun hideNoInternetBanner() {
        noInternetSnackbar?.dismiss()
        noInternetSnackbar = null
    }

    // ─── OVERFLOW MENU ───────────────────────────────────────────────────────

    fun showOverflowMenu(anchor: View) {
        val wrapper = ContextThemeWrapper(this, R.style.DarkPopupMenu)
        val popup = PopupMenu(wrapper, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_bodycam -> {
                    val intent = Intent(this, BodycamActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.action_setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.action_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }
                R.id.action_logout -> {
                    showLogoutConfirmation()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    protected fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    fun showLogoutConfirmation() {
        val title = SpannableString("Logout")
        title.setSpan(
            ForegroundColorSpan(getColor(R.color.white)),
            0, title.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val message = SpannableString("Are you sure you want to logout?")
        message.setSpan(
            ForegroundColorSpan(getColor(R.color.white)),
            0, message.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        MaterialAlertDialogBuilder(this, R.style.DarkAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Logout") { _, _ -> logout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        sessionManager.clearSession()
        SosManager.deactivate() // Matikan SOS saat logout
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}