package com.bartech.fleet_guard_rfid_count

import android.content.Context
import android.content.SharedPreferences
import kotlin.apply

class Prefs(context: Context) {

    companion object {
        private const val USER_ID = "User_ID"
        private const val PROTOCOL = "protocol"
        private const val IP_ADDRESS = "ip_address"
        private const val PORT_NUMBER = "port_number"
        private const val PLANT_CODE = "plant_code"
        private const val USER_ROLE = "user_role"
        private const val USER_NAME = "user_name"
        private const val HHT_MENU_ACCESS = "hht_menu_access"
        private const val TOKEN = "Token"
    }

    private val preferences: SharedPreferences = context.getSharedPreferences("Fleet_Guard_RFID_Prefs", Context.MODE_PRIVATE)

    fun setIpAddressAndPort(protocol: String, ipAddress: String, port: String) {
        preferences.edit().apply {
            putString(PROTOCOL, protocol)
            putString(IP_ADDRESS, ipAddress)
            putString(PORT_NUMBER, port)
            apply()
        }
    }

    fun getIpAddress(): String? {
        return preferences.getString(IP_ADDRESS, null)
    }

    fun getPortNumber(): String? {
        return preferences.getString(PORT_NUMBER, null)
    }

    fun getProtocol(): String? {
        return preferences.getString(PROTOCOL, null)
    }

    fun saveLoginData(token: String, userId: String, userRole: String, userName: String, hhtAccessMenu: String, plantCode: String?) {
        preferences.edit().apply {
            putString(TOKEN, token)
            putString(USER_ID, userId)
            putString(USER_ROLE, userRole)
            putString(USER_NAME, userName)
            putString(HHT_MENU_ACCESS, hhtAccessMenu)
            putString(PLANT_CODE, plantCode)
            apply()
        }
    }

    fun getUserName(): String? {
        return preferences.getString(USER_NAME, null)
    }

    fun getUserRole(): String? {
        return preferences.getString(USER_ROLE, null)
    }

    fun getUserId(): String? {
        return preferences.getString(USER_ID, null)
    }

    fun getToken(): String? {
        return preferences.getString(TOKEN, null)
    }

    fun getPlantCode(): String? {
        return preferences.getString(PLANT_CODE, null)
    }

    fun getHHTMenuAccess(): String? {
        return preferences.getString(HHT_MENU_ACCESS, null)
    }


    fun clearToken() {
        preferences.edit().apply {
            remove(TOKEN)
            remove(USER_ID)
            remove(PLANT_CODE)
            remove(USER_ROLE)
            remove(USER_NAME)
            remove(HHT_MENU_ACCESS)
            apply()
        }
    }

}