package com.bartech.fleet_guard_rfid_count

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.GridView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar

class MainScreenActivity : AppCompatActivity() {

    private lateinit var topAppBar: MaterialToolbar
    private lateinit var gridView: GridView

    private lateinit var hhtAccessMenuValues: String
    private val items: MutableList<Pair<String, String>> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        topAppBar = findViewById(R.id.topAppBar)
        gridView = findViewById(R.id.gridView)

        hhtAccessMenuValues = Prefs(this).getHHTMenuAccess() ?: "1,2,3"

        topAppBar.setNavigationOnClickListener {
            onBackPressed()
        }

        if (hhtAccessMenuValues.contains("1")){
            items.add(Pair(
                "Mold Identification",
                "Assigns and registers unique RFID tags to individual machine parts for tracking and inventory management."
            ))
        }
        if (hhtAccessMenuValues.contains("2")){
            items.add(Pair(
                "Mold Cycle Count (Auto)",
                "Automatically calculates the usage cycle count of tagged machine parts by scanning their associated RFID tags."
            ))
        }

        if (hhtAccessMenuValues.contains("3")){
            items.add(Pair(
                "Mold Cycle Count",
                "Automatically calculates the usage cycle count of tagged machine parts by scanning their associated RFID tags."
            ))
        }

        // Initialize and set the custom adapter
        val adapter = MovementCardAdapter(this, items)
        gridView.adapter = adapter

        // Set click listener for the GridView items
        gridView.setOnItemClickListener { parent, view, position, id ->
            val clickedItem = items[position]
            // Use a 'when' statement to add specific navigation logic for each card
            when (clickedItem.first) {
                "Mold Identification" -> {
                    startActivity(Intent(this, PartIdentificationActivity::class.java))
                }
                "Mold Cycle Count (Auto)" -> {
                    startActivity(Intent(this, PartCycleCount_Auto_Activity::class.java))
                }
                "Mold Cycle Count" -> {
                    startActivity(Intent(this, PartCycleCountActivity::class.java))
                }
            }
        }

    }
}