package com.bartech.fleet_guard_rfid_count

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.auth0.android.jwt.JWT
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.code

data class LoginResponse(
    @SerializedName("Status")
    val status: String,
    @SerializedName("Message")
    val message: String,
    @SerializedName("token")
    val token: String?,
    @SerializedName("is_change_password")
    val isChangePassword: Boolean?
)

data class UserPayload(
    @SerializedName("user_id") val userId: String,
    @SerializedName("user_name") val userName: String,
    @SerializedName("user_status") val userStatus: String,
    @SerializedName("user_role") val userRole: String,
    @SerializedName("name") val name: String,
    @SerializedName("hht_menu_access") val hhtMenuAccess: String,
    @SerializedName("plant_code") val plantCode: String?,
    @SerializedName("company_code") val companyCode: String?,
    @SerializedName("line_code") val lineCode: String?
)

class LoginActivity : AppCompatActivity() {

    private lateinit var settingsBtn: ImageButton
    private lateinit var userIdInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginBtn: MaterialButton
    private lateinit var versionCodeTextView: TextView

    // Correctly initialize dependencies with lazy delegation
    private val prefs by lazy { Prefs(this) }
    private val apiManager by lazy { ApiManager(this, prefs) }
    private val gson by lazy { Gson() }

    private var loadingDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        settingsBtn = findViewById(R.id.settingsBtn)
        userIdInput = findViewById(R.id.userIdInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginBtn = findViewById(R.id.loginBtn)
        versionCodeTextView = findViewById(R.id.versionCodeTextView)

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            versionCodeTextView.text = "Version: $versionName"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        if (prefs.getIpAddress().isNullOrEmpty() || prefs.getPortNumber().isNullOrEmpty()) {
            Toast.makeText(this, "Please do the Network Configuration first.", Toast.LENGTH_SHORT).show()
        }

        settingsBtn.setOnClickListener {
            val intent = Intent(this, WebSettingActivity::class.java)
            startActivity(intent)
        }

        loginBtn.setOnClickListener {
            staticLogin()
        }
    }

    private fun staticLogin() {
        val userId = userIdInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        val deviceSN = getDeviceSerialNumber().trim()

        if (userId.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username and password.", Toast.LENGTH_SHORT).show()
            return
        }

        if (userId == "admin" && password == "admin") {
            Toast.makeText(this, "Login successful.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainScreenActivity::class.java))
        } else {
            Toast.makeText(this, "Invalid credentials.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkCredentials() {
        val userId = userIdInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        val deviceSN = getDeviceSerialNumber().trim()

        if (prefs.getIpAddress().isNullOrEmpty() || prefs.getPortNumber().isNullOrEmpty()) {
            Toast.makeText(this, "Please do the Network Configuration first.", Toast.LENGTH_SHORT).show()
            return
        }

        if (userId.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username and password.", Toast.LENGTH_SHORT).show()
            return
        }

        val endpoint = "api/auth/check-credentials"

        val jsonObject = JsonObject().apply {
            addProperty("User_ID", userId)
            addProperty("User_Password", password)
            addProperty("DeviceSN", deviceSN)
            addProperty("ApplicationType", "HHT")
        }

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { showLoadingDialog() }

            try {
                val response = apiManager.callApi(endpoint, "POST", jsonObject.toString())
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    if (response.isSuccessful && responseBody != null) {
                        handleSuccessfulResponse(responseBody)
                    } else {
                        val errorMessage = response.message
                        Toast.makeText(this@LoginActivity, "Error ${response.code}: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    Toast.makeText(this@LoginActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    Toast.makeText(this@LoginActivity, "Configuration Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleSuccessfulResponse(responseBody: String) {
        val loginResponse = gson.fromJson(responseBody, LoginResponse::class.java)
        Log.d("Response Body", responseBody)

        Toast.makeText(this, loginResponse.message, Toast.LENGTH_SHORT).show()

        when (loginResponse.status) {
            "T" -> {
                loginResponse.token?.let { token ->
                    val userPayload = decodeJwt(token)
                    if (userPayload != null) {
                        // Correctly calling saveLoginData on the prefs instance
                        prefs.saveLoginData(token, userPayload.userId, userPayload.userRole, userPayload.userName, userPayload.hhtMenuAccess, userPayload.plantCode)
                        if (loginResponse.isChangePassword == true) {
//                            startActivity(Intent(this, ChangePasswordActivity::class.java))
                        } else {
                            startActivity(Intent(this, MainScreenActivity::class.java))
                        }
                    }
                }
            }
            "F" -> { /* Message already handled by Toast */ }
//            "G" -> {
//                startActivity(Intent(this, UserDeviceRegisterActivity::class.java))
//            }
            else -> {
                Toast.makeText(this, "Unknown status: ${loginResponse.status}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun decodeJwt(token: String): UserPayload? {
        return try {
            val jwt = JWT(token)
            val userClaim = jwt.getClaim("user")
            // The user claim is a JsonObject. Convert it to a String first, then use Gson.
            val userClaimJson = userClaim.asObject(JsonObject::class.java).toString()
            gson.fromJson(userClaimJson, UserPayload::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("HardwareIds")
    private fun getDeviceSerialNumber(): String {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    // Android 10+ (no access to real serial for normal apps)
                    Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    try {
                        Build.getSerial() // requires READ_PHONE_STATE permission
                    } catch (e: SecurityException) {
                        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                    }
                }
                else -> {
                    @Suppress("DEPRECATION")
                    Build.SERIAL
                }
            }
        } catch (e: Exception) {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        }
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = Dialog(this).apply {
                setCancelable(false)
                setContentView(LayoutInflater.from(context).inflate(R.layout.loading_dialog, null))
                window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

}