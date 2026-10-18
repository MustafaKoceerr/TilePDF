package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.FormFieldKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeFormFieldMappingTest {

    private fun native(type: Int, flags: Int = 0, options: Array<String> = emptyArray(), checked: Boolean = false) =
        NativeFormField(3, type, "name", "value", flags, floatArrayOf(1f, 2f, 3f, 4f), options, checked, "Yes")

    @Test
    fun textFieldKeepsEverything() {
        assertEquals(
            FormField(2, 3, "name", FormFieldKind.Text, "value", PdfRect(1f, 2f, 3f, 4f), exportValue = "Yes"),
            native(FormFieldType.TEXT).toFormField(pageIndex = 2),
        )
    }

    @Test
    fun flagsGiveReadOnlyAndMultiline() {
        val field = native(FormFieldType.TEXT, flags = (1 shl 0) or (1 shl 12)).toFormField(0)!!

        assertEquals(true, field.readOnly)
        assertEquals(true, field.multiline)
    }

    @Test
    fun kindsAreMapped() {
        assertEquals(FormFieldKind.CheckBox, native(FormFieldType.CHECK_BOX).toFormField(0)!!.kind)
        assertEquals(FormFieldKind.RadioButton, native(FormFieldType.RADIO_BUTTON).toFormField(0)!!.kind)
        assertEquals(FormFieldKind.ComboBox, native(FormFieldType.COMBO_BOX, options = arrayOf("A")).toFormField(0)!!.kind)
        assertEquals(listOf("A"), native(FormFieldType.LIST_BOX, options = arrayOf("A")).toFormField(0)!!.options)
    }

    @Test
    fun otherWidgetsAreNotFilled() {
        assertNull(native(1).toFormField(0))
        assertNull(native(7).toFormField(0))
    }
}
