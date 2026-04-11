package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.OTP
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OtpActivity : BaseActivity() {

    private var email: String = ""
    private var name: String = ""
    private var phone: String = ""
    private var password: String = ""
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        email = intent.getStringExtra("email") ?: ""
        name = intent.getStringExtra("name") ?: ""
        phone = intent.getStringExtra("phone") ?: ""
        password = intent.getStringExtra("password") ?: ""

        progressBar = findViewById(R.id.progressBar)

        findViewById<TextView>(R.id.tvEmailInfo).text = "OTP sent to $email"

        findViewById<Button>(R.id.btnVerify).setOnClickListener {
            val otpLayout = findViewById<TextInputLayout>(R.id.otpInput)
            val otp = otpLayout.editText?.text.toString().trim()

            if (otp.length != 6) {
                otpLayout.error = "Enter the 6-digit OTP"
                return@setOnClickListener
            }
            otpLayout.error = null
            verifyOtp(otp)
        }

        findViewById<TextView>(R.id.tvResend).setOnClickListener {
            resendOtp()
        }
    }

    private fun verifyOtp(otp: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.verifyEmailOtp(
                    type = OtpType.Email.SIGNUP,
                    email = email,
                    token = otp
                )

                // Set password + save name and phone in metadata
                if (password.isNotEmpty()) {
                    SupabaseClient.client.auth.updateUser {
                        this.password = password
                        data = buildJsonObject {
                            put("name", name)
                            put("phone", phone)
                        }
                    }
                }

                showLoading(false)
                Toast.makeText(this@OtpActivity, "Account Created! 🎉", Toast.LENGTH_SHORT).show()

                // Save to SharedPreferences same as existing app
                val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                with(prefs.edit()) {
                    putString("user_name", name)
                    putString("user_email", email)
                    putInt("user_points", 1250)
                    putInt("user_total_trades", 0)
                    putFloat("user_rating", 0f)
                    putInt("user_total_skills", 0)
                    apply()
                }

                val intent = Intent(this@OtpActivity, Home::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)

            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@OtpActivity, "Invalid or expired OTP. Try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resendOtp() {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signInWith(OTP) {
                    this.email = this@OtpActivity.email
                    createUser = true
                }
                Toast.makeText(this@OtpActivity, "OTP resent to $email", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@OtpActivity, "Failed to resend OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnVerify).isEnabled = !show
    }
}
