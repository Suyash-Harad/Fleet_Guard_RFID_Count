package com.bartech.fleet_guard_rfid_count

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import okhttp3.OkHttpClient
import okhttp3.Request

class WebSettingActivity : AppCompatActivity() {

    private lateinit var backBtn: ImageButton
    private lateinit var ipAddressInput: TextInputEditText
    private lateinit var portInput: TextInputEditText
    private lateinit var protocolDropdown: AutoCompleteTextView // NEW
    private lateinit var saveBtn: MaterialButton
    private lateinit var urlDisplayCard: CardView
    private lateinit var testConnectionBtn: MaterialButton
    private lateinit var generatedUrlText: TextView
    private lateinit var copyUrlBtn: ImageButton
    private lateinit var resultCard: MaterialCardView
    private lateinit var resultText: TextView

    private lateinit var prefs: Prefs // NEW: Initialize Prefs here

    private var loadingDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        enableEdgeToEdge()
        setContentView(R.layout.activity_web_setting)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        backBtn = findViewById(R.id.backBtn)
        ipAddressInput = findViewById(R.id.ipAddressInput)
        portInput = findViewById(R.id.portInput)
        protocolDropdown = findViewById(R.id.protocolDropdown) // NEW
        saveBtn = findViewById(R.id.saveBtn)
        urlDisplayCard = findViewById(R.id.urlDisplayCard)
        testConnectionBtn = findViewById(R.id.testConnectionBtn)
        generatedUrlText = findViewById(R.id.generatedUrlText)
        copyUrlBtn = findViewById(R.id.copyUrlBtn)
        resultCard = findViewById(R.id.resultCard)
        resultText = findViewById(R.id.resultText)

        prefs = Prefs(this) // Initialize prefs here

        // Setup protocol dropdown menu
        val protocols = resources.getStringArray(R.array.protocol_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, protocols)
        protocolDropdown.setAdapter(adapter)

        // Load saved values and update UI
        val ipAddress = prefs.getIpAddress() ?:  ""
        val port = prefs.getPortNumber() ?: ""
        val protocol = prefs.getProtocol() ?: "http" // Default to http

        ipAddressInput.setText(ipAddress)
        portInput.setText(port)
        protocolDropdown.setText(protocol, false)

        if (!ipAddress.isNullOrEmpty() && !port.isNullOrEmpty()) {
            setVisibilityForFields(View.VISIBLE)
            val generatedUrl = "${protocol}://${ipAddress}:${port}"
            generatedUrlText.text = generatedUrl
            showSuccessResult("Configuration saved")
        } else {
            setVisibilityForFields(View.VISIBLE)
            showWarningResult("No URL to test")
        }

        backBtn.setOnClickListener {
            onBackPressed()
        }

        copyUrlBtn.setOnClickListener {
            val generatedUrl = generatedUrlText.text.toString().trim()

            if (generatedUrl.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Generated URL", generatedUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No URL to copy", Toast.LENGTH_SHORT).show()
            }
        }

        saveBtn.setOnClickListener {
            val ipAddress = ipAddressInput.text.toString()
            val portNumber = portInput.text.toString()
            val protocol = protocolDropdown.text.toString()

            if (ipAddress.isNotEmpty() && portNumber.isNotEmpty()) {
                prefs.setIpAddressAndPort(protocol, ipAddress, portNumber)
                setVisibilityForFields(View.VISIBLE)
                val generatedUrl = "${protocol}://${ipAddress}:${portNumber}"
                generatedUrlText.text = generatedUrl
                showSuccessResult("Configuration saved")
            } else {
                Toast.makeText(this, "Please enter both IP address and port number", Toast.LENGTH_SHORT).show()
                setVisibilityForFields(View.VISIBLE)
            }
        }

        testConnectionBtn.setOnClickListener {
            val generatedUrl = generatedUrlText.text.toString().trim()

            if (generatedUrl.isNotEmpty()) {
                showLoadingDialog()

                Thread {
                    try {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .build()

                        val request = Request.Builder()
                            .url(generatedUrl)
                            .get()
                            .build()

                        val response = client.newCall(request).execute()

                        runOnUiThread {
                            hideLoadingDialog()
                            if (response.isSuccessful) {
                                showSuccessResult("Connection Successful")
                            } else {
                                showErrorResult("Connection Failed (Code: ${response.code})")
                            }
                        }
                        response.close()
                    } catch (e: Exception) {
                        runOnUiThread {
                            hideLoadingDialog()
                            showErrorResult("Error: ${e.localizedMessage}")
                        }
                    }
                }.start()
            } else {
                showWarningResult("No URL to test")
            }
        }
    }

    private fun setVisibilityForFields(visibility: Int) {
        urlDisplayCard.visibility = visibility
        testConnectionBtn.visibility = visibility
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = Dialog(this).apply {
                setCancelable(false)
                setContentView(LayoutInflater.from(context).inflate(R.layout.loading_dialog, null))
                window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun showSuccessResult(message: String) {
        resultCard.visibility = View.VISIBLE
        resultCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success))
        resultText.text = message
    }

    private fun showErrorResult(message: String) {
        resultCard.visibility = View.VISIBLE
        resultCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.error))
        resultText.text = message
    }

    private fun showWarningResult(message: String) {
        resultCard.visibility = View.VISIBLE
        resultCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.warning))
        resultText.text = message
    }

}