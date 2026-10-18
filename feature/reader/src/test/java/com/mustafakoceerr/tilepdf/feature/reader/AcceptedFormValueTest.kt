package com.mustafakoceerr.tilepdf.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AcceptedFormValueTest {

    @Test
    fun anAcceptedValueIsKept() {
        assertEquals("42", acceptedFormValue(before = "", after = "42"))
    }

    @Test
    fun aValueTheScriptReformattedIsKeptAsTheFormHoldsIt() {
        assertEquals("5", acceptedFormValue(before = "", after = "5"))
        assertEquals("Ada", acceptedFormValue(before = "ada", after = "Ada"))
    }

    @Test
    fun aRejectedValueIsNotKept() {
        assertNull(acceptedFormValue(before = "30", after = "30"))
    }
}
