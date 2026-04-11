package com.example.sgp

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.*

class CreateTradeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_trade)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        val currentUserId = prefs.getString("user_email", "")?.replace(".", "_") ?: ""
        val currentUserName = prefs.getString("user_name", "") ?: ""

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val database = FirebaseDatabase.getInstance().reference
        val yourSkillInput = findViewById<TextInputLayout>(R.id.yourSkillLayout)
        val partnerEmailInput = findViewById<TextInputLayout>(R.id.partnerEmailLayout)
        val partnerSkillInput = findViewById<TextInputLayout>(R.id.partnerSkillLayout)
        val btnPropose = findViewById<Button>(R.id.btnPropose)

        btnPropose.setOnClickListener {
            val yourSkill = yourSkillInput.editText?.text.toString().trim()
            val partnerEmail = partnerEmailInput.editText?.text.toString().trim()
            val partnerSkill = partnerSkillInput.editText?.text.toString().trim()

            if (yourSkill.isEmpty() || partnerEmail.isEmpty() || partnerSkill.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val partnerId = partnerEmail.replace(".", "_")
            database.child("Users").child(partnerId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        Toast.makeText(this@CreateTradeActivity, "Partner not found", Toast.LENGTH_SHORT).show()
                        return
                    }
                    val partnerName = snapshot.child("name").getValue(String::class.java) ?: "Unknown"
                    val tradeId = database.child("Trades").push().key ?: return
                    val trade = Trade(
                        id = tradeId,
                        requesterId = currentUserId,
                        requesterName = currentUserName,
                        requesterSkill = yourSkill,
                        receiverId = partnerId,
                        receiverName = partnerName,
                        receiverSkill = partnerSkill,
                        status = "pending",
                        timestamp = System.currentTimeMillis()
                    )
                    database.child("Trades").child(tradeId).setValue(trade)
                        .addOnSuccessListener {
                            Toast.makeText(this@CreateTradeActivity, "Trade proposed", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { Toast.makeText(this@CreateTradeActivity, "Failed: ${it.message}", Toast.LENGTH_SHORT).show() }
                }
                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@CreateTradeActivity, error.message, Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
