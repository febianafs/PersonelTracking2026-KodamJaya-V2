package com.example.personeltracking2026.ui.bodycam

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.personeltracking2026.App
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.base.BaseActivity
import com.example.personeltracking2026.core.device.DeviceMode
import com.example.personeltracking2026.core.mqtt.MqttPayloadBuilder
import com.example.personeltracking2026.core.navigation.LastScreen
import com.example.personeltracking2026.core.service.MqttLocationService
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.core.sos.SosManager
import com.example.personeltracking2026.data.repository.BodycamRepository
import com.example.personeltracking2026.databinding.ActivityBodycamBinding
import com.example.personeltracking2026.ui.login.LoginActivity
import com.example.personeltracking2026.ui.settings.SettingsActivity
import com.example.personeltracking2026.utils.DeviceIdentityManager
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.launch

class BodycamActivity : BaseActivity(), ConnectChecker {

    private lateinit var binding: ActivityBodycamBinding
    private lateinit var rtmpCamera: RtmpCamera2
    private val viewModel: BodycamViewModel by viewModels {
        BodycamViewModel.Factory(BodycamRepository())
    }

    private var isMicEnabled = true
    private var isCameraEnabled = true
    private var isSurfaceReady = false

    private var originalRadius: Float = 0f
    private var originalElevation: Float = 0f
    private var originalMargins: ViewGroup.MarginLayoutParams? = null
    private var hasPublishedStreamStart = false
    private lateinit var sessionManager: SessionManager

    companion object {
        val RESOLUTION_LD = Pair(640, 360)
        val RESOLUTION_SD = Pair(854, 480)
        val RESOLUTION_HD = Pair(1280, 720)
    }

    // ─────────────────────────────────────────────
    //  ConnectChecker callbacks
    // ─────────────────────────────────────────────

    override fun onConnectionStarted(url: String) {}

    override fun onConnectionSuccess() {
        runOnUiThread {
            Toast.makeText(this, "Stream connected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            Toast.makeText(this, "Connection failed: $reason", Toast.LENGTH_SHORT).show()
            viewModel.stopStream()
            startCameraPreview()
        }
    }

    override fun onNewBitrate(bitrate: Long) {}

    override fun onDisconnect() {
        runOnUiThread {
            Toast.makeText(this, "Stream disconnected", Toast.LENGTH_SHORT).show()
            if (viewModel.isLive()) viewModel.stopStream()
        }
    }

    override fun onAuthError() {
        runOnUiThread {
            Toast.makeText(this, "Auth error", Toast.LENGTH_SHORT).show()
            viewModel.stopStream()
        }
    }

    override fun onAuthSuccess() {}

    // ─────────────────────────────────────────────
    //  Permission launcher
    // ─────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        when {
            cameraGranted && audioGranted -> startCameraPreview()
            !cameraGranted -> Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            !audioGranted -> Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBodycamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestBatteryOptimizationExemption()

        val deviceManager = DeviceIdentityManager(this)
        val identity = deviceManager.getIdentity()

        if (deviceManager.isAutoGenerated()) {
            showSerialDialog()
        }

        val serial = identity.serial
        val androidId = identity.androidId

        sessionManager = SessionManager(this)

        val app = application as App

        // FIX: Set mode BODYCAM agar MqttLocationService skip publish Radio payload
        app.currentMode = DeviceMode.BODYCAM

        SosManager.init(
            mqtt             = app.mqttManager,
            session          = sessionManager,
            serial           = serial,
            id               = androidId,
            type             = SosManager.DeviceType.BODYCAM,
            locationProvider = { Triple(app.currentLat, app.currentLon, app.currentAccuracy) }
        )

        WindowCompat.setDecorFitsSystemWindows(window, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val glView = binding.surfaceView as OpenGlView
        rtmpCamera = RtmpCamera2(glView, this)

        binding.surfaceView.post {
            isSurfaceReady = true
            checkAndRequestPermissions()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.isLive()) {
                enterPipMode()
            } else {
                finish()
            }
        }

        binding.cameraCard.post {
            originalRadius = binding.cameraCard.radius
            originalElevation = binding.cameraCard.cardElevation

            val params = binding.cameraCard.layoutParams as ViewGroup.MarginLayoutParams
            originalMargins = ViewGroup.MarginLayoutParams(params)
        }

        setupResolutionToggle()
        setupClickListeners()
        observeStreamState()
        observeTimer()
    }

