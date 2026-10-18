package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.FormFieldKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormFieldAtTest {

    private fun field(name: String, rect: PdfRect, readOnly: Boolean = false) =
        FormField(0, 0, name, FormFieldKind.Text, "", rect, readOnly = readOnly)

    private val page = field("page", PdfRect(0f, 0f, 500f, 500f))
    private val box = field("box", PdfRect(100f, 100f, 120f, 120f))

    @Test
    fun insideHits() = assertEquals(box, formFieldAt(listOf(box), PdfPoint(110f, 105f)))

    @Test
    fun outsideMisses() = assertNull(formFieldAt(listOf(box), PdfPoint(130f, 105f)))

    @Test
    fun readOnlyIgnored() = assertNull(formFieldAt(listOf(box.copy(readOnly = true)), PdfPoint(110f, 105f)))

    @Test
    fun smallestWins() = assertEquals(box, formFieldAt(listOf(page, box), PdfPoint(110f, 105f)))

    private fun button(kind: FormFieldKind, checked: Boolean, export: String = "Yes") =
        FormField(0, 0, "b", kind, "", PdfRect(0f, 0f, 1f, 1f), checked = checked, exportValue = export)

    @Test
    fun tapTogglesCheckBox() {
        assertEquals("Yes", tapValue(button(FormFieldKind.CheckBox, checked = false)))
        assertEquals("Off", tapValue(button(FormFieldKind.CheckBox, checked = true)))
    }

    @Test
    fun tapChoosesRadioButtonOnce() {
        assertEquals("L", tapValue(button(FormFieldKind.RadioButton, checked = false, export = "L")))
        assertNull(tapValue(button(FormFieldKind.RadioButton, checked = true, export = "L")))
    }

    @Test
    fun tapOnTextOrChoiceAsksInstead() {
        assertNull(tapValue(button(FormFieldKind.Text, checked = false)))
        assertNull(tapValue(button(FormFieldKind.ComboBox, checked = false)))
    }
}
