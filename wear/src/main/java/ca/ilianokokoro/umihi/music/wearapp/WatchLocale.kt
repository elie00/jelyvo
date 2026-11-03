package ca.ilianokokoro.umihi.music.wearapp

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** Jelyvo speaks French, whatever language the watch is set to. */
object WatchLocale {
    /** French in the watch's region: the region still decides what music YouTube offers. */
    fun frenchFor(device: Locale): Locale =
        Locale.Builder().setLanguage("fr").setRegion(device.country).build()

    /** A context whose resources read in French, with French as the default locale for requests. */
    fun wrap(base: Context): Context {
        val french = frenchFor(base.resources.configuration.locales[0])
        Locale.setDefault(french)
        val override = Configuration().apply { setLocales(LocaleList(french)) }
        return base.createConfigurationContext(override)
    }
}
