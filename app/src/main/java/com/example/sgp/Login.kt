package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.*

class Login : BaseActivity() {

    private lateinit var database: FirebaseDatabase
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        database = FirebaseDatabase.getInstance()
        progressBar = findViewById(R.id.progressBar)

        // Auto-login check
        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        val savedEmail = prefs.getString("user_email", "")

        if (!savedEmail.isNullOrEmpty()) {
            startActivity(Intent(this, Home::class.java))
            finish()
            return
        }

        initializeViews()
    }

    private fun initializeViews() {
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreateAccount = findViewById<Button>(R.id.tvCreateAccount)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        val emailInput = findViewById<TextInputLayout>(R.id.email)
        val passwordInput = findViewById<TextInputLayout>(R.id.password)

        btnLogin.setOnClickListener {
            val email = emailInput.editText?.text.toString().trim()
            val password = passwordInput.editText?.text.toString()

            if (validateForm(email, password)) {
                loginUser(email, password)
            }
        }

        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, Createaccount::class.java))
        }

        tvForgotPassword.setOnClickListener {
            Snackbar.make(
                findViewById(android.R.id.content),
                "Password reset not supported yet",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun validateForm(email: String, password: String): Boolean {
        var isValid = true

        val emailLayout = findViewById<TextInputLayout>(R.id.email)
        val passwordLayout = findViewById<TextInputLayout>(R.id.password)

        emailLayout.error = null
        passwordLayout.error = null

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Enter a valid email"
            isValid = false
        }

        if (password.isEmpty()) {
            passwordLayout.error = "Password required"
            isValid = false
        }

        return isValid
    }

    private fun loginUser(email: String, password: String) {
        showLoading(true)

        val userId = email.replace(".", "_")
        val userRef = database.getReference("Users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                showLoading(false)

                if (!snapshot.exists()) {
                    showError("Account not found")
                    return
                }

                val user = snapshot.getValue(Users::class.java)

                if (user == null || user.password != password) {
                    showError("Invalid email or password")
                    return
                }

                // Save session
                val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                prefs.edit()
                    .putString("user_name", user.name)
                    .putString("user_email", user.email)
                    .putString("user_location", user.location)
                    .putString("user_type", user.userType)
                    .apply()

                Toast.makeText(this@Login, "Login successful", Toast.LENGTH_SHORT).show()

                // Redirect based on user type
                val intent = if (user.userType == "admin") {
                    Intent(this@Login, AdminDashboardActivity::class.java)
                } else {
                    Intent(this@Login, Home::class.java)
                }

                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

            override fun onCancelled(error: DatabaseError) {
                showLoading(false)
                showError("Database error: ${error.message}")
            }
        })
    }

    private fun showError(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnLogin).isEnabled = !show
    }
}
