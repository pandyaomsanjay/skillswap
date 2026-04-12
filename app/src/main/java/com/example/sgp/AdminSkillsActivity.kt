package com.example.sgp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*

class AdminSkillsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SkillAdapter
    private val skillList = mutableListOf<Skill>()
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_skills)

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        database = FirebaseDatabase.getInstance().reference.child("Skills")

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SkillAdapter(skillList) { skill ->
            showSkillOptionsDialog(skill)
        }
        recyclerView.adapter = adapter

        loadSkills()
    }

    private fun loadSkills() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                skillList.clear()
                for (skillSnap in snapshot.children) {
                    skillSnap.getValue(Skill::class.java)?.let { skillList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@AdminSkillsActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showSkillOptionsDialog(skill: Skill) {
        val options = arrayOf("View Details", "Delete Skill")
        AlertDialog.Builder(this)
            .setTitle("Manage Skill: ${skill.title}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewSkillDetails(skill)
                    1 -> deleteSkill(skill)
                }
            }
            .show()
    }

    private fun viewSkillDetails(skill: Skill) {
        AlertDialog.Builder(this)
            .setTitle("Skill Details")
            .setMessage("""
                Title: ${skill.title}
                Description: ${skill.description}
                Category: ${skill.category}
                Posted by: ${skill.userName}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun deleteSkill(skill: Skill) {
        AlertDialog.Builder(this)
            .setTitle("Delete Skill")
            .setMessage("Are you sure you want to delete this skill?")
            .setPositiveButton("Delete") { _, _ ->
                database.child(skill.id).removeValue()
                    .addOnSuccessListener { Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show() }
                    .addOnFailureListener { Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class SkillAdapter(
        private val skills: List<Skill>,
        private val onItemClick: (Skill) -> Unit
    ) : RecyclerView.Adapter<SkillAdapter.ViewHolder>() {
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_skill, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val skill = skills[position]
            holder.tvTitle.text = skill.title
            holder.tvCategory.text = skill.category
            holder.tvUserName.text = "By: ${skill.userName}"
            holder.itemView.setOnClickListener { onItemClick(skill) }
        }

        override fun getItemCount() = skills.size
    }
}
