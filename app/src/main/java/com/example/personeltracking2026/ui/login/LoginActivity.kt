package com.example.personeltracking2026.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.data.repository.LoginRepository
import com.example.personeltracking2026.data.repository.Result
import com.example.personeltracking2026.databinding.ActivityLoginBinding
import com.example.personeltracking2026.ui.main.MainActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModel.Factory(LoginRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        handleSmallScreen()
        startPlanetAnimation()
        setupKeyboardScroll()
        observeLoginState()

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.login(username, password)
        }
    }

    private fun observeLoginState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { state ->
                    when (state) {
                        is Result.Loading -> {
                            binding.layoutConnecting.visibility = View.VISIBLE
                            binding.btnLogin.isEnabled = false
                        }
                        is Result.Success -> {
                            binding.layoutConnecting.visibility = View.GONE
                            binding.btnLogin.isEnabled = true

                            val token = state.data.data?.token ?: ""

                            val name = state.data.data?.profile?.full_name
                                ?: state.data.data?.user?.name
                                ?: ""

                            val avatarUrl = state.data.data?.profile?.avatar_url ?: ""
                            val nrp = state.data.data?.user?.username ?: ""

                            val sessionManager = SessionManager(this@LoginActivity)
                            sessionManager.saveSession(token, name)
                            sessionManager.saveAvatar(avatarUrl)
                            sessionManager.saveNrp(nrp)

                            Log.d("LOGIN_AVATAR", "avatarUrl = $avatarUrl")
                            Log.d("LOGIN_NAME", "name = $name")

                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                        is Result.Error -> {
                            binding.layoutConnecting.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            viewModel.resetState()
                        }
                        null -> {
                            binding.layoutConnecting.visibility = View.GONE
                            binding.btnLogin.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun handleSmallScreen() {
        val displayMetrics = resources.displayMetrics
        val widthInch = displayMetrics.widthPixels / displayMetrics.xdpi
        val heightInch = displayMetrics.heightPixels / displayMetrics.ydpi
        val screenInch = Math.sqrt((widthInch * widthInch + heightInch * heightInch).toDouble())
        if (screenInch <= 5.0) {
            binding.txtTagline.visibility = View.GONE
        }
        if (screenInch <= 4.0) {
            binding.imgPlanet.visibility = View.GONE
        }
    }

    private fun startPlanetAnimation() {
        if (binding.imgPlanet.visibility == View.VISIBLE) {
            binding.imgPlanet.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.rotate_planet)
            )
        }
    }

    private fun setupKeyboardScroll() {
        binding.etUsername.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.scrollView.postDelayed({
                val location = IntArray(2)
                binding.tilUsername.getLocationOnScreen(location)
                binding.scrollView.smoothScrollTo(
                    0, binding.scrollView.scrollY + location[1] - 300
                )
            }, 300)
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.scrollView.postDelayed({
                val location = IntArray(2)
                binding.tilPassword.getLocationOnScreen(location)
                binding.scrollView.smoothScrollTo(
                    0, binding.scrollView.scrollY + location[1] - 300
                )
            }, 300)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.imgPlanet.clearAnimation()
    }

    override fun onResume() {
        super.onResume()
        startPlanetAnimation()
    }
}