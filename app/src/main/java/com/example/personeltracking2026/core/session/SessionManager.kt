package com.example.personeltracking2026.core.session

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREF_NAME      = "personel_tracking_session"
        private const val KEY_TOKEN      = "token"
        private const val KEY_NAME       = "name"
        private const val KEY_ROLE       = "role"

        // Personel detail — disimpan setelah API berhasil
        private const val KEY_RANK       = "rank"
        private const val KEY_UNIT       = "unit"
        private const val KEY_BATTALION  = "battalion"
        private const val KEY_SQUAD      = "squad"
        private const val KEY_AVATAR     = "avatar"
        private const val KEY_NRP        = "nrp"

        const val ROLE_PERSONEL = "personel"
        const val ROLE_BODYCAM  = "bodycam"

        private const val KEY_LAST_SCREEN = "last_screen"
    }

    // ─── AUTH ────────────────────────────────────────────────────────────────

    fun saveSession(token: String, name: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun saveRole(role: String) {
        prefs.edit().putString(KEY_ROLE, role).apply()
    }

    fun saveNrp(nrp: String) {
        prefs.edit().putString(KEY_NRP, nrp).apply()
    }

    fun saveAvatar(avatar: String?) {
        prefs.edit()
            .putString(KEY_AVATAR, avatar ?: "")
            .apply()
    }

    fun getToken()   : String? = prefs.getString(KEY_TOKEN, null)
    fun getName()    : String? = prefs.getString(KEY_NAME, null)
    fun getRole()    : String? = prefs.getString(KEY_ROLE, null)

    fun saveLastScreen(screen: com.example.personeltracking2026.core.navigation.LastScreen) {
        prefs.edit()
            .putString(KEY_LAST_SCREEN, screen.name)
            .apply()
    }

    fun getLastScreen(): com.example.personeltracking2026.core.navigation.LastScreen? {
        val value = prefs.getString(KEY_LAST_SCREEN, null)
        return try {
            value?.let {
                com.example.personeltracking2026.core.navigation.LastScreen.valueOf(it)
            }
        } catch (e: Exception) {
            null
        }
    }
    fun isLoggedIn() : Boolean = getToken() != null

    fun getUserId(): Int? {
        val token = getToken() ?: return null
        return try {
            val jwt = com.auth0.android.jwt.JWT(token)
            jwt.getClaim("user_id").asInt()
        } catch (e: Exception) {
            null
        }
    }

    fun getUsername(): String? {
        val token = getToken() ?: return null
        return try {
            val jwt = com.auth0.android.jwt.JWT(token)
            jwt.getClaim("username").asString()
        } catch (e: Exception) {
            null
        }
    }

    // ─── PERSONEL DETAIL ─────────────────────────────────────────────────────

    /**
     * Dipanggil di PersonelViewModel setelah API getPersonelDetail berhasil.
     * Supaya data identity tersedia meski API gagal di sesi berikutnya.
     */
    fun savePersonelDetail(
        nrp       : String?,
        rank      : String?,
        unit      : String?,
        battalion : String?,
        squad     : String?,
        avatar    : String?
    ) {
        prefs.edit()
            .putString(KEY_NRP,       nrp       ?: "")
            .putString(KEY_RANK,      rank      ?: "")
            .putString(KEY_UNIT,      unit      ?: "")
            .putString(KEY_BATTALION, battalion ?: "")
            .putString(KEY_SQUAD,     squad     ?: "")
            .putString(KEY_AVATAR,    avatar    ?: "")
            .apply()
    }

    fun getRank()      : String = prefs.getString(KEY_RANK,      "") ?: ""
    fun getUnit()      : String = prefs.getString(KEY_UNIT,      "") ?: ""
    fun getBattalion() : String = prefs.getString(KEY_BATTALION, "") ?: ""
    fun getSquad()     : String = prefs.getString(KEY_SQUAD,     "") ?: ""
    fun getAvatar()    : String = prefs.getString(KEY_AVATAR,    "") ?: ""
    fun getNrp()       : String = prefs.getString(KEY_NRP,       "") ?: ""

    // ─── CLEAR ───────────────────────────────────────────────────────────────

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}