// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.sync

import com.ferlagod.misnotas.data.local.entity.FolderEntity
import com.ferlagod.misnotas.data.local.entity.NoteEntity
import com.ferlagod.misnotas.data.local.model.SyncStatus
import com.ferlagod.misnotas.security.CryptoManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RemoteCodecTest {

    private val key = CryptoManager.deriveKey("SyncTestMasterPassword2026", CryptoManager.generateSalt())

    private val note = NoteEntity(
        id = "11111111-2222-3333-4444-555555555555",
        folderId = "folder-1",
        title = "Mi Título Secreto",
        content = "# Hola\n- [ ] tarea\n![img](attachment://abc)",
        isPinned = true,
        color = "BLUE",
        updatedAt = 1729999999000L,
        isDeleted = true,
        syncStatus = SyncStatus.PENDING_UPLOAD
    )

    @Test
    fun noteRoundTrip() {
        val raw = RemoteCodec.encodeNote(note, key)
        val record = RemoteCodec.decodeNote(raw, note.id, key) as RemoteRecord.Live
        assertEquals(note.copy(syncStatus = SyncStatus.SYNCED), record.entity)
    }

    @Test
    fun noteFileHidesAllMetadata() {
        val raw = RemoteCodec.encodeNote(note, key)
        listOf("Secreto", "folder-1", "BLUE", "1729999999000", "isPinned", "tarea").forEach {
            assertFalse("El fichero remoto no debe contener '$it'", raw.contains(it))
        }
    }

    @Test
    fun payloadIsBoundToItsId() {
        // Un servidor malicioso que copie el payload de una nota a otro fichero no debe poder colarlo.
        val raw = JSONObject(RemoteCodec.encodeNote(note, key))
        raw.put("id", "otro-id")
        try {
            RemoteCodec.decodeNote(raw.toString(), "otro-id", key)
            fail("Debería fallar el descifrado por AAD")
        } catch (_: Exception) {
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun fileNameMustMatchId() {
        RemoteCodec.decodeNote(RemoteCodec.encodeNote(note, key), "otro-id", key)
    }

    @Test
    fun wrongKeyFails() {
        val other = CryptoManager.deriveKey("otra", CryptoManager.generateSalt())
        try {
            RemoteCodec.decodeNote(RemoteCodec.encodeNote(note, key), note.id, other)
            fail("Debería fallar con otra clave")
        } catch (_: Exception) {
        }
    }

    @Test
    fun tombstoneRoundTrip() {
        val raw = RemoteCodec.encodeTombstone(RemoteCodec.TYPE_NOTE, note.id, 42L, key)
        val record = RemoteCodec.decodeNote(raw, note.id, key)
        assertEquals(RemoteRecord.Tombstone(note.id, 42L, verified = true), record)
        assertTrue(RemoteCodec.isTombstone(raw))
        assertFalse(RemoteCodec.isTombstone(RemoteCodec.encodeNote(note, key)))
    }

    @Test
    fun legacyTombstoneWithoutProofIsUnverified() {
        val raw = """{"format":"misnotas","version":2,"type":"note","id":"${note.id}","deleted":true,"deletedAt":7}"""
        assertEquals(RemoteRecord.Tombstone(note.id, 7L, verified = false), RemoteCodec.decodeNote(raw, note.id, key))
    }

    @Test
    fun forgedOrForeignTombstoneIsRejected() {
        val other = CryptoManager.deriveKey("otra", CryptoManager.generateSalt())
        val foreign = RemoteCodec.encodeTombstone(RemoteCodec.TYPE_NOTE, note.id, 42L, other)
        try {
            RemoteCodec.decodeNote(foreign, note.id, key)
            fail("Una lápida de otra clave no debe aceptarse")
        } catch (_: java.security.GeneralSecurityException) {
        }
        // Prueba válida pero copiada a otra nota: el AAD lo impide.
        val moved = RemoteCodec.encodeTombstone(RemoteCodec.TYPE_NOTE, note.id, 42L, key).replace(note.id, "otra-nota")
        try {
            RemoteCodec.decodeNote(moved, "otra-nota", key)
            fail("Una lápida movida a otra nota no debe aceptarse")
        } catch (_: java.security.GeneralSecurityException) {
        }
    }

    @Test
    fun folderRoundTrip() {
        val folder = FolderEntity(id = "f1", parentId = "p1", name = "Trabajo", color = "INDIGO", updatedAt = 5L)
        val record = RemoteCodec.decodeFolder(RemoteCodec.encodeFolder(folder, key), "f1", key) as RemoteRecord.Live
        assertEquals(folder.copy(syncStatus = SyncStatus.SYNCED), record.entity)
    }

    @Test
    fun readsLegacyV1Note() {
        val legacy = JSONObject().apply {
            put("id", note.id)
            put("folderId", JSONObject.NULL)
            put("title", CryptoManager.encrypt("Antigua", key))
            put("content", CryptoManager.encrypt("Texto v1", key))
            put("isPinned", false)
            put("color", "DEFAULT")
            put("updatedAt", 10L)
            put("isDeleted", false)
        }.toString()
        val record = RemoteCodec.decodeNote(legacy, note.id, key) as RemoteRecord.Live
        assertEquals("Antigua", record.entity.title)
        assertEquals("Texto v1", record.entity.content)
        assertEquals(null, record.entity.folderId)
    }

    @Test
    fun attachmentRoundTripIsBoundToId() {
        val bytes = ByteArray(1000) { it.toByte() }
        val enc = RemoteCodec.encryptAttachment(bytes, "a1", key)
        assertTrue(bytes.contentEquals(RemoteCodec.decryptAttachment(enc, "a1", key)))
        try {
            RemoteCodec.decryptAttachment(enc, "a2", key)
            fail("AAD distinto debe fallar")
        } catch (_: Exception) {
        }
    }

    @Test
    fun keyInfoVerifiesPassword() {
        val salt = CryptoManager.generateSalt()
        val k = CryptoManager.deriveKey("correcta", salt, 20_000)
        val info = KeyInfo.parse(KeyInfo.create(salt, 20_000, k).toJson())
        assertTrue(info.verify(CryptoManager.deriveKey("correcta", info.salt, info.iterations)))
        assertFalse(info.verify(CryptoManager.deriveKey("incorrecta", info.salt, info.iterations)))
    }
}
