package com.example.sgp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Profile : BaseActivity() {

    private lateinit var database: FirebaseDatabase
    private lateinit var storageReference: StorageReference
    private lateinit var currentUserEmail: String
    private lateinit var currentUserId: String

    // Views
    private lateinit var profileImageView: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvMemberSince: TextView
    private lateinit var tvExchanges: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvCredits: TextView
    private lateinit var skillsContainer: LinearLayout
    private lateinit var achievementsGrid: GridLayout
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        database = FirebaseDatabase.getInstance()
        storageReference = FirebaseStorage.getInstance().reference

        initViews()
        setupToolbar()
        setupBottomNavigation()

        getUserFromPrefs()
        loadUserProfile()
    }

    private fun initViews() {
        profileImageView = findViewById(R.id.profileImage)
        tvName = findViewById(R.id.tvName)
        tvMemberSince = findViewById(R.id.tvMemberSince)
        tvExchanges = findViewById(R.id.tvExchanges)
        tvRating = findViewById(R.id.tvRating)
        tvCredits = findViewById(R.id.tvCredits)
        skillsContainer = findViewById(R.id.skillsContainer)
        achievementsGrid = findViewById(R.id.achievementsGrid)
        bottomNav = findViewById(R.id.bottomNavigation)

        findViewById<ImageButton>(R.id.btnChangePhoto).setOnClickListener {
            showImagePickerDialog()
        }

        findViewById<MaterialButton>(R.id.btnEditProfile).setOnClickListener {
            if (currentUserEmail.isNotEmpty()) {
                val intent = Intent(this, EditProfileActivity::class.java).apply {
                    putExtra("email", currentUserEmail)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "User email missing", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, Login::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }

        findViewById<MaterialButton>(R.id.btnAddNewSkill).setOnClickListener {
            startActivity(Intent(this, AddSkillActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.layoutBookingHistory).setOnClickListener {
            Toast.makeText(this, "Booking History", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.layoutReviews).setOnClickListener {
            Toast.makeText(this, "Reviews & Ratings", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.layoutSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.layoutLogout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.profileToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.my_profile)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.selectedItemId = R.id.nav_profile

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, Home::class.java))
                    finish()
                    true
                }
                R.id.nav_explore -> {
                    startActivity(Intent(this, ExploreActivity::class.java))
                    true
                }
                R.id.nav_add_skill -> {
                    startActivity(Intent(this, AddSkillActivity::class.java))
                    true
                }
                R.id.nav_trades -> {
                    startActivity(Intent(this, MyTradesActivity::class.java))
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }

    private fun getUserFromPrefs() {
        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        currentUserEmail = prefs.getString("user_email", "") ?: ""
        currentUserId = currentUserEmail.replace(".", "_")

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadUserProfile() {
        if (currentUserEmail.isEmpty()) return

        database.getReference("Users").child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(Users::class.java)
                    user?.let { updateUI(it) }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@Profile, error.message, Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun updateUI(user: Users) {
        tvName.text = capitalizeName(user.name)
        tvExchanges.text = user.completedTrades.toString()
        tvRating.text = String.format("%.1f", user.rating)
        tvCredits.text = user.credits?.toString() ?: "0"

        // Format member since date
        val date = Date(user.joinedDate)
        val format = SimpleDateFormat("yyyy", Locale.getDefault())
        tvMemberSince.text = "Member since ${format.format(date)}"

        // Profile image
        if (!user.profileImage.isNullOrEmpty()) {
            Glide.with(this).load(user.profileImage).into(profileImageView)
        }

        // Load skills (teach skills)
        loadUserSkills()
    }

    private fun loadUserSkills() {
        database.getReference("Skills")
            .orderByChild("userId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    skillsContainer.removeAllViews()
                    for (skillSnap in snapshot.children) {
                        val skill = skillSnap.getValue(Skill::class.java)
                        skill?.let { addSkillView(it) }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@Profile, error.message, Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun addSkillView(skill: Skill) {
        val view = layoutInflater.inflate(R.layout.item_profile_skill, skillsContainer, false)
        val tvSkill = view.findViewById<TextView>(R.id.tvSkill)
        tvSkill.text = skill.title
        skillsContainer.addView(view)
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Upload from Gallery", "Take Photo")
        AlertDialog.Builder(this)
            .setTitle("Change Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> galleryLauncher.launch("image/*")
                    1 -> cameraLauncher.launch(null)
                }
            }
            .show()
    }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                profileImageView.setImageURI(it)
                uploadImageToFirebase(it)
            }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                val path = MediaStore.Images.Media.insertImage(
                    contentResolver,
                    bitmap,
                    "ProfileImage",
                    null
                )
                val uri = Uri.parse(path)
                profileImageView.setImageURI(uri)
                uploadImageToFirebase(uri)
            }
        }

    private fun uploadImageToFirebase(uri: Uri) {
        val fileRef = storageReference.child("profile_images/$currentUserId.jpg")
        fileRef.putFile(uri)
            .addOnSuccessListener {
                fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    database.getReference("Users")
                        .child(currentUserId)
                        .child("profileImage")
                        .setValue(downloadUri.toString())
                    Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ -> logoutUser() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logoutUser() {
        getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }
}
