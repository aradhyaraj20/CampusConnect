package com.example.campusconnect.core.ai

import android.content.Context
import android.content.SharedPreferences

/**
 * Resolves Gemini configuration from two layers, in priority order:
 *
 *   1. Runtime override  - a key an ADMIN entered in-app (Admin > AI Configuration).
 *                          Stored in private SharedPreferences, never in source or XML.
 *   2. Build-time value  - BuildConfig, populated from local.properties at build time.
 *
 * The runtime layer exists so the app can be demoed/handed over without rebuilding,
 * while local.properties stays the option for developer machines. Either way the key
 * never lives in Kotlin source, XML, strings.xml, or version control (spec section 37).
 *
 * init() must be called once (from CampusConnectApp) before the runtime layer works;
 * if it isn't, AIConfig degrades safely to the BuildConfig value alone.
 */
object AIConfig {
    const val DEFAULT_MODEL = "gemini-2.5-flash"

    private const val PREF_NAME = "campus_connect_ai_config"
    private const val KEY_API_KEY = "runtime_gemini_api_key"
    private const val KEY_MODEL = "runtime_gemini_model"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    private val buildTimeApiKey: String
        get() = runCatching {
            val field = Class.forName("com.example.campusconnect.BuildConfig")
                .getField("GEMINI_API_KEY")
            (field.get(null) as? String).orEmpty()
        }.getOrDefault("")

    private val buildTimeModel: String
        get() = runCatching {
            val field = Class.forName("com.example.campusconnect.BuildConfig")
                .getField("GEMINI_MODEL")
            (field.get(null) as? String).orEmpty()
        }.getOrDefault(DEFAULT_MODEL)

    /** The key actually used for requests - runtime override wins over the build-time value. */
    val apiKey: String
        get() {
            val runtime = prefs?.getString(KEY_API_KEY, null).orEmpty().trim()
            return if (runtime.isNotBlank()) runtime else buildTimeApiKey.trim()
        }

    /** Cached/discovered model, or blank when it should be discovered from the key. */
    val model: String
        get() {
            val runtime = prefs?.getString(KEY_MODEL, null).orEmpty().trim()
            return if (runtime.isNotBlank()) runtime else if (isUsingRuntimeKey) "" else buildTimeModel.trim()
        }

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && apiKey != "YOUR_GEMINI_API_KEY"

    /** True when the active key came from the in-app admin screen rather than the build. */
    val isUsingRuntimeKey: Boolean
        get() = prefs?.getString(KEY_API_KEY, null).orEmpty().isNotBlank()

    /** True when a key was baked in at build time via local.properties. */
    val hasBuildTimeKey: Boolean
        get() = buildTimeApiKey.isNotBlank()

    /** Saves only the key; the model is discovered from the key (see GeminiAIDataSource). */
    fun saveRuntimeKey(apiKey: String) {
        prefs?.edit()
            ?.putString(KEY_API_KEY, apiKey.trim())
            ?.remove(KEY_MODEL)
            ?.apply()
    }

    /** Caches the model discovered for the active key; blank clears it to force re-discovery. */
    fun cacheModel(model: String) {
        prefs?.edit()?.putString(KEY_MODEL, model.trim())?.apply()
    }

    /** Drops the in-app key, falling back to whatever local.properties provided. */
    fun clearRuntimeKey() {
        prefs?.edit()?.remove(KEY_API_KEY)?.remove(KEY_MODEL)?.apply()
    }

    /**
     * Shows only enough of the key to confirm which one is active - the full value is
     * never rendered back into the UI once saved.
     */
    fun maskedKey(): String {
        val key = apiKey
        return when {
            key.isBlank() -> "Not set"
            key.length <= 8 -> "\u2022".repeat(key.length)
            else -> "${key.take(4)}${"\u2022".repeat(8)}${key.takeLast(4)}"
        }
    }
}
