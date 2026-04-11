package com.example.sgp

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val langName = prefs.getString(SettingsActivity.KEY_LANGUAGE, "English")
        val langCode = when (langName) {
            "Spanish" -> "es"
            "French"  -> "fr"
            "German"  -> "de"
            "Hindi"   -> "hi"
            else      -> "en"
        }
        Log.d("BaseActivity", "attachBaseContext: setting locale to $langCode (from $langName)")

        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)

        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun getResources(): Resources {
        val context = super.getResources().configuration?.let { config ->
            createConfigurationContext(config)
        } ?: this
        return context.resources
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply dark mode before super.onCreate
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)

        // Programmatically set status bar color to ensure consistency across all activities
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.primary_green)
        }

        Log.d("BaseActivity", "onCreate: current locale = ${resources.configuration.locales.get(0)}")
    }

    fun capitalizeName(name: String): String {
        return name.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}

