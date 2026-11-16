package com.dayflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("dayflow_settings")

data class Prefs(
    val name: String = "", val waterGoal: Int = 8, val waterInterval: Int = 120,
    val waterStart: String = "08:00", val waterEnd: String = "22:00",
    val wake: String = "06:30", val sleep: String = "23:00",
    val quietEnabled: Boolean = false, val quietStart: String = "22:30", val quietEnd: String = "07:00",
    val notifications: Boolean = true, val sound: Boolean = true, val accent: String = "dawn",
    val focusMode: Boolean = false, val reviewEnabled: Boolean = true, val reviewTime: String = "20:00",
    val sleeping: Boolean = false, val sleepStart: Long = 0L)

class PrefsStore(private val ctx: Context) {
    private object K {
        val name = stringPreferencesKey("name"); val wg = intPreferencesKey("wg"); val wi = intPreferencesKey("wi")
        val ws = stringPreferencesKey("ws"); val we = stringPreferencesKey("we")
        val wake = stringPreferencesKey("wake"); val slp = stringPreferencesKey("slp")
        val qe = booleanPreferencesKey("qe"); val qs = stringPreferencesKey("qs"); val qn = stringPreferencesKey("qn")
        val notif = booleanPreferencesKey("notif"); val sound = booleanPreferencesKey("sound")
        val accent = stringPreferencesKey("accent"); val focus = booleanPreferencesKey("focus")
        val re = booleanPreferencesKey("re"); val rt = stringPreferencesKey("rt")
        val sleeping = booleanPreferencesKey("sleeping"); val ss = longPreferencesKey("ss")
        /** true once the old built-in sample data has been removed (or never existed: fresh install). */
        val noSample = booleanPreferencesKey("no_sample")
    }
    val flow: Flow<Prefs> = ctx.ds.data.map { p ->
        Prefs(p[K.name] ?: "", p[K.wg] ?: 8, p[K.wi] ?: 120, p[K.ws] ?: "08:00", p[K.we] ?: "22:00",
            p[K.wake] ?: "06:30", p[K.slp] ?: "23:00", p[K.qe] ?: false, p[K.qs] ?: "22:30", p[K.qn] ?: "07:00",
            p[K.notif] ?: true, p[K.sound] ?: true, p[K.accent] ?: "dawn", p[K.focus] ?: false,
            p[K.re] ?: true, p[K.rt] ?: "20:00", p[K.sleeping] ?: false, p[K.ss] ?: 0L)
    }
    suspend fun set(b: (MutablePreferences) -> Unit) { ctx.ds.edit { b(it) } }
    suspend fun clear() = set { it.clear() }
    suspend fun sampleRemoved(): Boolean = ctx.ds.data.first()[K.noSample] ?: false
    suspend fun markSampleRemoved() = set { it[K.noSample] = true }
    suspend fun name(v: String) = set { it[K.name] = v }
    suspend fun waterGoal(v: Int) = set { it[K.wg] = v }
    suspend fun waterInterval(v: Int) = set { it[K.wi] = v }
    suspend fun waterStart(v: String) = set { it[K.ws] = v }
    suspend fun waterEnd(v: String) = set { it[K.we] = v }
    suspend fun wake(v: String) = set { it[K.wake] = v }
    suspend fun sleepTime(v: String) = set { it[K.slp] = v }
    suspend fun quietEnabled(v: Boolean) = set { it[K.qe] = v }
    suspend fun quietStart(v: String) = set { it[K.qs] = v }
    suspend fun quietEnd(v: String) = set { it[K.qn] = v }
    suspend fun notifications(v: Boolean) = set { it[K.notif] = v }
    suspend fun sound(v: Boolean) = set { it[K.sound] = v }
    suspend fun accent(v: String) = set { it[K.accent] = v }
    suspend fun focus(v: Boolean) = set { it[K.focus] = v }
    suspend fun reviewEnabled(v: Boolean) = set { it[K.re] = v }
    suspend fun reviewTime(v: String) = set { it[K.rt] = v }
    suspend fun sleeping(v: Boolean, start: Long) = set { it[K.sleeping] = v; it[K.ss] = start }
}
