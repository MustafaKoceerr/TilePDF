package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.TextMarkup
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Draft columns as JSON. Each annotation is an object with a `type` and a
 * format version `v`; one this version doesn't know is skipped, so a draft
 * written by a newer app version still loads.
 */
internal object AnnotationCodec {

    private const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeAnnotations(annotations: List<Annotation>): String = buildJsonArray {
        annotations.forEach { add(it.toJson()) }
    }.toString()

    fun decodeAnnotations(text: String): List<Annotation> =
        json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            try {
                element.toAnnotation()
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    fun encodeRefs(refs: Set<ForeignRef>): String = json.encodeToString(refs.map { RefJson(it.pageIndex, it.key) })

    fun decodeRefs(text: String): Set<ForeignRef> =
        json.decodeFromString<List<RefJson>>(text).mapTo(LinkedHashSet()) { ForeignRef(it.page, it.key) }

    private fun Annotation.toJson(): JsonElement {
        val (type, body) = when (this) {
            is TextMarkup -> "markup" to json.encodeToJsonElement(
                MarkupJson(id, pageIndex, color, kind.name, rects.map { listOf(it.left, it.top, it.right, it.bottom) }, text),
            )
            is Ink -> "ink" to json.encodeToJsonElement(
                InkJson(id, pageIndex, color, widthPt, strokes.map { stroke -> stroke.flatMap { listOf(it.x, it.y) } }),
            )
            is Note -> "note" to json.encodeToJsonElement(NoteJson(id, pageIndex, color, at.x, at.y, text))
        }
        return JsonObject(body as JsonObject + mapOf("type" to JsonPrimitive(type), "v" to JsonPrimitive(VERSION)))
    }

    private fun JsonElement.toAnnotation(): Annotation? {
        val fields = this as? JsonObject ?: return null
        if (fields["v"]?.jsonPrimitive?.intOrNull != VERSION) return null
        return when (fields["type"]?.jsonPrimitive?.content) {
            "markup" -> json.decodeFromJsonElement<MarkupJson>(this).let { m ->
                TextMarkup(
                    m.id, m.page, m.color, MarkupKind.valueOf(m.kind),
                    m.rects.map { PdfRect(it[0], it[1], it[2], it[3]) }, m.text,
                )
            }
            "ink" -> json.decodeFromJsonElement<InkJson>(this).let { i ->
                Ink(i.id, i.page, i.color, i.width, i.strokes.map { s -> s.chunked(2) { PdfPoint(it[0], it[1]) } })
            }
            "note" -> json.decodeFromJsonElement<NoteJson>(this).let { n ->
                Note(n.id, n.page, n.color, PdfPoint(n.x, n.y), n.text)
            }
            else -> null
        }
    }

    @Serializable
    private data class MarkupJson(
        val id: String,
        val page: Int,
        val color: Int,
        val kind: String,
        /** left, top, right, bottom per line. */
        val rects: List<List<Float>>,
        val text: String,
    )

    @Serializable
    private data class InkJson(
        val id: String,
        val page: Int,
        val color: Int,
        val width: Float,
        /** x, y pairs per stroke. */
        val strokes: List<List<Float>>,
    )

    @Serializable
    private data class NoteJson(val id: String, val page: Int, val color: Int, val x: Float, val y: Float, val text: String)

    @Serializable
    private data class RefJson(val page: Int, val key: String)
}
