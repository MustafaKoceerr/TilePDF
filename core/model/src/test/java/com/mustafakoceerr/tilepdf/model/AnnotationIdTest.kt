package com.mustafakoceerr.tilepdf.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationIdTest {

    @Test
    fun newIdsAreOwn() {
        assertTrue(isOwnAnnotationId(newAnnotationId()))
    }

    @Test
    fun newIdsAreUnique() {
        assertEquals(1000, List(1000) { newAnnotationId() }.toSet().size)
    }

    @Test
    fun foreignAndNullAreNotOwn() {
        for (id in listOf("acrobat-1", null, "", "TILEPDF-x")) {
            assertFalse(id.toString(), isOwnAnnotationId(id))
        }
    }
}
