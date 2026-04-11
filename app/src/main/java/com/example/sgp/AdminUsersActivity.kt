package com.example.sgp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*

class AdminUsersActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserAdapter
    private val userList = mutableListOf<User>()
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_users)

        // Check admin
        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("Users")

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter(userList) { user ->
            showUserOptionsDialog(user)
        }
        recyclerView.adapter = adapter

        loadUsers()

        findViewById<FloatingActionButton>(R.id.fabAddUser).setOnClickListener {
            // Option to add a new user manually (for admin)
            startActivity(Intent(this, Createaccount::class.java))
        }
    }

    private fun loadUsers() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userList.clear()
                for (userSnap in snapshot.children) {
                    val user = userSnap.getValue(User::class.java)
                    if (user != null) {
                        userList.add(user)
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminUsersActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showUserOptionsDialog(user: User) {
        val options = arrayOf("View Details", "Promote to Admin", "Demote to User", "Delete User")
        AlertDialog.Builder(this)
            .setTitle("Manage User: ${user.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewUserDetails(user)
                    1 -> updateUserType(user, "admin")
                    2 -> updateUserType(user, "standard")
                    3 -> deleteUser(user)
                }
            }
            .show()
    }

    private fun viewUserDetails(user: User) {
        // Could open a detail activity or show dialog
        val message = """
            Name: ${user.name}
            Email: ${user.email}
            Phone: ${user.phone}
            Location: ${user.location}
            Type: ${user.userType}
            Trades: ${user.completedTrades}
            Rating: ${user.rating}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("User Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateUserType(user: User, newType: String) {
        val userId = user.email.replace(".", "_")
        database.child(userId).child("userType").setValue(newType)
            .addOnSuccessListener {
                Toast.makeText(this, "User type updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteUser(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Delete User")
            .setMessage("Are you sure you want to delete ${user.name}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val userId = user.email.replace(".", "_")
                database.child(userId).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "User deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Adapter inner class
    class UserAdapter(
        private val users: List<User>,
        private val onItemClick: (User) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvName)
            val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
            val tvType: TextView = itemView.findViewById(R.id.tvType)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]
            holder.tvName.text = user.name
            holder.tvEmail.text = user.email
            holder.tvType.text = user.userType
            holder.itemView.setOnClickListener { onItemClick(user) }
        }

        override fun getItemCount() = users.size
    }
}
