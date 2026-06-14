package com.example.personeltracking2026kodamjayav2.core.session

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

        private const val KEY_ID = "id"
        private const val KEY_NRP = "nrp"
        private const val KEY_SATUAN = "satuan"
        private const val KEY_BATALYON = "batalyon"
        private const val KEY_PELETON = "peleton"
        private const val KEY_REGU = "regu"
        private const val KEY_KOMPI = "kompi"
        private const val KEY_DIVISI = "divisi"
        private const val KEY_BRIGADE = "brigade"
        private const val KEY_TEAM = "team"
        private const val KEY_UNIT = "unit"
        private const val KEY_RANK = "rank"
        private const val KEY_AVATAR_URL = "avatar_url"

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

    fun saveName(name: String?) {
        prefs.edit()
            .putString(KEY_NAME, name ?: "")
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
            .putString(KEY_AVATAR_URL, avatar ?: "")
            .apply()
    }

    fun getToken()   : String? = prefs.getString(KEY_TOKEN, null)
    fun getName()    : String? = prefs.getString(KEY_NAME, null)
    fun getRole()    : String? = prefs.getString(KEY_ROLE, null)

    fun saveLastScreen(screen: com.example.personeltracking2026kodamjayav2.core.navigation.LastScreen) {
        prefs.edit()
            .putString(KEY_LAST_SCREEN, screen.name)
            .apply()
    }

    fun getLastScreen(): com.example.personeltracking2026kodamjayav2.core.navigation.LastScreen? {
        val value = prefs.getString(KEY_LAST_SCREEN, null)
        return try {
            value?.let {
                com.example.personeltracking2026kodamjayav2.core.navigation.LastScreen.valueOf(it)
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
        id: String?,
        nrp: String?,
        name: String?,
        satuan: String?,
        batalyon: String?,
        peleton: String?,
        regu: String?,
        kompi: String?,
        divisi: String?,
        brigade: String?,
        team: String?,
        unit: String?,
        rank: String?,
        avatarUrl: String?
    ) {
        prefs.edit()
            .putString(KEY_ID, id ?: "")
            .putString(KEY_NRP, nrp ?: "")
            .putString(KEY_NAME, name ?: "")
            .putString(KEY_SATUAN, satuan ?: "")
            .putString(KEY_BATALYON, batalyon ?: "")
            .putString(KEY_PELETON, peleton ?: "")
            .putString(KEY_REGU, regu ?: "")
            .putString(KEY_KOMPI, kompi ?: "")
            .putString(KEY_DIVISI, divisi ?: "")
            .putString(KEY_BRIGADE, brigade ?: "")
            .putString(KEY_TEAM, team ?: "")
            .putString(KEY_UNIT, unit ?: "")
            .putString(KEY_RANK, rank ?: "")
            .putString(KEY_AVATAR_URL, avatarUrl ?: "")
            .apply()
    }


    fun getId(): String =
        prefs.getString(KEY_ID, "") ?: ""

    fun getNrp(): String =
        prefs.getString(KEY_NRP, "") ?: ""

    fun getSatuan(): String =
        prefs.getString(KEY_SATUAN, "") ?: ""

    fun getBatalyon(): String =
        prefs.getString(KEY_BATALYON, "") ?: ""

    fun getPeleton(): String =
        prefs.getString(KEY_PELETON, "") ?: ""

    fun getRegu(): String =
        prefs.getString(KEY_REGU, "") ?: ""

    fun getKompi(): String =
        prefs.getString(KEY_KOMPI, "") ?: ""

    fun getDivisi(): String =
        prefs.getString(KEY_DIVISI, "") ?: ""

    fun getBrigade(): String =
        prefs.getString(KEY_BRIGADE, "") ?: ""

    fun getTeam(): String =
        prefs.getString(KEY_TEAM, "") ?: ""

    fun getUnit(): String =
        prefs.getString(KEY_UNIT, "") ?: ""

    fun getRank(): String =
        prefs.getString(KEY_RANK, "") ?: ""

    fun getAvatarUrl(): String =
        prefs.getString(KEY_AVATAR_URL, "") ?: ""

    // ─── CLEAR ───────────────────────────────────────────────────────────────

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}