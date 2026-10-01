package ru.ytkab0bp.beamklipper.db

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ytkab0bp.beamklipper.InstanceIcon
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.testing.RobolectricSupport
import ru.ytkab0bp.beamklipper.testing.TestApp
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class BeamDBTest {
    private lateinit var db: BeamDB

    @Before
    fun setUp() {
        db = RobolectricSupport.setUpApp()
    }

    @After
    fun tearDown() = db.close()

    private fun instance(id: String, name: String, icon: InstanceIcon = InstanceIcon.PRINTER) =
        KlipperInstance().also { it.id = id; it.name = name; it.icon = icon }

    private fun rawInsert(id: String?, name: String?, icon: String?, autostart: Any?) {
        db.writableDatabase.execSQL(
            "INSERT OR REPLACE INTO instances (id, name, icon, autostart) VALUES (?, ?, ?, ?)",
            arrayOf(id, name, icon, autostart)
        )
    }

    @Test
    fun `a new database has no instances`() {
        assertTrue(db.getInstances().isEmpty())
    }

    @Test
    fun `an inserted instance is read back with its fields`() {
        db.insert(instance("a1", "Neptune 3 Pro", InstanceIcon.ROBOT))
        val stored = db.getInstances().single()
        assertEquals("a1", stored.id)
        assertEquals("Neptune 3 Pro", stored.name)
        assertEquals(InstanceIcon.ROBOT, stored.icon)
        assertFalse(stored.autostart)
    }

    @Test
    fun `inserting the same id again replaces the row`() {
        db.insert(instance("a1", "First"))
        db.insert(instance("a1", "Second"))
        assertEquals(listOf("Second"), db.getInstances().map { it.name })
    }

    @Test
    fun `several instances are kept`() {
        db.insert(instance("a1", "One"))
        db.insert(instance("a2", "Two"))
        assertEquals(setOf("One", "Two"), db.getInstances().map { it.name }.toSet())
    }

    @Test
    fun `an update changes the name and icon`() {
        db.insert(instance("a1", "Old"))
        val stored = db.getInstances().single()
        stored.name = "New"
        stored.icon = InstanceIcon.MOON
        db.update(stored)
        val after = db.getInstances().single()
        assertEquals("New", after.name)
        assertEquals(InstanceIcon.MOON, after.icon)
    }

    @Test
    fun `delete removes the row and the instance's files`() {
        db.insert(instance("a1", "Gone"))
        val stored = db.getInstances().single()
        File(stored.directory, "public/config").mkdirs()
        File(stored.directory, "public/config/printer.cfg").writeText("[mcu]")
        db.delete(stored)
        assertTrue(db.getInstances().isEmpty())
        assertFalse(stored.directory.exists())
    }

    @Test
    fun `the autostart flag is stored as 0 or 1`() {
        rawInsert("x1", "On", "PRINTER", 1)
        rawInsert("x2", "Off", "PRINTER", 0)
        val byName = db.getInstances().associateBy { it.name }
        assertTrue(byName.getValue("On").autostart)
        assertFalse(byName.getValue("Off").autostart)
    }

    @Test
    fun `an unknown icon reads as the printer icon`() {
        rawInsert("x1", "Old", "REMOVED_ICON", 0)
        assertEquals(InstanceIcon.PRINTER, db.getInstances().single().icon)
    }

    @Test
    fun `rows without a name or icon are skipped`() {
        rawInsert("x1", null, "PRINTER", 0)
        rawInsert("x2", "NoIcon", null, 0)
        rawInsert("x3", "Fine", "PRINTER", 0)
        assertEquals(listOf("Fine"), db.getInstances().map { it.name })
    }

    @Test
    fun `an instance saved through the db is the one the registry hands out`() {
        db.insert(instance("a1", "Registry"))
        assertEquals("Registry", KlipperInstance.getInstance("a1")!!.name)
        assertEquals(listOf("Registry"), KlipperInstance.getInstances().map { it.name })
    }

    @Test
    fun `upgrading from an old schema keeps one row per id`() {
        val w = db.writableDatabase
        w.execSQL("DROP TABLE instances")
        w.execSQL("CREATE TABLE instances (id TEXT, name TEXT, icon TEXT, autostart INTEGER)")
        w.execSQL("INSERT INTO instances VALUES ('a', 'first', 'PRINTER', 0)")
        w.execSQL("INSERT INTO instances VALUES ('a', 'duplicate', 'PRINTER', 0)")
        w.execSQL("INSERT INTO instances VALUES ('b', 'other', 'HOME', 1)")
        db.onUpgrade(w, 3, 4)
        val rows = db.getInstances()
        assertEquals(setOf("a", "b"), rows.map { it.id }.toSet())
        assertEquals(2, rows.size)
        // and the new schema enforces uniqueness
        rawInsert("a", "again", "PRINTER", 0)
        assertEquals(2, db.getInstances().size)
    }

    @Test
    fun `upgrading from the current schema changes nothing`() {
        db.insert(instance("a1", "Stay"))
        db.onUpgrade(db.writableDatabase, 4, 4)
        assertEquals(listOf("Stay"), db.getInstances().map { it.name })
    }

    @Test
    fun `the database is reachable through the app in the main process`() {
        assertNotNull(KlipperApp.getDatabaseOrNull())
        assertNull(null)
    }
}
