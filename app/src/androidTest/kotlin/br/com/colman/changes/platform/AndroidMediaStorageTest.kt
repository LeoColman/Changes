// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Seção 3.1: mídia só no armazenamento interno privado, com `.nomedia`, sem escapar da raiz. */
@RunWith(AndroidJUnit4::class)
class AndroidMediaStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.filesDir, "media-test")
    private val storage = AndroidMediaStorage(root, Dispatchers.IO)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun mediaLivesInPrivateStorageAndIsHiddenFromIndexers() {
        assertTrue(root.canonicalPath.startsWith(context.filesDir.canonicalPath))
        assertTrue(File(root, ".nomedia").isFile)
        assertTrue(storage.listAll().isEmpty())
    }

    @Test
    fun writesAtomicallyAndReturnsTheSha256() = runBlocking {
        val path = "0f8fad5b-d9cb-469f-a165-70867728950e.jpg"
        val checksum = storage.write(path, "abc".byteInputStream())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", checksum)
        assertEquals("abc", storage.openRead(path)!!.use { it.readBytes().decodeToString() })
        assertEquals(listOf(path), storage.listAll())
        assertFalse(File(root, "$path.part").exists())
    }

    @Test
    fun movesAndDeletes() = runBlocking {
        storage.write(".import-1/a.jpg", "x".byteInputStream())
        storage.move(".import-1/a.jpg", "a.jpg")
        assertTrue(storage.exists("a.jpg"))
        assertFalse(storage.exists(".import-1/a.jpg"))
        assertTrue(storage.delete("a.jpg"))
        assertFalse(storage.exists("a.jpg"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPathsThatEscapeTheRoot() {
        storage.exists("../databases/changes.db")
    }
}
