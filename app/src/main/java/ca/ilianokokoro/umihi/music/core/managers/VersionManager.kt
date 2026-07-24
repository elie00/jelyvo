package ca.ilianokokoro.umihi.music.core.managers

import android.content.Context


object VersionManager {

    private var versionName: String? = null

    fun initialize(context: Context) {
        if (versionName == null) {
            versionName = try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName
            } catch (_: Exception) {
                String()
            }
        }
    }

    fun getVersionName(): String {
        return versionName.toString()
    }
}
