package com.mustafakoceerr.tilepdf.core.pdf

import androidx.annotation.Keep
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.FormFieldKind

/**
 * One fillable widget as PDFium reports it, in top-left points. Built by
 * native code, hence [Keep].
 *
 * @param annotIndex its position among the page's annotations.
 * @param type PDFium's `FPDF_FORMFIELD_*`.
 * @param flags the field's `/Ff`.
 * @param rect left, top, right, bottom.
 */
@Keep
internal class NativeFormField(
    @JvmField val annotIndex: Int,
    @JvmField val type: Int,
    @JvmField val name: String,
    @JvmField val value: String,
    @JvmField val flags: Int,
    @JvmField val rect: FloatArray,
    @JvmField val options: Array<String>,
    @JvmField val checked: Boolean,
    @JvmField val exportValue: String,
)

/** PDFium's `FPDF_FORMFIELD_*` types TilePDF fills. */
internal object FormFieldType {
    const val CHECK_BOX = 2
    const val RADIO_BUTTON = 3
    const val COMBO_BOX = 4
    const val LIST_BOX = 5
    const val TEXT = 6
}

private const val FLAG_READ_ONLY = 1 shl 0
private const val FLAG_MULTILINE = 1 shl 12

/** The widget as a [FormField] on [pageIndex]; null for kinds TilePDF doesn't fill. */
internal fun NativeFormField.toFormField(pageIndex: Int): FormField? {
    val kind = when (type) {
        FormFieldType.TEXT -> FormFieldKind.Text
        FormFieldType.CHECK_BOX -> FormFieldKind.CheckBox
        FormFieldType.RADIO_BUTTON -> FormFieldKind.RadioButton
        FormFieldType.COMBO_BOX -> FormFieldKind.ComboBox
        FormFieldType.LIST_BOX -> FormFieldKind.ListBox
        else -> return null
    }
    return FormField(
        pageIndex = pageIndex,
        annotIndex = annotIndex,
        name = name,
        kind = kind,
        value = value,
        rect = PdfRect(rect[0], rect[1], rect[2], rect[3]),
        options = options.toList(),
        checked = checked,
        exportValue = exportValue,
        readOnly = flags and FLAG_READ_ONLY != 0,
        multiline = flags and FLAG_MULTILINE != 0,
    )
}
