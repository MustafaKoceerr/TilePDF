package com.mustafakoceerr.tilepdf.model

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect

/** The value of a check box or radio group with nothing chosen, as PDF writes it. */
const val FORM_VALUE_OFF = "Off"

enum class FormFieldKind { Text, CheckBox, RadioButton, ComboBox, ListBox }

/**
 * One widget of an AcroForm field. A field shown more than once, like a
 * radio group, has a widget per place, all with the same [name].
 *
 * [value] is the field's `/V`: the text, the chosen option's label, or for a
 * check box or radio group the export value of the checked widget ([FORM_VALUE_OFF]
 * when none is).
 */
data class FormField(
    val pageIndex: Int,
    /** The widget's index among the page's annotations, as the engine counts them. */
    val annotIndex: Int,
    val name: String,
    val kind: FormFieldKind,
    val value: String,
    /** Top-left points. */
    val rect: PdfRect,
    /** Labels of a combo or list box's options. */
    val options: List<String> = emptyList(),
    /** Whether this check box or radio button widget is on. */
    val checked: Boolean = false,
    /** The value this check box or radio button gives the field when on. */
    val exportValue: String = "",
    val readOnly: Boolean = false,
    val multiline: Boolean = false,
)