    override fun onResume() {
        super.onResume()
        SessionManager(this).saveLastScreen(LastScreen.BODYCAM)

        // FIX: Selalu set ulang mode BODYCAM saat resume
        // (mencegah mode berubah kalau balik dari Settings atau layar lain)
        (application as App).currentMode = DeviceMode.BODYCAM

        if (!isSurfaceReady) return
        if (isInPictureInPictureMode) return
        if (hasCameraPermission() && isCameraEnabled && !rtmpCamera.isOnPreview) {
            startCameraPreview()
        }

        val app = application as App
        val identity = DeviceIdentityManager(this).getIdentity() ?: return

        SosManager.init(
            mqtt             = app.mqttManager,
            session          = sessionManager,
            serial           = identity.serial,
            id               = identity.androidId,
            type             = SosManager.DeviceType.BODYCAM,
            locationProvider = { Triple(app.currentLat, app.currentLon, app.currentAccuracy) }
        )
    }

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode) {
            if (viewModel.isLive()) viewModel.stopStream()
            stopCameraPreview()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isSurfaceReady = false
        if (::rtmpCamera.isInitialized) {
            if (rtmpCamera.isStreaming) rtmpCamera.stopStream()
            if (rtmpCamera.isOnPreview) rtmpCamera.stopPreview()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isFinishing && !isDestroyed && viewModel.isLive()) {
            enterPipMode()
        }
    }

    // ─────────────────────────────────────────────
    //  Permission
    // ─────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) startCameraPreview() else permissionLauncher.launch(permissions)
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasAudioPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    // ─────────────────────────────────────────────
    //  Camera Preview
    // ─────────────────────────────────────────────

    private fun startCameraPreview() {
        if (!isSurfaceReady) return
        if (!hasCameraPermission()) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            return
        }

        // FIX: Selalu stop preview dulu sebelum start ulang
        // untuk menghindari IllegalStateException dari MediaCodec
        try {
            if (rtmpCamera.isOnPreview) {
                rtmpCamera.stopPreview()
            }
        } catch (e: Exception) {
            // Abaikan error saat stop
        }

        val res = if (viewModel.isHdSelected.value) RESOLUTION_HD else RESOLUTION_SD

        try {
            // FIX: urutan parameter startPreview: width (res.first) dulu, height (res.second) belakang
            rtmpCamera.startPreview(CameraHelper.Facing.BACK, res.first, res.second)
            binding.layoutIdle?.visibility = View.GONE
            binding.surfaceView?.visibility = View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCameraPreview() {
        try {
            if (::rtmpCamera.isInitialized && rtmpCamera.isOnPreview) {
                rtmpCamera.stopPreview()
            }
        } catch (e: Exception) {
            // Abaikan GL release error dari RootEncoder
        }
    }

    // ─────────────────────────────────────────────
    //  RTMP Stream
    // ─────────────────────────────────────────────

    private fun startRtmpStream() {
        val deviceManager = DeviceIdentityManager(this)
        val identity = deviceManager.getIdentity()

        if (deviceManager.isAutoGenerated()) {
            showSerialDialog()
        }

        val serial = identity.serial
        val url = StreamUtils.getRtmpUrl(serial)

        if (!hasAudioPermission()) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            viewModel.stopStream()
            return
        }

        if (rtmpCamera.isStreaming) return

        val res = if (viewModel.isHdSelected.value) RESOLUTION_HD else RESOLUTION_SD
        val videoBitrate = if (viewModel.isHdSelected.value) 800 * 1024 else 400 * 1024

        try {
            // FIX UTAMA: urutan parameter prepareVideo harus width dulu (res.first),
            // baru height (res.second). Sebelumnya terbalik → IllegalStateException crash.
            //
            // prepareVideo(width, height, fps, bitrate, rotation)
            //   res.first  = width  (misal 1280 untuk HD, 854 untuk SD)
            //   res.second = height (misal 720 untuk HD, 480 untuk SD)
            val prepared = rtmpCamera.prepareAudio(
                96 * 1024,
                16000,
                false
            ) && rtmpCamera.prepareVideo(
                res.first,   // ← width  (FIX: sebelumnya res.second)
                res.second,  // ← height (FIX: sebelumnya res.first)
                30,
                videoBitrate,
                CameraHelper.getCameraOrientation(this)
            )

            if (prepared) {
                // FIX: tambahkan guard isDestroyed dan !rtmpCamera.isStreaming
                // di dalam postDelayed untuk mencegah double-start
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isDestroyed && !isFinishing && !rtmpCamera.isStreaming) {
                        rtmpCamera.startStream(url)
                    }
                }, 400)
            } else {
                Toast.makeText(this, "Unable to setup stream", Toast.LENGTH_SHORT).show()
                viewModel.stopStream()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error stream: ${e.message}", Toast.LENGTH_SHORT).show()
            viewModel.stopStream()
        }
    }

    private fun stopRtmpStream() {
        if (::rtmpCamera.isInitialized && rtmpCamera.isStreaming) {
            rtmpCamera.stopStream()
        }
    }

    // ─────────────────────────────────────────────
    //  Mic Toggle
    // ─────────────────────────────────────────────

    private fun toggleMic() {
        isMicEnabled = !isMicEnabled
        if (isMicEnabled) rtmpCamera.enableAudio() else rtmpCamera.disableAudio()
        binding.btnMic?.setImageResource(
            if (isMicEnabled) R.drawable.ic_mic else R.drawable.ic_mic_off
        )
        binding.btnMic?.alpha = if (isMicEnabled) 1f else 0.5f
        Toast.makeText(
            this,
            if (isMicEnabled) "Microphone active" else "Microphone inactive",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ─────────────────────────────────────────────
    //  Camera On/Off
    // ─────────────────────────────────────────────

    private fun toggleCamera() {
        isCameraEnabled = !isCameraEnabled
        if (isCameraEnabled) {
            binding.btnVideo?.setImageResource(R.drawable.ic_cam)
            binding.btnVideo?.alpha = 1f
            binding.surfaceView?.visibility = View.VISIBLE
            binding.surfaceView.postDelayed({
                if (isSurfaceReady && hasCameraPermission()) startCameraPreview()
            }, 300)
        } else {
            if (viewModel.isLive()) viewModel.stopStream()
            stopCameraPreview()
            binding.surfaceView?.visibility = View.GONE
            binding.btnVideo?.setImageResource(R.drawable.ic_cam_off)
            binding.btnVideo?.alpha = 0.5f
        }
    }

    // ─────────────────────────────────────────────
    //  Resolution Toggle
    // ─────────────────────────────────────────────

    private fun setupResolutionToggle() {
        updateResolutionUi(viewModel.isHdSelected.value)

        binding.btnSd?.setOnClickListener {
            if (!viewModel.isLive()) {
                viewModel.setResolution(false)
                updateResolutionUi(false)
                restartPreviewWithResolution()
            }
        }

        binding.btnHd?.setOnClickListener {
            if (!viewModel.isLive()) {
                viewModel.setResolution(true)
                updateResolutionUi(true)
                restartPreviewWithResolution()
            }
        }
    }

    private fun updateResolutionUi(isHd: Boolean) {
        binding.btnSd?.alpha = if (!isHd) 1f else 0.5f
        binding.btnSd?.setBackgroundResource(
            if (!isHd) R.drawable.bg_resolution_active else android.R.color.transparent
        )
        binding.btnSd?.setTextColor(
            if (!isHd) ContextCompat.getColor(this, android.R.color.white)
            else ContextCompat.getColor(this, R.color.gray)
        )
        binding.btnHd?.alpha = if (isHd) 1f else 0.5f
        binding.btnHd?.setBackgroundResource(
            if (isHd) R.drawable.bg_resolution_active else android.R.color.transparent
        )
        binding.btnHd?.setTextColor(
            if (isHd) ContextCompat.getColor(this, android.R.color.white)
            else ContextCompat.getColor(this, R.color.gray)
        )
        val canChange = !viewModel.isLive()
        binding.btnSd?.isEnabled = canChange
        binding.btnHd?.isEnabled = canChange
    }

    private fun restartPreviewWithResolution() {
        stopCameraPreview()
        binding.surfaceView.postDelayed({
            if (isSurfaceReady && hasCameraPermission()) {
                startCameraPreview()
            }
        }, 300)
    }

    // ─────────────────────────────────────────────
    //  Click Listeners
    // ─────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnGoLive?.setOnClickListener {
            when (viewModel.streamState.value) {
                is StreamState.Idle -> viewModel.startStream()
                is StreamState.Live -> viewModel.stopStream()
                else -> {}
            }
        }
        binding.btnMic?.setOnClickListener { toggleMic() }
        binding.btnVideo?.setOnClickListener { toggleCamera() }
        binding.btnSave?.setOnClickListener { viewModel.saveRecording() }
        binding.btnDiscard?.setOnClickListener { viewModel.discardRecording() }
        binding.btnOverflow?.setOnClickListener { showBodycamMenu(it) }
    }

    // ─────────────────────────────────────────────
    //  Observe
    // ─────────────────────────────────────────────

    private fun observeStreamState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.streamState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun observeTimer() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.timerText.collect { time ->
                    binding.tvTimer?.text = " $time"
                }
            }
        }
    }

    private fun renderState(state: StreamState) {
        when (state) {
            is StreamState.Idle -> {
                stopRtmpStream()
                binding.layoutIdle?.visibility = if (isCameraEnabled) View.VISIBLE else View.GONE
                binding.layoutEnded?.visibility = View.GONE
                binding.liveIndicator?.visibility = View.GONE
                binding.tvLiveText?.text = "Go Live"
                if (isCameraEnabled) {
                    binding.surfaceView?.visibility = View.VISIBLE
                    startCameraPreview()
                }
                updateResolutionUi(viewModel.isHdSelected.value)
                setControlsEnabled(false)
            }
            is StreamState.Live -> {
                startRtmpStream()

                hasPublishedStreamStart = true
                publishBodycamStream()

                binding.layoutIdle?.visibility = View.GONE
                binding.layoutEnded?.visibility = View.GONE
                binding.liveIndicator?.visibility = View.VISIBLE
                binding.tvLiveText?.text = "Stop"
                updateResolutionUi(viewModel.isHdSelected.value)
                setControlsEnabled(true)
            }
            is StreamState.Ended -> {
                stopRtmpStream()
                stopCameraPreview()
                hasPublishedStreamStart = false

                binding.surfaceView?.visibility = View.GONE
                binding.layoutIdle?.visibility = View.GONE
                binding.layoutEnded?.visibility = View.VISIBLE
                binding.liveIndicator?.visibility = View.GONE
                binding.btnSave?.visibility = View.GONE
                binding.tvDuration?.text = "Duration: ${state.duration}"
                binding.tvLiveText?.text = "Go Live"
                updateResolutionUi(viewModel.isHdSelected.value)
                setControlsEnabled(false)
            }
        }
    }

    private fun setControlsEnabled(isLive: Boolean) {
        binding.btnMic?.isEnabled = true
        binding.btnVideo?.isEnabled = true
        binding.btnSd?.isEnabled = !isLive
        binding.btnHd?.isEnabled = !isLive
    }

    private fun showBodycamMenu(anchor: View) {
        val wrapper = ContextThemeWrapper(this, R.style.DarkPopupMenu)

        PopupMenu(wrapper, anchor).apply {
            menuInflater.inflate(R.menu.menu_bodycam, menu)

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_ht -> {
                        // FIX: Set mode ke RADIO sebelum pindah ke PersonelActivity
                        (application as App).currentMode = DeviceMode.RADIO

                        val intent = Intent(
                            this@BodycamActivity,
                            com.example.personeltracking2026.ui.personel.PersonelActivity::class.java
                        )
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                        true
                    }
                    R.id.action_logout -> {
                        showLogoutConfirmation()
                        true
                    }
                    else -> false
                }
            }

            show()
        }
    }

    // ─────────────────────────────────────────────
    //  Logout
    // ─────────────────────────────────────────────

    private fun logoutToLogin() {
        val app = application as App

        // FIX: Set mode NONE saat logout
        app.currentMode = DeviceMode.NONE

        MqttLocationService.stopService(this)
        app.mqttManager.disconnect()
        sessionManager.clearSession()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ─────────────────────────────────────────────
    //  PiP Mode
    // ─────────────────────────────────────────────

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isFinishing || isDestroyed) return

            val rect = Rect()
            binding.surfaceView.getGlobalVisibleRect(rect)

            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setSourceRectHint(rect)
                .build()

            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            binding.pipOverlay?.visibility = View.VISIBLE
            binding.pipOverlay?.alpha = 1f

            binding.surfaceView.postDelayed({
                try {
                    if (rtmpCamera.isOnPreview) {
                        rtmpCamera.stopPreview()
                        // FIX: width (first) dan height (second) konsisten
                        rtmpCamera.startPreview(
                            CameraHelper.Facing.BACK,
                            if (viewModel.isHdSelected.value) RESOLUTION_HD.first else RESOLUTION_SD.first,
                            if (viewModel.isHdSelected.value) RESOLUTION_HD.second else RESOLUTION_SD.second
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                binding.pipOverlay?.postDelayed({
                    binding.pipOverlay?.animate()
                        ?.alpha(0f)
                        ?.setDuration(300)
                        ?.withEndAction { binding.pipOverlay?.visibility = View.GONE }
                        ?.start()
                }, 200)
            }, 50)

            binding.cameraCard.radius = 0f
            binding.cameraCard.cardElevation = 0f
            binding.cameraCard.setCardBackgroundColor(android.graphics.Color.BLACK)
            (binding.cameraCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                setMargins(0, 0, 0, 0)
            }

            binding.toolbar?.visibility = View.GONE
            binding.controlPanel.visibility = View.GONE
            binding.layoutResolution?.visibility = View.GONE
            binding.imgChart?.visibility = View.GONE
            binding.liveIndicator.visibility = View.GONE
        } else {
            binding.cameraCard.radius = originalRadius
            binding.cameraCard.cardElevation = originalElevation
            val params = binding.cameraCard.layoutParams as ViewGroup.MarginLayoutParams
            originalMargins?.let {
                params.setMargins(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin)
            }
            binding.cameraCard.requestLayout()

            binding.toolbar?.visibility = View.VISIBLE
            binding.controlPanel.visibility = View.VISIBLE
            binding.layoutResolution?.visibility = View.VISIBLE
            binding.imgChart?.visibility = View.VISIBLE
            binding.liveIndicator.visibility = View.VISIBLE
        }
    }

    // ─────────────────────────────────────────────
    //  Publish Bodycam Payload
    // ─────────────────────────────────────────────

    private fun publishBodycamStream() {
        val app = application as App
        val identity = DeviceIdentityManager(this).getIdentity() ?: return

        val serial = identity.serial
        val androidId = identity.androidId
        val streamUrl = StreamUtils.getRtmpUrl(serial)

        val payload = MqttPayloadBuilder.buildBodycamDataPayload(
            serialNumber = serial,
            androidId = androidId,
            streamUrl = streamUrl
        )
        app.mqttManager.publishBodycamData(payload)
    }

    // ─────────────────────────────────────────────
    //  Serial Number Dialog
    // ─────────────────────────────────────────────

    private fun showSerialDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_serial_req, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnLater = dialogView.findViewById<Button>(R.id.btnLater)
        val btnSetting = dialogView.findViewById<Button>(R.id.btnGoToSetting)

        btnLater.setOnClickListener { dialog.dismiss() }

        btnSetting.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}