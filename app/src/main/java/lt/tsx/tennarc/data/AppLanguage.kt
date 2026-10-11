package lt.tsx.tennarc.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.icu.util.ULocale
import android.os.Build
import android.os.LocaleList
import android.telephony.TelephonyManager
import androidx.core.content.edit
import lt.tsx.tennarc.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

object AppLanguage {
    fun supported(context: Context): List<Locale> {
        val tags = mutableListOf<String>()
        context.resources.getXml(R.xml._generated_res_locale_config).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name == "locale") {
                    xml.getAttributeValue(ANDROID_NS, "name")?.let(tags::add)
                }
            }
        }
        return tags.distinct().map(Locale::forLanguageTag)
    }

    fun chosen(context: Context): Locale? =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.takeUnless { it.isEmpty }?.get(0)
        } else {
            store(context).getString(KEY, null)?.let(Locale::forLanguageTag)
        }

    fun phone(context: Context): List<Locale> {
        val list = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            android.content.res.Resources.getSystem().configuration.locales
        }
        return List(list.size()) { list[it] }
    }

    fun guessed(context: Context): List<Locale> {
        val zone = try {
            android.icu.util.TimeZone.getRegion(java.util.TimeZone.getDefault().id)
        } catch (_: IllegalArgumentException) {
            null
        }
        val phone = context.getSystemService(TelephonyManager::class.java)
        val carrier = listOfNotNull(phone?.networkCountryIso, phone?.simCountryIso)
        return (listOfNotNull(zone) + carrier)
            .map { it.uppercase(Locale.ROOT) }
            .filter { it.length == 2 && it.all(Char::isLetter) }
            .distinct()
            .mapNotNull { region ->
                val likely = ULocale.addLikelySubtags(ULocale("und_$region"))
                likely.language.takeIf { it.isNotEmpty() && it != "und" }?.let { Locale.Builder().setLanguage(it).setScript(likely.script).setRegion(region).build() }
            }
    }

    fun choose(activity: Activity, locale: Locale?) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                locale?.let { LocaleList(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            store(activity).edit { if (locale == null) remove(KEY) else putString(KEY, locale.toLanguageTag()) }
            activity.recreate()
        }
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val locale = chosen(base) ?: return base
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    private fun store(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private const val STORE = "app_language"
    private const val KEY = "locale"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
}
