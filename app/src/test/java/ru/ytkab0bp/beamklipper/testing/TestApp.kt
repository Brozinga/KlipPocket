package ru.ytkab0bp.beamklipper.testing

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.db.BeamDB
import ru.ytkab0bp.beamklipper.utils.Prefs

// A plain Application for Robolectric tests. KlipperApp.onCreate starts Python,
// USB and notifications, so tests wire a KlipperApp to the same base context
// instead and set up only what the code under test reads.
class TestApp : Application()

object RobolectricSupport {
    fun context(): Application = ApplicationProvider.getApplicationContext()

    fun installKlipperApp(): KlipperApp {
        val app = KlipperApp()
        val attach = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attach.isAccessible = true
        attach.invoke(app, context())
        KlipperApp.INSTANCE = app
        return app
    }

    // App, real SharedPreferences, a real (Robolectric) SQLite database, no
    // instances registered and the bundle install already "done".
    fun setUpApp(): BeamDB {
        val app = installKlipperApp()
        Prefs.init(app)
        app.getSharedPreferences("${app.packageName}_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        val db = BeamDB(app)
        KlipperApp.DATABASE = db
        resetInstanceRegistry()
        completeBundleInstall()
        return db
    }

    // KlipperInstance keeps its registry in static fields.
    fun resetInstanceRegistry() {
        KlipperInstance.resetSlotsForFreshStart()
        val cls = KlipperInstance::class.java
        cls.getDeclaredField("instances").apply { isAccessible = true }.set(null, emptyList<KlipperInstance>())
        (cls.getDeclaredField("instanceMap").apply { isAccessible = true }.get(null) as MutableMap<*, *>).clear()
    }

    fun completeBundleInstall() {
        KlipperApp::class.java.getDeclaredField("_bundleInstallJob").apply { isAccessible = true }
            .set(null, CompletableDeferred(Unit))
    }
}
