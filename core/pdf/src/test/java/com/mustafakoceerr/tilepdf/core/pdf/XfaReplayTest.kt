package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.XfaEdit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class XfaReplayTest {

    private class RecordingForm : FillableForm {
        override val pageCount = 2
        val steps = mutableListOf<String>()
        override suspend fun xfaTap(pageIndex: Int, at: PdfPoint): String? {
            steps += "tap $pageIndex ${at.x},${at.y}"
            return ""
        }
        override suspend fun xfaWrite(pageIndex: Int, text: String) {
            steps += "write $pageIndex $text"
        }
    }

    @Test
    fun editsAreTappedAndWrittenInOrder() = runBlocking {
        val form = RecordingForm()

        form.replay(
            listOf(
                XfaEdit(0, PdfPoint(10f, 20f), "Ada"),
                XfaEdit(1, PdfPoint(5f, 6f)),
                XfaEdit(0, PdfPoint(10f, 20f), "Grace"),
            ),
        )

        assertEquals(
            listOf("tap 0 10.0,20.0", "write 0 Ada", "tap 1 5.0,6.0", "tap 0 10.0,20.0", "write 0 Grace"),
            form.steps,
        )
    }

    @Test
    fun aTextEditWhoseTapFocusesNoTextFieldFails() = runBlocking {
        val form = object : FillableForm {
            override val pageCount = 1
            override suspend fun xfaTap(pageIndex: Int, at: PdfPoint): String? = null
        }

        val failure = runCatching { form.replay(listOf(XfaEdit(0, PdfPoint(1f, 1f), "Ada"))) }.exceptionOrNull()

        assertEquals(PdfWriteException::class, failure?.let { it::class })
    }
}
