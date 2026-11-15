package com.dang.glancecaps.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dang.glancecaps.data.ModulePrefs
import java.util.Locale

object Lang {
    const val SYSTEM = "system"
    const val EN = "en"
    const val VI = "vi"
    const val ES = "es"

    var setting by mutableStateOf(SYSTEM)

    val code: String
        get() = when (setting) {
            VI, ES -> setting
            EN -> EN
            else -> when (Locale.getDefault().language) {
                "vi" -> VI
                "es" -> ES
                else -> EN
            }
        }

    val isVi: Boolean get() = code == VI

    val translating: Boolean get() = code != EN

    val locale: Locale
        get() = when (code) {
            VI -> Locale("vi", "VN")
            ES -> Locale("es", "419")
            else -> Locale.getDefault()
        }

    fun load(p: ModulePrefs) {
        setting = p.language
    }
}

fun tr(text: String): String = when (Lang.code) {
    Lang.VI -> Vi.translate(text)
    Lang.ES -> Es.translate(text)
    else -> text
}
