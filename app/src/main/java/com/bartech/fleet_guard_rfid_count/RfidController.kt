package com.bartech.fleet_guard_rfid_count

import android.content.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.cipherlab.rfid.GeneralString
import com.cipherlab.rfid.RFIDMode

object RfidController {

    private var safeManager: SafeRfidManager? = null
    private var listener: RfidListener? = null
    private var context: Context? = null
    private var initialized = false

    private var scanMode = RfidScanMode.CONTINUOUS

    // -------------------------- EPC RECEIVER --------------------------
    private val epcReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {

            if (intent?.action != GeneralString.Intent_RFIDSERVICE_TAG_DATA)
                return

            val epc =
                intent.getStringExtra(GeneralString.EXTRA_EPC) ?: return

            handleEpc(epc)
        }
    }

    // ----------------------- SERVICE READY RECEIVER -------------------
    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {

            if (intent?.action != GeneralString.Intent_RFIDSERVICE_CONNECTED)
                return

            // *** CHANGE 1: Call configure() instead of init() ***
            // The service is connected; now we configure the device modes.
            initialized = safeManager?.configure() == true

            if (initialized)
                listener?.onReady()
            else
                listener?.onError("RFID configuration failed after service connected")
        }
    }

    // ----------------------------- INIT -------------------------------
    fun init(ctx: Context, listener: RfidListener): Boolean {
        this.context = ctx.applicationContext
        this.listener = listener
        this.safeManager = SafeRfidManager(ctx)

        // *** CHANGE 2: Call init() here to START the asynchronous connection. ***
        if (safeManager?.init() == false) {
            listener.onError("RFID Manager instance failed to initialize.")
            return false
        }

        // Register SERVICE_CONNECTED receiver (Registers the receiver that waits for the service)
        val serviceFilter =
            IntentFilter(GeneralString.Intent_RFIDSERVICE_CONNECTED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context?.registerReceiver(serviceReceiver, serviceFilter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                context!!,
                serviceReceiver,
                serviceFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        // Register EPC scanning receiver
        val epcFilter =
            IntentFilter(GeneralString.Intent_RFIDSERVICE_TAG_DATA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context?.registerReceiver(epcReceiver, epcFilter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                context!!,
                epcReceiver,
                epcFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        // Return true because the initialization process has successfully started.
        return true
    }

    // ---------------------------- EPC ------------------------------
    private fun handleEpc(epc: String) {
            listener?.onTagRead(epc)
        if (scanMode == RfidScanMode.SINGLE) {
            Handler(Looper.getMainLooper()).postDelayed({
                stopScan()
            }, 150)
        }
    }

    // ---------------------------- MODES -----------------------------
    fun setScanMode(mode: RfidScanMode) {
        scanMode = mode
        when (mode) {
            RfidScanMode.CONTINUOUS -> safeManager?.setScanModeContinuous()
            RfidScanMode.SINGLE -> safeManager?.setScanModeSingle()
        }
    }

    fun setWorkMode(mode: RfidWorkMode) {
        when (mode) {
            RfidWorkMode.EPC_ONLY -> safeManager?.setMode(RFIDMode.Inventory)
            RfidWorkMode.TID_ONLY -> safeManager?.setMode(RFIDMode.Inventory_EPC_TID)
            RfidWorkMode.USER_MEMORY -> safeManager?.setMode(RFIDMode.ReadTag)
        }
    }

    fun getApiVersion() = safeManager?.getApiVersion() ?: "Unknown"

    // -------------------------- START / STOP -------------------------
    fun startScan() {
        if (!initialized) {
            listener?.onError("RFID not ready. Wait for onReady() call.")
            return
        }
        listener?.onScanStart()
        safeManager?.startScan()
    }

    fun stopScan() {
        safeManager?.stopScan()
        listener?.onScanStop()
    }

    // ---------------------------- CLEANUP ---------------------------
    fun release() {
        try {
            context?.unregisterReceiver(epcReceiver)
            context?.unregisterReceiver(serviceReceiver)
        } catch (_: Exception) {}

        safeManager?.release()
        safeManager = null
        initialized = false
    }
}