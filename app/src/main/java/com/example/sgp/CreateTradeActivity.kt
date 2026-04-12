package com.example.sgp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.*

class CreateTradeActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var currentUserEmail: String
    private lateinit var currentUserId: String
    private lateinit var currentUserName: String

    // UI Elements
    private lateinit var etYourSkill: TextInputEditText
    private lateinit var etPartnerEmail: TextInputEditText
    private lateinit var etPartnerSkill: TextInputEditText
    private lateinit var etMessage: TextInputEditText
    private lateinit var btnPropose: MaterialButton

    // Selected skill objects (to get videoUrl)
    private var selectedYourSkill: Skill? = null
    private var selectedPartnerSkill: Skill? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_trade)

        database = FirebaseDatabase.getInstance().reference

        // Get current user from SharedPreferences
        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        currentUserEmail = prefs.getString("user_email", "") ?: ""
        currentUserId = currentUserEmail.replace(".", "_")
        currentUserName = prefs.getString("user_name", "") ?: ""

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupToolbar()
        loadYourSkills()
    }

    private fun initViews() {
        etYourSkill = findViewById(R.id.etYourSkill)
        etPartnerEmail = findViewById(R.id.etPartnerEmail)
        etPartnerSkill = findViewById(R.id.etPartnerSkill)
        etMessage = findViewById(R.id.etMessage)
        btnPropose = findViewById(R.id.btnPropose)

        // Your Skill dropdown
        etYourSkill.setOnClickListener {
            showSkillPicker("your")
        }

        // Partner Email validation and skill loading
        etPartnerEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && etPartnerEmail.text?.isNotEmpty() == true) {
                loadPartnerSkills(etPartnerEmail.text.toString())
            }
        }

        etPartnerSkill.setOnClickListener {
            val partnerEmail = etPartnerEmail.text.toString().trim()
            if (partnerEmail.isEmpty()) {
                Toast.makeText(this, "Enter partner's email first", Toast.LENGTH_SHORT).show()
            } else {
                showSkillPicker("partner", partnerEmail)
            }
        }

        btnPropose.setOnClickListener {
            proposeTrade()
        }
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadYourSkills() {
        // Load skills owned by current user
        database.child("Skills").orderByChild("userId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val skills = mutableListOf<Skill>()
                    for (skillSnap in snapshot.children) {
                        skillSnap.getValue(Skill::class.java)?.let { skills.add(it) }
                    }
                    if (skills.isEmpty()) {
                        etYourSkill.setText("No skills found. Please add a skill first.")
                        etYourSkill.isEnabled = false
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun showSkillPicker(type: String, partnerEmail: String = "") {
        val query = if (type == "your") {
            database.child("Skills").orderByChild("userId").equalTo(currentUserId)
        } else {
            val partnerId = partnerEmail.replace(".", "_")
            database.child("Skills").orderByChild("userId").equalTo(partnerId)
        }

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val skills = mutableListOf<Skill>()
                for (skillSnap in snapshot.children) {
                    skillSnap.getValue(Skill::class.java)?.let { skills.add(it) }
                }
                if (skills.isEmpty()) {
                    Toast.makeText(this@CreateTradeActivity, "No skills available", Toast.LENGTH_SHORT).show()
                    return
                }
                val skillTitles = skills.map { it.title }.toTypedArray()
                AlertDialog.Builder(this@CreateTradeActivity)
                    .setTitle("Select Skill")
                    .setItems(skillTitles) { _, which ->
                        val selected = skills[which]
                        if (type == "your") {
                            selectedYourSkill = selected
                            etYourSkill.setText(selected.title)
                        } else {
                            selectedPartnerSkill = selected
                            etPartnerSkill.setText(selected.title)
                        }
                    }
                    .show()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadPartnerSkills(email: String) {
        val partnerId = email.replace(".", "_")
        database.child("Skills").orderByChild("userId").equalTo(partnerId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = snapshot.childrenCount
                    if (count == 0L) {
                        Toast.makeText(this@CreateTradeActivity, "Partner has no skills", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun proposeTrade() {
        val yourSkillObj = selectedYourSkill
        val partnerSkillObj = selectedPartnerSkill
        val partnerEmail = etPartnerEmail.text.toString().trim()
        val message = etMessage.text.toString().trim()

        if (yourSkillObj == null) {
            Toast.makeText(this, "Select your skill", Toast.LENGTH_SHORT).show()
            return
        }
        if (partnerEmail.isEmpty()) {
            Toast.makeText(this, "Enter partner's email", Toast.LENGTH_SHORT).show()
            return
        }
        if (partnerSkillObj == null) {
            Toast.makeText(this, "Select partner's skill", Toast.LENGTH_SHORT).show()
            return
        }

        // Get partner's user ID and name
        val partnerId = partnerEmail.replace(".", "_")
        database.child("Users").child(partnerId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val partner = snapshot.getValue(Users::class.java)
                if (partner == null) {
                    Toast.makeText(this@CreateTradeActivity, "Partner not registered", Toast.LENGTH_SHORT).show()
                    return
                }

                val tradeId = database.child("Trades").push().key ?: return
                val trade = Trade(
                    id = tradeId,
                    requesterId = currentUserId,
                    receiverId = partnerId,
                    requesterSkill = yourSkillObj.title,
                    receiverSkill = partnerSkillObj.title,
                    status = "pending",
                    requesterName = currentUserName,
                    receiverName = partner.name,
                    timestamp = System.currentTimeMillis(),
                    videoUrl = yourSkillObj.videoUrl ?: "",  // ✅ store video URL
                    isActive = true,
                    uploaderName = currentUserName,
                    skillOffered = yourSkillObj.title,
                    skillRequested = partnerSkillObj.title,
                    rating = 0f,
                    uploaderAvatar = R.drawable.ic_default_profile
                )

                database.child("Trades").child(tradeId).setValue(trade)
                    .addOnSuccessListener {
                        Toast.makeText(this@CreateTradeActivity, "Trade proposed!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@CreateTradeActivity, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
