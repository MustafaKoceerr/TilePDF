package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.pdf.FormScriptHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class ScriptDialogsTest {

    private val printed = mutableListOf<Unit>()
    private val pages = mutableListOf<Int>()
    private val dialogs = ScriptDialogs(onPrint = { printed += Unit }, onGoToPage = { pages += it })
    private val pdfThread = Executors.newSingleThreadExecutor()

    /** Runs [call] on another thread, as PDFium does, and waits until it shows its dialog. */
    private fun <T> asking(call: () -> T): Future<T> {
        val future = pdfThread.submit(call)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (dialogs.dialog.value == null && !future.isDone) {
            check(System.nanoTime() < deadline) { "No dialog shown" }
            Thread.sleep(5)
        }
        return future
    }

    @Test
    fun alertWaitsForTheAnswer() {
        val result = asking { dialogs.alert("Too old", "Form", FormScriptHost.BUTTONS_YES_NO, 0) }

        assertEquals(ScriptDialog.Alert("Too old", "Form", FormScriptHost.BUTTONS_YES_NO), dialogs.dialog.value)
        dialogs.answer(ScriptAnswer.Button(FormScriptHost.RETURN_NO))

        assertEquals(FormScriptHost.RETURN_NO, result.get(5, TimeUnit.SECONDS))
        assertNull(dialogs.dialog.value)
    }

    @Test
    fun promptReturnsTheTextOrNullWhenCancelled() {
        val typed = asking { dialogs.response("Name?", "", "Ada", "", password = false) }
        assertEquals(ScriptDialog.Prompt("Name?", "", "Ada", "", false), dialogs.dialog.value)
        dialogs.answer(ScriptAnswer.Text("Grace"))
        assertEquals("Grace", typed.get(5, TimeUnit.SECONDS))

        val cancelled = asking { dialogs.response("Name?", "", "", "", password = false) }
        dialogs.answer(ScriptAnswer.Text(null))
        assertNull(cancelled.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun closingReleasesAWaitingCallWithTheDefault() {
        val result = asking { dialogs.alert("Sure?", "", FormScriptHost.BUTTONS_OK_CANCEL, 0) }

        dialogs.close()

        assertEquals(FormScriptHost.RETURN_OK, result.get(5, TimeUnit.SECONDS))
        assertNull(dialogs.dialog.value)
    }

    @Test
    fun afterClosingCallsReturnTheDefaultAtOnce() {
        dialogs.close()

        assertEquals(FormScriptHost.RETURN_YES, dialogs.alert("Go on?", "", FormScriptHost.BUTTONS_YES_NO_CANCEL, 0))
        assertNull(dialogs.response("Name?", "", "", "", password = false))
        assertNull(dialogs.dialog.value)
    }

    @Test
    fun printAndPageJumpsArePassedOn() {
        dialogs.print()
        dialogs.goToPage(3)

        assertEquals(1, printed.size)
        assertEquals(listOf(3), pages)
    }

    @Test
    fun pdfiumsOwnAlertTitleIsLeftForTheReaderToWord() {
        val result = asking { dialogs.alert("Too old", "Alert", FormScriptHost.BUTTONS_OK, 0) }

        assertEquals(ScriptDialog.Alert("Too old", "", FormScriptHost.BUTTONS_OK), dialogs.dialog.value)
        dialogs.answer(ScriptAnswer.Button(FormScriptHost.RETURN_OK))
        result.get(5, TimeUnit.SECONDS)
    }
}
