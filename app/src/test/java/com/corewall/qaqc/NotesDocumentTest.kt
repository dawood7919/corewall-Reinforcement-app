package com.corewall.qaqc

import com.corewall.qaqc.data.db.NoteEntity
import com.corewall.qaqc.notes.NotesBlock
import com.corewall.qaqc.notes.NotesDocument
import com.corewall.qaqc.notes.NotesDocumentCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesDocumentTest {
    private fun legacy(body: String) = NoteEntity(
        elementId = "FLOOR_NOTE", level = "GROUND", body = body,
        createdAt = 1L, updatedAt = 1L
    )

    @Test fun migratesLegacyTextChecklistImageAndAudioToIndependentBlocks() {
        val document = NotesDocumentCodec.decode(
            legacy("ملاحظة للموقع\n- [x] فحص القاعدة\n![واجهة](/data/site.jpg)\n[[audio:/data/voice.m4a]]")
        )
        assertEquals(4, document.blocks.size)
        assertEquals(NotesBlock.TEXT, document.blocks[0].type)
        assertTrue(document.blocks[1].checked)
        assertEquals(NotesBlock.IMAGE, document.blocks[2].type)
        assertEquals(NotesBlock.AUDIO, document.blocks[3].type)
    }

    @Test fun encodesDocumentAndProducesSearchSummaryAndMediaIndex() {
        val source = NotesDocument(blocks = listOf(
            NotesBlock.text("Beam B3 – 4T32"),
            NotesBlock.checklist("فحص الوصلات", true),
            NotesBlock.image("/data/photo.jpg"),
            NotesBlock.audio("/data/voice.m4a", 24_000)
        ))
        val restored = NotesDocumentCodec.decode(legacy("").copy(documentJson = NotesDocumentCodec.encode(source)))
        assertEquals(source.blocks.map { it.type }, restored.blocks.map { it.type })
        assertTrue(NotesDocumentCodec.summary(restored).contains("Beam B3"))
        assertEquals(listOf("/data/photo.jpg", "/data/voice.m4a"), NotesDocumentCodec.mediaPaths(restored))
    }
}
