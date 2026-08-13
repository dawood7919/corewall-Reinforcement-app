package com.corewall.qaqc

import com.corewall.qaqc.ui.notes.NoteBlock
import com.corewall.qaqc.ui.notes.countAudio
import com.corewall.qaqc.ui.notes.notePreview
import com.corewall.qaqc.ui.notes.parseNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMarkdownTest {
    @Test
    fun `keeps image and audio attachments as ordered inline blocks`() {
        val markdown = """
            فحص حائط الدور الثالث
            ![صورة شدة](/tmp/site-photo.jpg)
            [[audio:/tmp/voice-note.m4a]]
            [[file:/tmp/report.pdf]]
        """.trimIndent()

        val blocks = parseNote(markdown)
        assertTrue(blocks[0] is NoteBlock.Paragraph)
        assertTrue(blocks[1] is NoteBlock.Image)
        assertTrue(blocks[2] is NoteBlock.Audio)
        assertTrue(blocks[3] is NoteBlock.FileCard)
        assertEquals(1, countAudio(markdown))
        assertEquals("فحص حائط الدور الثالث", notePreview(markdown))
    }
}
