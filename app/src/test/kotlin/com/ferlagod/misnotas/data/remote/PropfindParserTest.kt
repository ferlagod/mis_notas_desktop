// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Notas — https://github.com/ferlagod/mis_notas_desktop

package com.ferlagod.misnotas.data.remote

import com.ferlagod.misnotas.data.remote.webdav.WebDavClient
import org.junit.Assert.assertEquals
import org.junit.Test

class PropfindParserTest {

    private val xml = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns" xmlns:oc="http://owncloud.org/ns">
         <d:response>
          <d:href>/remote.php/dav/files/ana/MisNotas/</d:href>
          <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getetag>"dir"</d:getetag></d:prop></d:propstat>
         </d:response>
         <d:response>
          <d:href>/remote.php/dav/files/ana/MisNotas/note_1%2B2.json</d:href>
          <d:propstat><d:prop>
            <d:getlastmodified>Wed, 11 Nov 2026 10:00:00 GMT</d:getlastmodified>
            <d:getetag>W/"abc123"</d:getetag>
            <d:resourcetype/>
            <d:getcontentlength>42</d:getcontentlength>
          </d:prop></d:propstat>
         </d:response>
         <d:response>
          <d:href>/remote.php/dav/files/ana/MisNotas/keyinfo.json</d:href>
          <d:propstat><d:prop><d:getetag>"k"</d:getetag><d:resourcetype/></d:prop></d:propstat>
         </d:response>
        </d:multistatus>
    """.trimIndent()

    @Test
    fun parsesFilesAndSkipsFolders() {
        val files = WebDavClient("https://example.com/dav/", "u", "p").parsePropfindResponse(xml)
        assertEquals(listOf("note_1+2.json", "keyinfo.json"), files.map { it.name })
        val note = files.first()
        assertEquals("abc123", note.etag)
        assertEquals(42L, note.contentLength)
        assertEquals(1794391200000L, note.lastModified)
    }

    @Test(expected = Exception::class)
    fun rejectsDoctype() {
        WebDavClient("https://example.com/dav/", "u", "p")
            .parsePropfindResponse("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><x>&e;</x>""")
    }
}
