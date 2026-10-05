package com.bartech.fleet_guard_rfid_count

import android.content.Context
import android.util.Log
import com.cipherlab.rfid.ScanMode
import com.cipherlab.rfid.SwitchMode
import com.cipherlab.rfid.RFIDMode
import com.cipherlab.rfidapi.RfidManager

class SafeRfidManager(private val context: Context) {

    private var rfid: RfidManager? = null
    private var apiVersion: String = "Unknown"
    private val tag = "SafeRfidManager"

    /**
     * Initializes the RfidManager instance. This starts the asynchronous
     * service connection process.
     */
    fun init(): Boolean {
        return try {
            rfid = RfidManager.InitInstance(context)

            if (rfid == null) {
                Log.e(tag, "InitInstance returned NULL. Check if the device is compatible or the RFID Service app is installed.")
                return false
            }

            // Do NOT check GetConnectionStatus() or set RFIDMode here, as the service is not yet connected.

            apiVersion = try {
                rfid?.GetAPIVersion() ?: "Unknown"
            } catch (e: Exception) { "Unknown" }

            Log.i(tag, "RFID initialization started, API=$apiVersion")
            true

        } catch (ex: Exception) {
            Log.e(tag, "Init error: ${ex.message}")
            false
        }
    }

    /**
     * Configures the RFID device. This must be called AFTER the
     * GeneralString.Intent_RFIDSERVICE_CONNECTED broadcast is received.
     */
    fun configure(): Boolean {
        if (rfid == null) return false
        return try {
            // Now, it is safe to check the status and configure the device
            if (!rfid!!.GetConnectionStatus()) {
                Log.e(tag, "Configuration failed: Service reports not connected despite receiving CONNECTED intent.")
                return false
            }

            // Set the default mode and log success
            rfid?.SetRFIDMode(RFIDMode.Inventory)
            Log.i(tag, "RFID Service configured successfully.")
            return true
        } catch (ex: Exception) {
            Log.e(tag, "Configuration error: ${ex.message}")
            return false
        }
    }

    fun isReady() = (rfid != null && rfid!!.GetConnectionStatus())

    fun setMode(mode: RFIDMode) {
        try { rfid?.SetRFIDMode(mode) } catch (_: Exception) {}
    }

    fun getApiVersion() = apiVersion

    fun setScanModeContinuous() {
        try { rfid?.SetScanMode(ScanMode.Continuous) } catch (_: Exception) {}
    }

    fun setScanModeSingle() {
        try { rfid?.SetScanMode(ScanMode.Single) } catch (_: Exception) {}
    }

    fun startScan(): Boolean {
        return try {
            if (!isReady()) return false
            rfid?.SetSwitchMode(SwitchMode.UHFRFIDReader)
            rfid?.SoftScanTrigger(true)
            true
        } catch (_: Exception) { false }
    }

    fun stopScan() {
        try { rfid?.SoftScanTrigger(false) } catch (_: Exception) {}
    }

    fun release() {
        try { rfid?.Release() } catch (_: Exception) {}
        rfid = null
    }
}