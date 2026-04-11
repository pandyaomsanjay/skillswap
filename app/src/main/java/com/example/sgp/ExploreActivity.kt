package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.database.*

class ExploreActivity : BaseActivity() {   // Changed from AppCompatActivity

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SkillAdapter
    private val allSkills = mutableListOf<Skill>()
    private val displayedSkills = mutableListOf<Skill>()
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)   // This will now call BaseActivity.onCreate
        setContentView(R.layout.activity_explore)

        // Toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SkillAdapter(displayedSkills) { skill ->
            Toast.makeText(this, skill.title, Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter

        // Search
        val etSearch = findViewById<TextInputEditText>(R.id.etSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSkills(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Firebase
        database = FirebaseDatabase.getInstance().reference.child("Skills")
        loadSkills()

        // Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_explore

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, Home::class.java))
                    finish()
                    true
                }
                R.id.nav_explore -> true
                R.id.nav_add_skill -> {
                    startActivity(Intent(this, AddSkillActivity::class.java))
                    true
                }
                R.id.nav_trades -> {
                    startActivity(Intent(this, MyTradesActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, Profile::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadSkills() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allSkills.clear()
                for (skillSnap in snapshot.children) {
                    skillSnap.getValue(Skill::class.java)?.let { allSkills.add(it) }
                }
                displayedSkills.clear()
                displayedSkills.addAll(allSkills)
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ExploreActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun filterSkills(query: String) {
        displayedSkills.clear()
        if (query.isEmpty()) {
            displayedSkills.addAll(allSkills)
        } else {
            displayedSkills.addAll(allSkills.filter {
                it.title.contains(query, true) ||
                        it.category.contains(query, true) ||
                        it.description.contains(query, true)
            })
        }
        adapter.notifyDataSetChanged()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // Adapter
    class SkillAdapter(
        private val skills: List<Skill>,
        private val onItemClick: (Skill) -> Unit
    ) : RecyclerView.Adapter<SkillAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            val tvUser: TextView = itemView.findViewById(R.id.tvUser)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_explore_skill, parent, false)
            return ViewHolder(view)
        }

        // In ExploreActivity's SkillAdapter onItemClick
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val skill = skills[position]
            holder.tvTitle.text = skill.title
            holder.tvCategory.text = skill.category
            holder.tvUser.text = "By: ${skill.userName}"
            holder.itemView.setOnClickListener {
                if (skill.skillType == "playlist") {
                    // Navigate to PlaylistActivity with skill.id
                    val intent = Intent(holder.itemView.context, PlaylistActivity::class.java)
                    intent.putExtra("skillId", skill.id)
                    holder.itemView.context.startActivity(intent)
                } else {
                    // Navigate to video view / trade screen
                    Toast.makeText(holder.itemView.context, skill.title, Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun getItemCount() = skills.size
    }
}