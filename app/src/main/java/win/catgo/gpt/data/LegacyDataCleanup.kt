package win.catgo.gpt.data

import android.content.Context

/** Remove only the retired SSH namespaces; never delete the shared chat encryption key. */
internal object LegacyDataCleanup {
    fun removeSshData(context: Context) {
        context.getSharedPreferences("catgo_ssh_settings", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("catgo_ssh_credentials", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
