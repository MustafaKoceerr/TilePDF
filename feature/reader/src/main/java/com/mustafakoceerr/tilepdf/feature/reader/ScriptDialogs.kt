package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.pdf.FormScriptHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CompletableFuture

/** The English title PDFium gives an alert without one; the reader words its own. */
private const val PDFIUM_ALERT_TITLE = "Alert"

/** A dialog a form script asked for. */
sealed interface ScriptDialog {
    /** [buttons] is a [FormScriptHost] `BUTTONS_*` value. */
    data class Alert(val message: String, val title: String, val buttons: Int) : ScriptDialog

    data class Prompt(val question: String, val title: String, val default: String, val label: String, val password: Boolean) : ScriptDialog
}

sealed interface ScriptAnswer {
    /** A [FormScriptHost] `RETURN_*` value. */
    data class Button(val code: Int) : ScriptAnswer

    /** The typed text; null when cancelled. */
    data class Text(val text: String?) : ScriptAnswer
}

/**
 * The reader's side of form scripts: a script's dialog shows in [dialog]
 * while PDFium's thread waits for [answer]. After [close], waiting and later
 * calls get the default answer, so a closed reader never holds PDFium up.
 */
internal class ScriptDialogs(
    private val onPrint: () -> Unit,
    private val onGoToPage: (pageIndex: Int) -> Unit,
) : FormScriptHost {

    private val shown = MutableStateFlow<ScriptDialog?>(null)
    val dialog: StateFlow<ScriptDialog?> = shown.asStateFlow()

    private val lock = Any()
    private var pending: CompletableFuture<ScriptAnswer?>? = null
    private var closed = false

    override fun alert(message: String, title: String, buttons: Int, icon: Int): Int =
        (ask(ScriptDialog.Alert(message, title.takeUnless { it == PDFIUM_ALERT_TITLE }.orEmpty(), buttons)) as? ScriptAnswer.Button)?.code
            ?: FormScriptHost.defaultAlertAnswer(buttons)

    override fun response(question: String, title: String, default: String, label: String, password: Boolean): String? =
        (ask(ScriptDialog.Prompt(question, title, default, label, password)) as? ScriptAnswer.Text)?.text

    override fun print() = onPrint()

    override fun goToPage(pageIndex: Int) = onGoToPage(pageIndex)

    fun answer(answer: ScriptAnswer) {
        synchronized(lock) { pending?.complete(answer) }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            pending?.complete(null)
        }
        shown.value = null
    }

    /** Shows [dialog] and waits for its answer; null when closed meanwhile. */
    private fun ask(dialog: ScriptDialog): ScriptAnswer? {
        val answer = synchronized(lock) {
            if (closed) return null
            CompletableFuture<ScriptAnswer?>().also { pending = it }
        }
        shown.value = dialog
        try {
            return answer.get()
        } finally {
            synchronized(lock) { if (pending === answer) pending = null }
            shown.compareAndSet(dialog, null)
        }
    }
}
