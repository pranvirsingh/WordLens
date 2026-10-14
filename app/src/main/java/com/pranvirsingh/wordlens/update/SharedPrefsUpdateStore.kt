package com.pranvirsingh.wordlens.update

import android.content.Context
import androidx.core.content.edit

/** [UpdateStore] backed by the app's private SharedPreferences. */
class SharedPrefsUpdateStore(context: Context) : UpdateStore {
    private val prefs = context.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)

    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)

    override fun edit(block: UpdateStore.Editor.() -> Unit) {
        prefs.edit {
            val editor = this
            object : UpdateStore.Editor {
                override fun putString(key: String, value: String) {
                    editor.putString(key, value)
                }

                override fun putLong(key: String, value: Long) {
                    editor.putLong(key, value)
                }

                override fun remove(key: String) {
                    editor.remove(key)
                }
            }.block()
        }
    }
}
