package com.gang.test.player

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import com.gang.test.player.Utils.hasSAFChooser
import com.gang.test.player.Utils.isPiPSupported
import com.gang.test.player.Utils.orderByValue
import java.text.Collator
import java.util.*

class SettingsActivity : AppCompatActivity() {
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= 29) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            window.navigationBarColor = Color.TRANSPARENT
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
        if (Build.VERSION.SDK_INT >= 29) {
            val layout = findViewById<LinearLayout>(R.id.settings_layout)
            layout.setOnApplyWindowInsetsListener { view: View, windowInsets: WindowInsets ->
                view.setPadding(
                    windowInsets.systemWindowInsetLeft,
                    windowInsets.systemWindowInsetTop,
                    windowInsets.systemWindowInsetRight,
                    0
                )
                if (recyclerView != null) {
                    recyclerView!!.setPadding(0, 0, 0, windowInsets.systemWindowInsetBottom)
                }
                windowInsets.consumeSystemWindowInsets()
                windowInsets
            }
        }
    }

    @UnstableApi
    class SettingsFragment : PreferenceFragmentCompat() {
         override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val preferenceAutoPiP = findPreference<Preference>("autoPiP")
            if (preferenceAutoPiP != null) {
                preferenceAutoPiP.isEnabled = isPiPSupported(this.requireContext())
            }
            val preferenceFrameRateMatching = findPreference<Preference>("frameRateMatching")
            if (preferenceFrameRateMatching != null) {
                preferenceFrameRateMatching.isEnabled = Build.VERSION.SDK_INT >= 23
            }
            val listPreferenceFileAccess = findPreference<ListPreference>("fileAccess")
            if (listPreferenceFileAccess != null) {
                var entries: List<String> =
                    ArrayList(listOf(*resources.getStringArray(R.array.file_access_entries)))
                var values: List<String> =
                    ArrayList(listOf(*resources.getStringArray(R.array.file_access_values)))
                if (Build.VERSION.SDK_INT < 30) {
                    val index = values.indexOf("mediastore")
                    entries = entries.drop(index)
                    values = values.drop(index)
                }
                if (!hasSAFChooser(requireContext().packageManager)) {
                    val index = values.indexOf("saf")
                    entries = entries.drop(index)
                    values = values.drop(index)
                }
                listPreferenceFileAccess.entries = entries.toTypedArray()
                listPreferenceFileAccess.entryValues = values.toTypedArray()
            }
            val listPreferenceLanguageSub = findPreference<ListPreference>("languageSubtitle")
            if (listPreferenceLanguageSub != null) {
                val entries = LinkedHashMap<String, String>()
                entries[Prefs.TRACK_DEFAULT] = getString(R.string.pref_language_track_default)
                entries[Prefs.TRACK_DEVICE] = getString(R.string.pref_language_track_device)
                entries[Prefs.TRACK_NONE] = getString(R.string.pref_language_track_none)
                entries.putAll(languages)
                listPreferenceLanguageSub.entries = entries.values.toTypedArray()
                listPreferenceLanguageSub.entryValues = entries.keys.toTypedArray()
            }
            val listPreferenceLanguageAudio = findPreference<ListPreference>("languageAudio")
            if (listPreferenceLanguageAudio != null) {
                val entries = LinkedHashMap<String, String>()
                entries[Prefs.TRACK_DEFAULT] = getString(R.string.pref_language_track_default)
                entries[Prefs.TRACK_DEVICE] = getString(R.string.pref_language_track_device)
                entries.putAll(languages)
                listPreferenceLanguageAudio.entries = entries.values.toTypedArray()
                listPreferenceLanguageAudio.entryValues = entries.keys.toTypedArray()
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            if (Build.VERSION.SDK_INT >= 29) {
                recyclerView = listView
            }
        }

        // MissingResourceException: Couldn't find 3-letter language code for zz
        val languages: LinkedHashMap<String, String>
             get() {
                val languages = LinkedHashMap<String, String>()
                for (locale in Locale.getAvailableLocales()) {
                    try {
                        // MissingResourceException: Couldn't find 3-letter language code for zz
                        val key = locale.isO3Language
                        var language = locale.displayLanguage
                        val length = language.offsetByCodePoints(0, 1)
                        if (language.isNotEmpty()) {
                            language = language.substring(0, length)
                                .toUpperCase(locale) + language.substring(length)
                        }
                        val value = "$language [$key]"
                        languages[key] = value
                    } catch (e: MissingResourceException) {
                        e.printStackTrace()
                    }
                }
                val collator = Collator.getInstance()
                collator.strength = Collator.PRIMARY
                orderByValue(languages) { s: String?, s1: String? -> collator.compare(s, s1) }
                return languages
            }
    }

    companion object {
        var recyclerView: RecyclerView? = null
    }
}