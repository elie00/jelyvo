package ca.ilianokokoro.umihi.music.core.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** Jelyvo speaks French, whatever language the phone is set to. */
object AppLocale {
    /** French in the phone's region: the region still decides what music YouTube offers. */
    fun frenchFor(device: Locale): Locale =
        Locale.Builder().setLanguage("fr").setRegion(device.country).build()

    /**
     * A context whose resources read in French. Also makes French the default locale, since that is
     * what YouTube requests, dates and numbers go by. The override sets the locale alone, so the
     * context keeps following the screen as it rotates or resizes.
     */
    fun wrap(base: Context): Context {
        val french = frenchFor(base.resources.configuration.locales[0])
        Locale.setDefault(french)
        val override = Configuration().apply { setLocales(LocaleList(french)) }
        return base.createConfigurationContext(override)
    }
}
