package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.*
import com.example.sgp.Users

class EditProfileActivity : BaseActivity() {

    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        database = FirebaseDatabase.getInstance()

        // Try Intent extra first, then SharedPreferences
        var currentUserEmail = intent.getStringExtra("email")
        if (currentUserEmail.isNullOrEmpty()) {
            val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
            currentUserEmail = prefs.getString("user_email", "")
        }

        if (currentUserEmail.isNullOrEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews(currentUserEmail)
    }

    private fun initializeViews(email: String) {
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val nameInput = findViewById<TextInputLayout>(R.id.name)
        val phoneInput = findViewById<TextInputLayout>(R.id.phone)
        val locationInput = findViewById<TextInputLayout>(R.id.location)
        val skillsTeachInput = findViewById<TextInputLayout>(R.id.skillsTeach)
        val skillsLearnInput = findViewById<TextInputLayout>(R.id.skillsLearn)

        // Load current data
        loadCurrentData(email, nameInput, phoneInput, locationInput, skillsTeachInput, skillsLearnInput)

        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            // Safely get text from EditTexts
            val name = nameInput.editText?.text.toString()
            val phone = phoneInput.editText?.text.toString()
            val location = locationInput.editText?.text.toString()
            val skillsTeach = skillsTeachInput.editText?.text.toString()
            val skillsLearn = skillsLearnInput.editText?.text.toString()

            updateProfile(email, name, phone, location, skillsTeach, skillsLearn)
        }
    }

    private fun loadCurrentData(
        email: String,
        nameInput: TextInputLayout,
        phoneInput: TextInputLayout,
        locationInput: TextInputLayout,
        skillsTeachInput: TextInputLayout,
        skillsLearnInput: TextInputLayout
    ) {
        val userId = email.replace(".", "_")
        val userRef = database.getReference("Users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val user = snapshot.getValue(Users::class.java)
                    user?.let {
                        nameInput.editText?.setText(it.name)
                        phoneInput.editText?.setText(it.phone)
                        locationInput.editText?.setText(it.location)
                        skillsTeachInput.editText?.setText(it.skillsTeach)
                        skillsLearnInput.editText?.setText(it.skillsLearn)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@EditProfileActivity, "Failed to load data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateProfile(
        email: String,
        name: String,
        phone: String,
        location: String,
        skillsTeach: String,
        skillsLearn: String
    ) {
        if (name.isEmpty() || phone.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = email.replace(".", "_")
        val userRef = database.getReference("Users").child(userId)

        // Update only the fields we want to change
        val updates = hashMapOf<String, Any>(
            "name" to name,
            "phone" to phone,
            "location" to location,
            "skillsTeach" to skillsTeach,
            "skillsLearn" to skillsLearn
        )

        // Inside EditProfileActivity.kt - updateProfile() method

        userRef.updateChildren(updates)
            .addOnSuccessListener {
                // Update SharedPreferences with the new values
                val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                prefs.edit().apply {
                    putString("user_name", name)
                    putString("user_location", location)
                    // Optionally store phone if needed elsewhere
                    // putString("user_phone", phone)
                    apply()
                }

                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update profile: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}