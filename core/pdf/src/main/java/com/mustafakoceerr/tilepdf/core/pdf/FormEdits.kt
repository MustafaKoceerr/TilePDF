package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.model.FORM_VALUE_OFF
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.FormFieldKind

/** One change to one widget, as the engine makes it. */
sealed interface FormEdit {
    val pageIndex: Int
    val annotIndex: Int

    data class SetText(override val pageIndex: Int, override val annotIndex: Int, val text: String) : FormEdit
    data class SetChecked(override val pageIndex: Int, override val annotIndex: Int, val checked: Boolean) : FormEdit
    data class SetChoice(override val pageIndex: Int, override val annotIndex: Int, val optionIndex: Int) : FormEdit
}

/**
 * The widget changes that make [fields] hold [values] (field name → `/V`).
 * Fields that already match, read-only fields, unknown names and options
 * the field doesn't offer are left alone.
 */
fun formEdits(fields: List<FormField>, values: Map<String, String>): List<FormEdit> =
    values.flatMap { (name, value) ->
        fields.filter { it.name == name && !it.readOnly }.mapNotNull { widget -> widget.editFor(value) }
    }

private fun FormField.editFor(wanted: String): FormEdit? = when (kind) {
    FormFieldKind.Text -> FormEdit.SetText(pageIndex, annotIndex, wanted).takeIf { value != wanted }
    FormFieldKind.CheckBox -> {
        // A widget without an export value is on for any value but Off.
        val on = wanted != FORM_VALUE_OFF && (exportValue.isEmpty() || exportValue == wanted)
        FormEdit.SetChecked(pageIndex, annotIndex, on).takeIf { checked != on }
    }
    FormFieldKind.RadioButton -> when {
        // Choosing a button turns the group's other one off by itself.
        wanted == FORM_VALUE_OFF -> FormEdit.SetChecked(pageIndex, annotIndex, false).takeIf { checked }
        exportValue == wanted -> FormEdit.SetChecked(pageIndex, annotIndex, true).takeIf { !checked }
        else -> null
    }
    FormFieldKind.ComboBox, FormFieldKind.ListBox -> {
        val index = options.indexOf(wanted)
        FormEdit.SetChoice(pageIndex, annotIndex, index).takeIf { index >= 0 && value != wanted }
    }
}
