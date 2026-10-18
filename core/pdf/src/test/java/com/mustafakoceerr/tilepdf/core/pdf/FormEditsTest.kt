package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.FORM_VALUE_OFF
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.FormFieldKind
import org.junit.Assert.assertEquals
import org.junit.Test

class FormEditsTest {

    private val rect = PdfRect(0f, 0f, 10f, 10f)

    private fun field(
        annot: Int,
        name: String,
        kind: FormFieldKind,
        value: String = "",
        checked: Boolean = false,
        export: String = "",
        options: List<String> = emptyList(),
        readOnly: Boolean = false,
    ) = FormField(0, annot, name, kind, value, rect, options, checked, export, readOnly)

    @Test
    fun textChangesOnlyWhenDifferent() {
        val fields = listOf(field(0, "name", FormFieldKind.Text, value = "Ada"), field(1, "city", FormFieldKind.Text, value = "Bursa"))

        assertEquals(listOf(FormEdit.SetText(0, 0, "Grace")), formEdits(fields, mapOf("name" to "Grace", "city" to "Bursa")))
    }

    @Test
    fun checkBoxFollowsExportValue() {
        val fields = listOf(field(0, "agree", FormFieldKind.CheckBox, value = FORM_VALUE_OFF, export = "Yes"))

        assertEquals(listOf(FormEdit.SetChecked(0, 0, true)), formEdits(fields, mapOf("agree" to "Yes")))
        assertEquals(emptyList<FormEdit>(), formEdits(fields, mapOf("agree" to FORM_VALUE_OFF)))
    }

    @Test
    fun radioChecksMatchingButton() {
        val fields = listOf(
            field(3, "size", FormFieldKind.RadioButton, value = "S", checked = true, export = "S"),
            field(4, "size", FormFieldKind.RadioButton, value = "S", export = "L"),
        )

        assertEquals(listOf(FormEdit.SetChecked(0, 4, true)), formEdits(fields, mapOf("size" to "L")))
    }

    @Test
    fun radioOffUnchecksChecked() {
        val fields = listOf(
            field(3, "size", FormFieldKind.RadioButton, value = "S", checked = true, export = "S"),
            field(4, "size", FormFieldKind.RadioButton, value = "S", export = "L"),
        )

        assertEquals(listOf(FormEdit.SetChecked(0, 3, false)), formEdits(fields, mapOf("size" to FORM_VALUE_OFF)))
    }

    @Test
    fun choiceByLabel() {
        val fields = listOf(field(5, "city", FormFieldKind.ComboBox, value = "Ankara", options = listOf("Ankara", "İstanbul", "İzmir")))

        assertEquals(listOf(FormEdit.SetChoice(0, 5, 2)), formEdits(fields, mapOf("city" to "İzmir")))
    }

    @Test
    fun unknownOptionIsSkipped() {
        val fields = listOf(field(5, "city", FormFieldKind.ListBox, options = listOf("Ankara")))

        assertEquals(emptyList<FormEdit>(), formEdits(fields, mapOf("city" to "Paris")))
    }

    @Test
    fun readOnlyIsSkipped() {
        val fields = listOf(field(6, "id", FormFieldKind.Text, value = "42", readOnly = true))

        assertEquals(emptyList<FormEdit>(), formEdits(fields, mapOf("id" to "43")))
    }

    @Test
    fun unknownNameIsSkipped() {
        assertEquals(emptyList<FormEdit>(), formEdits(listOf(field(0, "name", FormFieldKind.Text)), mapOf("other" to "x")))
    }
}
