package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import android.widget.Toast
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.util.Locale
import java.util.regex.Pattern
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import kotlinx.coroutines.launch

class Createaccount : BaseActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var placesClient: PlacesClient
    private val EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@(.+)\$"
    )

    private var selectedPlaceId: String? = null
    private lateinit var countries: List<Country>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_createaccount)

        initializePlaces()
        countries = loadCountries()
        setupCountryCodeDropdown()
        setupLocationAutocomplete()

        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val tvsignin = findViewById<TextView>(R.id.tvsignin)
        val termsCheckbox = findViewById<CheckBox>(R.id.terms_checkbox)

        database = FirebaseDatabase.getInstance().getReference("Users")

        btnSignUp.setOnClickListener {
            if (validateForm() && termsCheckbox.isChecked) {
                createUserAccount()
            } else if (!termsCheckbox.isChecked) {
                Toast.makeText(this, "Please accept Terms & Conditions", Toast.LENGTH_SHORT).show()
            }
        }

        tvsignin.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun initializePlaces() {
        try {
            val appInfo = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )
            val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")
            if (apiKey.isNullOrEmpty()) {
                Toast.makeText(this, "API Key not found in manifest", Toast.LENGTH_LONG).show()
                return
            }
            if (!Places.isInitialized()) {
                Places.initialize(applicationContext, apiKey)
            }
            placesClient = Places.createClient(this)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to initialize Places API", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCountries(): List<Country> {
        return try {
            val jsonString = assets.open("countries.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<Country>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val dialCode = obj.getString("dial_code")
                val flagEmoji = obj.getString("emoji")
                val alpha2 = obj.getString("code")
                list.add(Country(name, dialCode, flagEmoji, alpha2))
            }
            list.sortedBy { it.name }
        } catch (e: IOException) {
            e.printStackTrace()
            fallbackCountries()
        } catch (e: JSONException) {
            e.printStackTrace()
            fallbackCountries()
        }
    }

    private fun fallbackCountries(): List<Country> {
        return listOf(
            Country("United States", "+1", "🇺🇸", "US"),
            Country("Canada", "+1", "🇨🇦", "CA"),
            Country("United Kingdom", "+44", "🇬🇧", "GB"),
            Country("India", "+91", "🇮🇳", "IN"),
            Country("Australia", "+61", "🇦🇺", "AU"),
            Country("Germany", "+49", "🇩🇪", "DE"),
            Country("France", "+33", "🇫🇷", "FR"),
            Country("Japan", "+81", "🇯🇵", "JP"),
            Country("China", "+86", "🇨🇳", "CN"),
            Country("Russia", "+7", "🇷🇺", "RU"),
            Country("Brazil", "+55", "🇧🇷", "BR"),
            Country("South Africa", "+27", "🇿🇦", "ZA"),
            Country("Mexico", "+52", "🇲🇽", "MX")
        ).sortedBy { it.name }
    }

    private fun setupCountryCodeDropdown() {
        val adapter = CountryArrayAdapter(this, countries)
        val actvCountryCode = findViewById<MaterialAutoCompleteTextView>(R.id.actvCountryCode)
        actvCountryCode.setAdapter(adapter)

        val defaultCountry = countries.find { it.alpha2Code == "IN" } ?: countries.firstOrNull()
        defaultCountry?.let {
            actvCountryCode.setText("${it.dialCode} ${it.flagEmoji}", false)
            actvCountryCode.tag = it.dialCode
        }

        actvCountryCode.setOnItemClickListener { _, _, position, _ ->
            val selected = countries[position]
            actvCountryCode.tag = selected.dialCode
        }
    }

    private fun setupLocationAutocomplete() {
        val actvLocation = findViewById<AutoCompleteTextView>(R.id.actvLocation)
        actvLocation.threshold = 2

        actvLocation.setOnItemClickListener { parent, _, position, _ ->
            val prediction = parent.getItemAtPosition(position) as AutocompletePrediction
            selectedPlaceId = prediction.placeId
            fetchPlaceDetails(prediction.placeId) { place ->
                runOnUiThread {
                    actvLocation.setText(place?.address ?: prediction.getFullText(null).toString())
                }
            }
        }

        actvLocation.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                selectedPlaceId = null
            }
        })

        val adapter = PlaceAutocompleteAdapter(this, placesClient)
        actvLocation.setAdapter(adapter)
    }

    private fun fetchPlaceDetails(placeId: String, callback: (Place?) -> Unit) {
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS)
        val request = FetchPlaceRequest.builder(placeId, placeFields).build()
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response -> callback(response.place) }
            .addOnFailureListener { callback(null) }
    }

    private fun validateForm(): Boolean {
        val nameInput = findViewById<TextInputLayout>(R.id.name)
        val emailInput = findViewById<TextInputLayout>(R.id.email)
        val countryCodeInput = findViewById<TextInputLayout>(R.id.countryCode)
        val phoneInput = findViewById<TextInputLayout>(R.id.phone)
        val locationInput = findViewById<TextInputLayout>(R.id.tilLocation)
        val passwordInput = findViewById<TextInputLayout>(R.id.password)
        val confirmPasswordInput = findViewById<TextInputLayout>(R.id.confirmPassword)

        val name = capitalizeName(nameInput.editText?.text.toString().trim())
        val email = emailInput.editText?.text.toString().trim()
        val countryCodeFull = findViewById<MaterialAutoCompleteTextView>(R.id.actvCountryCode).text.toString().trim()
        val phoneNumber = phoneInput.editText?.text.toString().trim()
        val location = locationInput.editText?.text.toString().trim()
        val password = passwordInput.editText?.text.toString()
        val confirmPassword = confirmPasswordInput.editText?.text.toString()

        var isValid = true

        nameInput.error = null
        emailInput.error = null
        countryCodeInput.error = null
        phoneInput.error = null
        locationInput.error = null
        passwordInput.error = null
        confirmPasswordInput.error = null

        if (name.isEmpty()) {
            nameInput.error = "Full name is required"
            isValid = false
        }

        if (email.isEmpty()) {
            emailInput.error = "Email is required"
            isValid = false
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            emailInput.error = "Enter a valid email address"
            isValid = false
        }

        if (countryCodeFull.isEmpty()) {
            countryCodeInput.error = "Select a country code"
            isValid = false
        } else {
            val codePattern = Regex("""^(\+\d+)""")
            val match = codePattern.find(countryCodeFull)
            if (match == null) {
                countryCodeInput.error = "Invalid country code format"
                isValid = false
            }
        }

        if (phoneNumber.isEmpty()) {
            phoneInput.error = "Phone number is required"
            isValid = false
        } else if (!phoneNumber.matches(Regex("^[0-9]{10}\$"))) {
            phoneInput.error = "Enter valid 10-digit number"
            isValid = false
        }

        if (location.isEmpty()) {
            locationInput.error = "Location is required"
            isValid = false
        } else if (selectedPlaceId == null) {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(location, 1)
                if (addresses.isNullOrEmpty()) {
                    locationInput.error = "Location not recognized – please select from list or enter a valid place"
                    isValid = false
                }
            } catch (e: Exception) {
                locationInput.error = "Unable to verify location"
                isValid = false
            }
        }

        if (password.isEmpty()) {
            passwordInput.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordInput.error = "Password must be at least 6 characters"
            isValid = false
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordInput.error = "Please confirm password"
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordInput.error = "Passwords do not match"
            isValid = false
        }

        return isValid
    }

    private fun createUserAccount() {
        val nameInput           = findViewById<TextInputLayout>(R.id.name)
        val emailInput          = findViewById<TextInputLayout>(R.id.email)
        val phoneInput          = findViewById<TextInputLayout>(R.id.phone)
        val locationInput       = findViewById<TextInputLayout>(R.id.tilLocation)
        val passwordInput       = findViewById<TextInputLayout>(R.id.password)
        val countryCodeInput    = findViewById<MaterialAutoCompleteTextView>(R.id.actvCountryCode)

        val countryCode = (countryCodeInput.tag as? String) ?: run {
            val codePattern = Regex("""^(\+\d+)""")
            codePattern.find(countryCodeInput.text.toString())?.groupValues?.get(1) ?: ""
        }

        val phoneNumber = phoneInput.editText?.text.toString().trim()
        val phone       = countryCode + phoneNumber
        val name        = capitalizeName(nameInput.editText?.text.toString().trim())
        val email       = emailInput.editText?.text.toString().trim()
        val location    = locationInput.editText?.text.toString().trim()
        val password    = passwordInput.editText?.text.toString()

        database.orderByChild("email").equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        emailInput.error = "Email already registered"
                        Toast.makeText(
                            this@Createaccount,
                            "Email already exists.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // ✅ Save to Firebase (NO SharedPreferences here)
                        saveUserToDatabase(name, email, phone, location, password)
                        // ✅ Send OTP then go to OtpActivity
                        sendSupabaseOtp(name, email, phone, location, password)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(
                        this@Createaccount,
                        "Database error: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun sendSupabaseOtp(
        name: String,
        email: String,
        phone: String,
        location: String,
        password: String
    ) {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signInWith(OTP) {
                    this.email = email
                    createUser = true
                }

                Toast.makeText(
                    this@Createaccount,
                    "OTP sent to $email",
                    Toast.LENGTH_LONG
                ).show()

                // ✅ Pass location too so OtpActivity can use it if needed
                val intent = Intent(this@Createaccount, OtpActivity::class.java).apply {
                    putExtra("email",    email)
                    putExtra("name",     name)
                    putExtra("phone",    phone)
                    putExtra("location", location)
                    putExtra("password", password)
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@Createaccount,
                    "Error sending OTP: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ✅ Only saves to Firebase — NO SharedPreferences, NO navigation
    private fun saveUserToDatabase(
        name: String,
        email: String,
        phone: String,
        location: String,
        password: String
    ) {
        val user = Users(
            name            = name,
            email           = email,
            phone           = phone,
            location        = location,
            password        = password,
            rating          = 0.0,
            completedTrades = 0,
            profileImage    = "",
            userType        = "standard",
            joinedDate      = System.currentTimeMillis()
        )

        val userId = email.replace(".", "_")

        database.child(userId).setValue(user)
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Failed to save user data: ${it.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        // ✅ SharedPreferences removed — written in OtpActivity after verification
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES & ADAPTERS (unchanged)
// ══════════════════════════════════════════════════════════════════════════════

data class Country(
    val name: String,
    val dialCode: String,
    val flagEmoji: String,
    val alpha2Code: String
) {
    override fun toString(): String = "$dialCode $flagEmoji"
}

class CountryArrayAdapter(
    context: Context,
    private val countries: List<Country>
) : ArrayAdapter<Country>(context, 0, countries) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        createView(position, convertView, parent)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        createView(position, convertView, parent)

    private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            android.R.layout.simple_dropdown_item_1line, parent, false
        )
        val textView = view as TextView
        val country = getItem(position)
        textView.text = "${country?.dialCode} ${country?.flagEmoji}"
        textView.setPadding(32, 16, 16, 16)
        return textView
    }
}

class PlaceAutocompleteAdapter(
    context: Context,
    private val placesClient: PlacesClient
) : ArrayAdapter<AutocompletePrediction>(
    context,
    android.R.layout.simple_dropdown_item_1line
), Filterable {

    private var predictions: List<AutocompletePrediction> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun getCount(): Int = predictions.size
    override fun getItem(position: Int): AutocompletePrediction? = predictions[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val text = getItem(position)?.getFullText(null).toString()
        (view as TextView).text = text
        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults =
                FilterResults()

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                runnable?.let { handler.removeCallbacks(it) }

                if (constraint == null || constraint.length < 2) {
                    predictions = emptyList()
                    clear()
                    notifyDataSetInvalidated()
                    return
                }

                runnable = Runnable {
                    val request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(constraint.toString())
                        .build()

                    placesClient.findAutocompletePredictions(request)
                        .addOnSuccessListener { response ->
                            predictions = response.autocompletePredictions
                            clear()
                            addAll(predictions)
                            notifyDataSetChanged()
                        }
                        .addOnFailureListener {
                            predictions = emptyList()
                            notifyDataSetInvalidated()
                        }
                }

                handler.postDelayed(runnable!!, 400)
            }
        }
    }
}
