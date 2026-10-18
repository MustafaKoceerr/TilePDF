package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.FORM_VALUE_OFF
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.FormFieldKind

/** The editable field under [point]; where fields overlap, the smallest, which is drawn on top. */
internal fun formFieldAt(fields: List<FormField>, point: PdfPoint): FormField? =
    fields.filter { !it.readOnly && point.x in it.rect.left..it.rect.right && point.y in it.rect.top..it.rect.bottom }
        .minByOrNull { it.rect.width * it.rect.height }

/**
 * The value a tap gives a check box (toggled) or radio button (chosen), or
 * null when the tap changes nothing or the field needs a dialog instead.
 */
internal fun tapValue(field: FormField): String? = when (field.kind) {
    FormFieldKind.CheckBox -> if (field.checked) FORM_VALUE_OFF else field.exportValue.ifEmpty { DEFAULT_ON_VALUE }
    FormFieldKind.RadioButton -> field.exportValue.takeIf { !field.checked && it.isNotEmpty() }
    FormFieldKind.Text, FormFieldKind.ComboBox, FormFieldKind.ListBox -> null
}

/** The usual "on" value of a check box that names none. */
private const val DEFAULT_ON_VALUE = "Yes"

/**
 * What to keep in the draft after filling a field that held [before] and now
 * holds [after]: the form's own value, as its scripts may have reformatted
 * it, or null when it didn't change, as when a script rejected the value.
 */
internal fun acceptedFormValue(before: String, after: String): String? = after.takeIf { it != before }
