package com.mustafakoceerr.tilepdf.core.pdfwriter

/** A PDF object, as far as signing needs to read and write one. */
sealed interface PdfObject

data object PdfNull : PdfObject

data class PdfBool(val value: Boolean) : PdfObject

/** A number as the file writes it, so it is written back exactly. */
data class PdfNumber(val text: String) : PdfObject {
    constructor(value: Long) : this(value.toString())

    val value: Double get() = text.toDouble()
    val long: Long get() = text.toDoubleOrNull()?.toLong() ?: 0L
}

/** A string's bytes; [hex] only says how it was written. Equal by content. */
class PdfString(val bytes: ByteArray, val hex: Boolean = false) : PdfObject {
    override fun equals(other: Any?) = other is PdfString && other.bytes.contentEquals(bytes)
    override fun hashCode() = bytes.contentHashCode()
    override fun toString() = "PdfString(${String(bytes, Charsets.ISO_8859_1)})"
}

/** A name without its slash, its #xx escapes decoded, each byte one char. */
data class PdfName(val name: String) : PdfObject

data class PdfArray(val items: List<PdfObject>) : PdfObject

data class PdfDict(val entries: Map<String, PdfObject>) : PdfObject {
    operator fun get(key: String): PdfObject? = entries[key]

    /** This dictionary with [key] set to [value], keeping the order of the others. */
    fun with(key: String, value: PdfObject): PdfDict = PdfDict(LinkedHashMap(entries).apply { put(key, value) })

    fun without(key: String): PdfDict = PdfDict(LinkedHashMap(entries).apply { remove(key) })
}

data class PdfRef(val num: Int, val gen: Int = 0) : PdfObject

/** A stream with its data as the file stores it, still encoded. */
class PdfStream(val dict: PdfDict, val data: ByteArray) : PdfObject {
    override fun equals(other: Any?) = other is PdfStream && other.dict == dict && other.data.contentEquals(data)
    override fun hashCode() = 31 * dict.hashCode() + data.contentHashCode()
}

class PdfFormatException(message: String) : RuntimeException(message)
