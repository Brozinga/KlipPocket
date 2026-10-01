package ru.ytkab0bp.beamklipper.provider

import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
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

// The SAF provider that exposes each printer profile's public folder (config,
// gcodes, timelapses) to a file manager.
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class InstanceFilesProviderTest {
    private lateinit var provider: InstanceFilesProvider
    private lateinit var instance: KlipperInstance

    @Before
    fun setUp() {
        RobolectricSupport.setUpApp()
        instance = KlipperInstance().apply { id = "abc"; name = "Neptune"; icon = InstanceIcon.PRINTER }
        KlipperApp.DATABASE.insert(instance)
        File(instance.publicDirectory, "config").mkdirs()
        File(instance.publicDirectory, "config/printer.cfg").writeText("[mcu]")
        File(instance.publicDirectory, "gcodes/cube.gcode").apply { parentFile.mkdirs(); writeText("G1") }

        provider = InstanceFilesProvider()
        provider.onCreate()
    }

    @Test
    fun `each running instance gets a root named after it`() {
        val c = provider.queryRoots(null)
        assertTrue(c.moveToFirst())
        val idIndex = c.getColumnIndex(DocumentsContract.Root.COLUMN_ROOT_ID)
        assertEquals("abc", c.getString(idIndex))
        val docIndex = c.getColumnIndex(DocumentsContract.Root.COLUMN_DOCUMENT_ID)
        assertEquals("instance:abc:", c.getString(docIndex))
    }

    @Test
    fun `the root document lists its children`() {
        val c: android.database.Cursor = provider.queryChildDocuments("instance:abc:", null, null as String?)
        val names = mutableListOf<String>()
        val nameIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        while (c.moveToNext()) names += c.getString(nameIndex)
        assertEquals(setOf("config", "gcodes"), names.toSet())
    }

    @Test
    fun `a subfolder lists the files it contains`() {
        val c: android.database.Cursor = provider.queryChildDocuments("instance:abc:gcodes", null, null as String?)
        assertTrue(c.moveToFirst())
        val nameIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        assertEquals("cube.gcode", c.getString(nameIndex))
    }

    @Test
    fun `a single document can be queried by its id`() {
        val c = provider.queryDocument("instance:abc:config/printer.cfg", null)
        assertTrue(c.moveToFirst())
        val nameIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        assertEquals("printer.cfg", c.getString(nameIndex))
        val sizeIndex = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        assertEquals(File(instance.publicDirectory, "config/printer.cfg").length(), c.getLong(sizeIndex))
    }

    @Test
    fun `a document can be opened for reading`() {
        val pfd = provider.openDocument("instance:abc:config/printer.cfg", "r", null)
        val text = java.io.FileInputStream(pfd.fileDescriptor).bufferedReader().readText()
        assertEquals("[mcu]", text)
        pfd.close()
    }

    @Test
    fun `a new document can be created inside a folder`() {
        val id = provider.createDocument("instance:abc:config", "text/plain", "notes.txt")
        assertEquals("instance:abc:config/notes.txt", id)
        assertTrue(File(instance.publicDirectory, "config/notes.txt").exists())
    }

    @Test
    fun `a document can be deleted`() {
        val f = File(instance.publicDirectory, "config/to_delete.cfg").apply { writeText("x") }
        provider.deleteDocument("instance:abc:config/to_delete.cfg")
        assertTrue(!f.exists())
    }

    @Test(expected = java.io.FileNotFoundException::class)
    fun `deleting a document that does not exist fails`() {
        provider.deleteDocument("instance:abc:config/missing.cfg")
    }

    @Test
    fun `a directory is flagged as creatable when writable`() {
        val c = provider.queryDocument("instance:abc:config", null)
        assertTrue(c.moveToFirst())
        val flags = c.getInt(c.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS))
        assertEquals(DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE, flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE)
    }
}
