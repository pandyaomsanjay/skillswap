package com.example.sgp

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.OTP
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OtpActivity : BaseActivity() {

    private var email    = ""
    private var name     = ""
    private var phone    = ""
    private var password = ""

    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvTimerLabel: TextView
    private lateinit var timerPill: MaterialCardView   // ✅ Fixed: was LinearLayout
    private lateinit var cardMain: MaterialCardView    // ✅ Fixed: was missing
    private lateinit var otpBoxes: List<EditText>
    private lateinit var otpCards: List<MaterialCardView>  // ✅ Added: direct card refs
    private lateinit var countDownTimer: CountDownTimer

    // ── Palette ───────────────────────────────────────────────────────────────
    private val C_CARD               = Color.parseColor("#1E293B")
    private val C_CARD_BORDER        = Color.parseColor("#C27803")
    private val C_BOX_FILL           = Color.parseColor("#131F2E")
    private val C_BOX_EMPTY_BORDER   = Color.parseColor("#1E2240")
    private val C_BOX_ACTIVE_BORDER  = Color.parseColor("#FBBF24")
    private val C_BOX_FILLED_BORDER  = Color.parseColor("#C27803")
    private val C_AMBER              = Color.parseColor("#FBBF24")
    private val C_GOLDEN             = Color.parseColor("#C27803")
    private val C_TIMER_GREEN_BG     = Color.parseColor("#0D2218")
    private val C_TIMER_GREEN_BORDER = Color.parseColor("#166534")
    private val C_GREEN              = Color.parseColor("#4ADE80")
    private val C_GREEN_LIGHT        = Color.parseColor("#86EFAC")
    private val C_TIMER_AMBER_BG     = Color.parseColor("#1C1500")
    private val C_TIMER_AMBER_BORDER = Color.parseColor("#C27803")
    private val C_AMBER_LABEL        = Color.parseColor("#FDE68A")
    private val C_TIMER_RED_BG       = Color.parseColor("#2A0D0D")
    private val C_TIMER_RED_BORDER   = Color.parseColor("#7F1D1D")
    private val C_RED                = Color.parseColor("#F87171")
    private val C_RED_LABEL          = Color.parseColor("#FCA5A5")

    companion object {
        const val TIMER_GREEN = 0
        const val TIMER_AMBER = 1
        const val TIMER_RED   = 2
    }

    private var currentTimerState = TIMER_GREEN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        email    = intent.getStringExtra("email")    ?: ""
        name     = intent.getStringExtra("name")     ?: ""
        phone    = intent.getStringExtra("phone")    ?: ""
        password = intent.getStringExtra("password") ?: ""

        // ── Bind views ──────────────────────────────────────────────────────
        progressBar  = findViewById(R.id.progressBar)
        tvError      = findViewById(R.id.tvError)
        tvTimer      = findViewById(R.id.tvTimer)
        tvTimerLabel = findViewById(R.id.tvTimerLabel)
        timerPill    = findViewById(R.id.timerPill)   // MaterialCardView ✅
        cardMain     = findViewById(R.id.cardMain)    // MaterialCardView ✅

        // ── OTP cards (for border color changes) ────────────────────────────
        otpCards = listOf(
            findViewById(R.id.cardOtp1),
            findViewById(R.id.cardOtp2),
            findViewById(R.id.cardOtp3),
            findViewById(R.id.cardOtp4),
            findViewById(R.id.cardOtp5),
            findViewById(R.id.cardOtp6)
        )

        // ── OTP edit texts ───────────────────────────────────────────────────
        otpBoxes = listOf(
            findViewById(R.id.otp1), findViewById(R.id.otp2), findViewById(R.id.otp3),
            findViewById(R.id.otp4), findViewById(R.id.otp5), findViewById(R.id.otp6)
        )

        // ── Apply card main style programmatically ───────────────────────────
        // ✅ Use MaterialCardView API instead of custom background drawable
        cardMain.setCardBackgroundColor(C_CARD)
        cardMain.strokeColor = C_CARD_BORDER
        cardMain.strokeWidth = dp(1f).toInt()

        // ── Highlight email in subtitle ──────────────────────────────────────
        val emailText = "OTP sent to $email"
        findViewById<TextView>(R.id.tvEmailInfo).apply {
            val spannable = android.text.SpannableString(emailText)
            val start = emailText.indexOf(email)
            if (start >= 0) {
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(C_AMBER),
                    start, start + email.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start, start + email.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            setText(spannable)
        }

        // ── Timer pill initial color ─────────────────────────────────────────
        applyTimerPillColor(TIMER_GREEN)

        // ── Initial OTP box states ───────────────────────────────────────────
        otpBoxes.forEachIndexed { i, box ->
            setBoxStyle(i, active = false, filled = false)
        }
        setBoxStyle(0, active = true, filled = false)

        setupOtpBoxes()
        startTimer()

        // ── Verify button ────────────────────────────────────────────────────
        findViewById<MaterialButton>(R.id.btnVerify).setOnClickListener {
            val otp = otpBoxes.joinToString("") { it.text.toString().trim() }
            if (otp.length != 6) {
                showError("Please enter the complete 6-digit code")
                shakeBoxes()
                return@setOnClickListener
            }
            hideError()
            verifyOtp(otp)
        }

        findViewById<TextView>(R.id.tvResend).setOnClickListener { resendOtp() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TIMER
    // ══════════════════════════════════════════════════════════════════════════

    private fun startTimer(totalMs: Long = 600_000L) {
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
        currentTimerState = TIMER_GREEN
        applyTimerPillColor(TIMER_GREEN)

        countDownTimer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(msLeft: Long) {
                val m = msLeft / 60000
                val s = (msLeft % 60000) / 1000
                tvTimer.text = String.format("%02d:%02d", m, s)

                when {
                    msLeft <= 30_000L && currentTimerState != TIMER_RED -> {
                        currentTimerState = TIMER_RED
                        applyTimerPillColor(TIMER_RED)
                        pulseTimer()
                    }
                    msLeft <= 60_000L && currentTimerState == TIMER_GREEN -> {
                        currentTimerState = TIMER_AMBER
                        applyTimerPillColor(TIMER_AMBER)
                    }
                }
            }

            override fun onFinish() {
                tvTimer.text = "00:00"
                applyTimerPillColor(TIMER_RED)
                showError("OTP expired. Tap Resend to get a new code.")
                findViewById<MaterialButton>(R.id.btnVerify).isEnabled = false
            }
        }.start()
    }

    // ✅ Fixed: uses MaterialCardView API instead of custom GradientDrawable
    private fun applyTimerPillColor(state: Int) {
        when (state) {
            TIMER_GREEN -> {
                timerPill.setCardBackgroundColor(C_TIMER_GREEN_BG)
                timerPill.strokeColor = C_TIMER_GREEN_BORDER
                timerPill.strokeWidth = dp(1f).toInt()
                tvTimer.setTextColor(C_GREEN)
                tvTimerLabel.setTextColor(C_GREEN_LIGHT)
            }
            TIMER_AMBER -> {
                timerPill.setCardBackgroundColor(C_TIMER_AMBER_BG)
                timerPill.strokeColor = C_TIMER_AMBER_BORDER
                timerPill.strokeWidth = dp(1f).toInt()
                tvTimer.setTextColor(C_AMBER)
                tvTimerLabel.setTextColor(C_AMBER_LABEL)
            }
            TIMER_RED -> {
                timerPill.setCardBackgroundColor(C_TIMER_RED_BG)
                timerPill.strokeColor = C_TIMER_RED_BORDER
                timerPill.strokeWidth = dp(1f).toInt()
                tvTimer.setTextColor(C_RED)
                tvTimerLabel.setTextColor(C_RED_LABEL)
            }
        }
    }

    private fun pulseTimer() {
        ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                tvTimer.scaleX = it.animatedValue as Float
                tvTimer.scaleY = it.animatedValue as Float
            }
        }.start()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OTP BOX LOGIC
    // ══════════════════════════════════════════════════════════════════════════

    private fun setupOtpBoxes() {
        otpBoxes.forEachIndexed { i, box ->
            box.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val filled = s?.length == 1
                    val isLast = i == otpBoxes.lastIndex
                    setBoxStyle(i, active = !filled || isLast, filled = filled)
                    if (filled && !isLast) {
                        otpBoxes[i + 1].requestFocus()
                        setBoxStyle(i + 1, active = true, filled = false)
                    }
                }
            })

            box.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL
                    && event.action == KeyEvent.ACTION_DOWN
                    && box.text.isEmpty() && i > 0
                ) {
                    otpBoxes[i - 1].apply {
                        setText("")
                        requestFocus()
                        setBoxStyle(i - 1, active = true, filled = false)
                    }
                    setBoxStyle(i, active = false, filled = false)
                    true
                } else false
            }

            box.setOnFocusChangeListener { _, hasFocus ->
                setBoxStyle(i, active = hasFocus, filled = box.text.isNotEmpty())
            }
        }
        otpBoxes[0].requestFocus()
    }

    // ✅ Fixed: uses MaterialCardView strokeColor instead of custom drawable on EditText
    private fun setBoxStyle(index: Int, active: Boolean, filled: Boolean) {
        val card = otpCards[index]
        val strokeColor = when {
            active && filled -> C_BOX_FILLED_BORDER
            active           -> C_BOX_ACTIVE_BORDER
            filled           -> C_BOX_FILLED_BORDER
            else             -> C_BOX_EMPTY_BORDER
        }
        card.setCardBackgroundColor(C_BOX_FILL)
        card.strokeColor = strokeColor
        card.strokeWidth = dp(2f).toInt()
    }

    private fun shakeBoxes() {
        TranslateAnimation(0f, 16f, 0f, 0f).apply {
            duration     = 500
            interpolator = CycleInterpolator(7f)
        }.also { findViewById<View>(R.id.otpBoxesContainer).startAnimation(it) }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ERROR HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvError.visibility = View.GONE
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ══════════════════════════════════════════════════════════════════════════
    // SUPABASE
    // ══════════════════════════════════════════════════════════════════════════

    private fun verifyOtp(otp: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.verifyEmailOtp(
                    type  = OtpType.Email.SIGNUP,
                    email = email,
                    token = otp
                )

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

                // ✅ Save SharedPreferences ONLY after successful OTP verification
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

                // ✅ Navigate to Home only after OTP verified
                startActivity(Intent(this@OtpActivity, Home::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })

            } catch (e: Exception) {
                showLoading(false)
                showError("Invalid or expired OTP. Please try again.")
                shakeBoxes()
                otpBoxes.forEach { it.setText("") }
                otpBoxes[0].requestFocus()
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
                otpBoxes.forEachIndexed { i, box ->
                    box.setText("")
                    setBoxStyle(i, active = false, filled = false)
                }
                otpBoxes[0].requestFocus()
                setBoxStyle(0, active = true, filled = false)
                hideError()
                startTimer()
                Toast.makeText(this@OtpActivity, "New OTP sent to $email ✓", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@OtpActivity, "Failed to resend OTP. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnVerify).isEnabled = !show
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
    }
}
