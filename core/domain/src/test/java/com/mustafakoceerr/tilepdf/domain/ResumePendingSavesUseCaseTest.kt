package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationRepository
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationSaveScheduler
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.AnnotationDraft
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.OcrWord
import com.mustafakoceerr.tilepdf.model.SaveError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePendingSavesUseCaseTest {

    private val requested = mutableListOf<String>()
    private val scheduler = AnnotationSaveScheduler { requested += it }

    private fun repository(dirty: List<String>) = object : AnnotationRepository {
        override fun observe(uri: String): Flow<AnnotationDraft?> = error("unused")
        override suspend fun prepare(uri: String, document: PdfDocument): AnnotationDraft = error("unused")
        override suspend fun update(uri: String, annotations: List<Annotation>, removedForeign: Set<ForeignRef>) = error("unused")
        override suspend fun setFormValue(uri: String, name: String, value: String) = Unit
        override suspend fun setRecognizedText(uri: String, pageIndex: Int, words: List<OcrWord>) = error("unused")
        override suspend fun recognizedText(uri: String, pages: Set<Int>): Map<Int, List<OcrWord>> = error("unused")
        override suspend fun markSaved(uri: String, saved: AnnotationDraft, newBase: FileStamp) = error("unused")
        override suspend fun markFailed(uri: String, error: SaveError) = error("unused")
        override suspend fun moveTo(from: String, to: String, saved: AnnotationDraft, newBase: FileStamp) = error("unused")
        override suspend fun dirtyUris(): List<String> = dirty
    }

    @Test
    fun requestsEveryDirtyDraft() = runTest {
        ResumePendingSavesUseCase(repository(listOf("content://a", "content://b")), scheduler)()
        assertEquals(listOf("content://a", "content://b"), requested)
    }

    @Test
    fun nothingDirtyRequestsNothing() = runTest {
        ResumePendingSavesUseCase(repository(emptyList()), scheduler)()
        assertEquals(emptyList<String>(), requested)
    }
}
