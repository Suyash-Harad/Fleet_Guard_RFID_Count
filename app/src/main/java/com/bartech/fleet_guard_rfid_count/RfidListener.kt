package com.bartech.fleet_guard_rfid_count

interface RfidListener {
    fun onTagRead(epc: String)
    fun onScanStart() {}
    fun onScanStop() {}
    fun onError(error: String) {}
    fun onReady() {}
}

