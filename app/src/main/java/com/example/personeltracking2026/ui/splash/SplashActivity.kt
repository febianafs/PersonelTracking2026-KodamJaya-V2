package com.example.personeltracking2026.ui.splash

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.service.MqttLocationService
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.data.repository.AuthRepository
import com.example.personeltracking2026.data.repository.Result
import com.example.personeltracking2026.databinding.ActivitySplashBinding
import com.example.personeltracking2026.ui.bodycam.BodycamActivity
import com.example.personeltracking2026.ui.login.LoginActivity
import com.example.personeltracking2026.ui.main.MainActivity
import com.example.personeltracking2026.ui.personel.PersonelActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.provider.Settings
import com.example.personeltracking2026.core.navigation.LastScreen

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var sessionManager: SessionManager
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.splash_fade_in)
        binding.imgLogo.startAnimation(fadeIn)

        lifecycleScope.launch {
            val minDelay = launch { delay(2000) }

            // Start service setelah Activity benar-benar visible
            // delay singkat memastikan Activity sudah resumed
            delay(500)
            MqttLocationService.startService(this@SplashActivity)

            val destination = if (sessionManager.isLoggedIn()) {
                validateToken()
            } else {
                LoginActivity::class.java
            }

            minDelay.join()

            binding.root.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    val intent = Intent(this@SplashActivity, destination)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }.start()
        }
    }

    private suspend fun validateToken(): Class<*> {
        val token = sessionManager.getToken() ?: return LoginActivity::class.java

        return when (val result = authRepository.checkToken(token)) {
            is Result.Success -> {

                val lastScreen = sessionManager.getLastScreen()

                if (lastScreen != null) {
                    when (lastScreen) {
                        LastScreen.PERSONEL -> PersonelActivity::class.java
                        LastScreen.BODYCAM  -> BodycamActivity::class.java
                    }
                } else {
                    // fallback kalau belum ada last screen
                    when (sessionManager.getRole()) {
                        SessionManager.ROLE_PERSONEL -> PersonelActivity::class.java
                        SessionManager.ROLE_BODYCAM  -> BodycamActivity::class.java
                        else                         -> MainActivity::class.java
                    }
                }
            }
            else -> LoginActivity::class.java
        }
    }
}