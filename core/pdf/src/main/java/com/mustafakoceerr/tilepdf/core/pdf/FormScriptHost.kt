package com.mustafakoceerr.tilepdf.core.pdf

/**
 * What a document's form scripts ask of the person reading it. PDFium calls
 * these on the PDF thread and waits for the answer, so a call may block.
 */
interface FormScriptHost {

    /** `app.alert`: shows [message] with the [buttons] (a `BUTTONS_*` value); returns a `RETURN_*` value. */
    fun alert(message: String, title: String, buttons: Int, icon: Int): Int

    /** `app.response`: asks [question]; returns the answer, or null when cancelled. */
    fun response(question: String, title: String, default: String, label: String, password: Boolean): String?

    /** `this.print()`. */
    fun print()

    /** `this.pageNum = …`: shows page [pageIndex]. */
    fun goToPage(pageIndex: Int)

    companion object {
        // PDFium's JSPLATFORM_ALERT_BUTTON_* and JSPLATFORM_ALERT_RETURN_*.
        const val BUTTONS_OK = 0
        const val BUTTONS_OK_CANCEL = 1
        const val BUTTONS_YES_NO = 2
        const val BUTTONS_YES_NO_CANCEL = 3

        const val RETURN_OK = 1
        const val RETURN_CANCEL = 2
        const val RETURN_NO = 3
        const val RETURN_YES = 4

        /** The answer when no one can be asked: go ahead, as if the first button was pressed. */
        fun defaultAlertAnswer(buttons: Int): Int =
            if (buttons == BUTTONS_YES_NO || buttons == BUTTONS_YES_NO_CANCEL) RETURN_YES else RETURN_OK
    }
}
