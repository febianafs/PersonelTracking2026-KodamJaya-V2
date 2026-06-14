package com.example.personeltracking2026kodamjayav2.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.personeltracking2026kodamjayav2.R
import com.example.personeltracking2026kodamjayav2.core.session.SessionManager
import com.example.personeltracking2026kodamjayav2.databinding.ActivityMainBinding
import com.example.personeltracking2026kodamjayav2.ui.bodycam.BodycamActivity
import com.example.personeltracking2026kodamjayav2.ui.login.LoginActivity
import com.example.personeltracking2026kodamjayav2.ui.personel.PersonelActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // Cek token dulu
        if (!sessionManager.isLoggedIn()) {
            goToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Disable back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        binding.layoutLoading.visibility = View.GONE
        binding.layoutContent.visibility = View.VISIBLE
        showRoleSelection()
    }

    private fun showRoleSelection() {
        binding.layoutContent.visibility = View.VISIBLE

        val chartAnim = AnimationUtils.loadAnimation(this, R.anim.chart_enter)

        binding.cardPersonel.setOnClickListener {
            animateCardSelect(isPersonel = true)
            binding.cardPersonel.postDelayed({
                sessionManager.saveRole(SessionManager.ROLE_PERSONEL)
                goToPersonel()
            }, 200)
        }

        binding.cardBodycam.setOnClickListener {
            animateCardSelect(isPersonel = false)
            binding.cardBodycam.postDelayed({
                sessionManager.saveRole(SessionManager.ROLE_BODYCAM)
                goToBodycam()
            }, 200)
        }
    }

    private fun animateCardSelect(isPersonel: Boolean) {
        val selectedCard = if (isPersonel) binding.cardPersonel else binding.cardBodycam
        val otherCard = if (isPersonel) binding.cardBodycam else binding.cardPersonel

        selectedCard.strokeColor = getColor(R.color.primary)
        selectedCard.strokeWidth = 4
        otherCard.strokeColor = getColor(R.color.gray)
        otherCard.strokeWidth = 2

        selectedCard.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                selectedCard.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }.start()
    }

    private fun goToPersonel() {
        val intent = Intent(this, PersonelActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun goToBodycam() {
        val intent = Intent(this, BodycamActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && binding.layoutContent.visibility == View.VISIBLE) {
            val chartAnim = AnimationUtils.loadAnimation(this, R.anim.chart_enter)
        }
    }
}