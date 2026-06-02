package com.taxiapp.passenger

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "taxi_app_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_REFERRAL_CODE = "referral_code"
        private const val KEY_FIRST_RIDE_DISCOUNT = "first_ride_discount"
        private const val KEY_WALLET_BALANCE = "wallet_balance"
        private const val KEY_TOTAL_RIDES = "total_rides"
        private const val KEY_REFERRAL_COUNT = "referral_count"
        private const val KEY_ACTIVE_RIDE_ID = "active_ride_id"
        private const val KEY_ACTIVE_RIDE_DATA = "active_ride_data"
    }
    
    fun saveSession(userId: String, userName: String, userEmail: String, userPhone: String, token: String = "") {
        val editor = prefs.edit()
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_TOKEN, token)
        editor.putString(KEY_USER_ID, userId)
        editor.putString(KEY_USER_NAME, userName)
        editor.putString(KEY_USER_EMAIL, userEmail)
        editor.putString(KEY_USER_PHONE, userPhone)
        editor.apply()
    }
    
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }
    
    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }
    
    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }
    
    fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }
    
    fun getUserPhone(): String? {
        return prefs.getString(KEY_USER_PHONE, null)
    }
    
    fun setReferralCode(code: String) {
        prefs.edit().putString(KEY_REFERRAL_CODE, code).apply()
    }
    
    fun getReferralCode(): String? {
        return prefs.getString(KEY_REFERRAL_CODE, null)
    }
    
    fun setFirstRideDiscount(used: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_RIDE_DISCOUNT, used).apply()
    }
    
    fun hasFirstRideDiscount(): Boolean {
        return !prefs.getBoolean(KEY_FIRST_RIDE_DISCOUNT, false)
    }
    
    fun setWalletBalance(balance: Double) {
        prefs.edit().putFloat(KEY_WALLET_BALANCE, balance.toFloat()).apply()
    }
    
    fun getWalletBalance(): Double {
        return prefs.getFloat(KEY_WALLET_BALANCE, 0.0f).toDouble()
    }
    
    fun addToWalletBalance(amount: Double) {
        val current = getWalletBalance()
        setWalletBalance(current + amount)
    }
    
    fun subtractFromWalletBalance(amount: Double): Boolean {
        val current = getWalletBalance()
        if (current >= amount) {
            setWalletBalance(current - amount)
            return true
        }
        return false
    }
    
    fun setTotalRides(count: Int) {
        prefs.edit().putInt(KEY_TOTAL_RIDES, count).apply()
    }
    
    fun getTotalRides(): Int {
        return prefs.getInt(KEY_TOTAL_RIDES, 0)
    }
    
    fun incrementTotalRides() {
        setTotalRides(getTotalRides() + 1)
    }
    
    fun setReferralCount(count: Int) {
        prefs.edit().putInt(KEY_REFERRAL_COUNT, count).apply()
    }
    
    fun getReferralCount(): Int {
        return prefs.getInt(KEY_REFERRAL_COUNT, 0)
    }
    
    fun incrementReferralCount() {
        setReferralCount(getReferralCount() + 1)
    }
    
    fun setActiveRideId(rideId: String?) {
        if (rideId != null) {
            prefs.edit().putString(KEY_ACTIVE_RIDE_ID, rideId).apply()
        } else {
            prefs.edit().remove(KEY_ACTIVE_RIDE_ID).apply()
        }
    }
    
    fun getActiveRideId(): String? {
        return prefs.getString(KEY_ACTIVE_RIDE_ID, null)
    }
    
    fun setActiveRideData(rideData: String?) {
        if (rideData != null) {
            prefs.edit().putString(KEY_ACTIVE_RIDE_DATA, rideData).apply()
        } else {
            prefs.edit().remove(KEY_ACTIVE_RIDE_DATA).apply()
        }
    }
    
    fun getActiveRideData(): String? {
        return prefs.getString(KEY_ACTIVE_RIDE_DATA, null)
    }
    
    fun clearActiveRide() {
        prefs.edit()
            .remove(KEY_ACTIVE_RIDE_ID)
            .remove(KEY_ACTIVE_RIDE_DATA)
            .apply()
    }
    
    fun clearSession() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }
}
