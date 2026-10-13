package com.example.campusconnect.testutil

import android.content.ContextWrapper
import android.content.SharedPreferences
import java.lang.reflect.Proxy

/**
 * Builds a Room-style DAO interface backed by lambdas, so use cases can be unit-tested on the JVM
 * without Room or Android. Only the methods a test cares about need a handler; any other call
 * returns 0 (fine for the fire-and-forget inserts/updates/deletes these tests ignore).
 * Suspend functions work because the trailing Continuation argument is simply dropped.
 */
inline fun <reified T : Any> fakeDao(handlers: Map<String, (List<Any?>) -> Any?>): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        val callArgs = (args?.toList() ?: emptyList()).filter { it !is kotlin.coroutines.Continuation<*> }
        val handler = handlers[method.name]
        if (handler != null) handler(callArgs) else 0
    } as T

/** In-memory SharedPreferences, enough for SessionManager. */
class FakePrefs : SharedPreferences {
    private val map = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { map[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { map[key] = values }
        override fun putInt(key: String, value: Int) = apply { map[key] = value }
        override fun putLong(key: String, value: Long) = apply { map[key] = value }
        override fun putFloat(key: String, value: Float) = apply { map[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { map[key] = value }
        override fun remove(key: String) = apply { map.remove(key) }
        override fun clear() = apply { map.clear() }
        override fun commit(): Boolean = true
        override fun apply() {}
    }
}

class FakeContext(private val prefs: SharedPreferences = FakePrefs()) : ContextWrapper(null) {
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
}
