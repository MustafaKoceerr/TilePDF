package com.mustafakoceerr.tilepdf.core.pdfwriter

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Signing an encrypted file would need its strings encrypted too, which this writer doesn't do. */
class PdfEncryptedException : RuntimeException("The document is encrypted")

class PdfSignatureTooLargeException(size: Int) :
    RuntimeException("The signature takes $size bytes; there is room for ${PdfSigner.SIGNATURE_BYTES}")

/**
 * Signs a PDF: turns one of its stamp annotations into a signature field,
 * whose appearance it keeps, and appends the signature as an incremental
 * update. Everything before the update stays as it was, so earlier
 * signatures stay intact.
 */
class PdfSigner {

    /**
     * Signs [pdf] where the stamp named [stampName] (its /NM) is. [sign] gets
     * the bytes the signature covers, which are everything but the signature
     * itself, and returns the CMS to put there.
     * @throws PdfFormatException if the file can't be read or has no such stamp.
     * @throws PdfSignatureTooLargeException if the CMS doesn't fit.
     * @throws PdfEncryptedException if the file is encrypted.
     */
    fun sign(pdf: ByteArray, stampName: String, signedAt: ZonedDateTime, reason: String, sign: (ByteArray) -> ByteArray): ByteArray {
        val file = PdfFile(pdf)
        if (file.trailer["Encrypt"] != null) throw PdfEncryptedException()
        val rootRef = file.trailer["Root"] as? PdfRef ?: throw PdfFormatException("No catalog")
        val catalog = file.resolve(rootRef) as? PdfDict ?: throw PdfFormatException("No catalog")
        val found = findStamp(file, catalog, stampName) ?: throw PdfFormatException("No stamp $stampName")

        val update = IncrementalUpdate(file)
        val signatureRef = update.add(
            PdfDict(
                linkedMapOf(
                    "Type" to PdfName("Sig"),
                    "Filter" to PdfName("Adobe.PPKLite"),
                    "SubFilter" to PdfName("ETSI.CAdES.detached"),
                    "M" to PdfString(pdfDate(signedAt).toByteArray(Charsets.ISO_8859_1)),
                    "Reason" to textString(reason),
                    "ByteRange" to PdfArray(List(4) { PdfNumber(RANGE_PLACEHOLDER) }),
                    "Contents" to PdfString(ByteArray(SIGNATURE_BYTES), hex = true),
                ),
            ),
        )

        val acroFormValue = catalog["AcroForm"]
        val acroForm = acroFormValue?.let { file.resolve(it) as? PdfDict } ?: PdfDict(emptyMap())
        val fields = (acroForm["Fields"]?.let { file.resolve(it) } as? PdfArray)?.items.orEmpty()
        val widget = found.stamp
            .with("Subtype", PdfName("Widget"))
            .with("FT", PdfName("Sig"))
            .with("T", textString("Signature${fields.size + 1}"))
            .with("V", signatureRef)
            // Printed, and locked so it isn't moved or deleted.
            .with("F", PdfNumber(132))
            .with("P", found.page)
        val widgetRef = placeWidget(update, file, found, widget)
        val newAcroForm = acroForm.with("Fields", PdfArray(fields + widgetRef)).with("SigFlags", PdfNumber(3))
        when (acroFormValue) {
            is PdfRef -> update.set(acroFormValue, newAcroForm)
            is PdfDict -> update.set(rootRef, catalog.with("AcroForm", newAcroForm))
            else -> update.set(rootRef, catalog.with("AcroForm", update.add(newAcroForm)))
        }

        val written = update.write()
        val holeStart = lastIndexOf(written, hole(), from = pdf.size)
        val holeEnd = holeStart + SIGNATURE_BYTES * 2 + 2
        // Exactly as wide as the placeholder: leading zeros are fine in PDF numbers.
        val range = "[%010d %010d %010d %010d]".format(0, holeStart, holeEnd, written.size - holeEnd).toByteArray(Charsets.ISO_8859_1)
        val rangeAt = lastIndexOf(written, rangePlaceholder(), from = pdf.size)
        range.copyInto(written, rangeAt)

        val signature = sign(written.copyOfRange(0, holeStart) + written.copyOfRange(holeEnd, written.size))
        if (signature.size > SIGNATURE_BYTES) throw PdfSignatureTooLargeException(signature.size)
        signature.joinToString("") { "%02X".format(it.toInt() and 0xFF) }.toByteArray(Charsets.ISO_8859_1).copyInto(written, holeStart + 1)
        return written
    }

    /**
     * Where a stamp was: on [page], at [index] of its /Annots, which may be an
     * object of its own ([annots]). [ref] is null when the stamp is written
     * inside the array, as PDFium writes new annotations.
     */
    private class FoundStamp(val page: PdfRef, val annots: PdfRef?, val items: List<PdfObject>, val index: Int, val ref: PdfRef?, val stamp: PdfDict)

    /**
     * Writes [widget] where the stamp was. A field must be an object of its
     * own for the AcroForm to point at it, so one written inside the page's
     * /Annots gets an object, and the array points at that.
     */
    private fun placeWidget(update: IncrementalUpdate, file: PdfFile, found: FoundStamp, widget: PdfDict): PdfRef {
        found.ref?.let { ref ->
            update.set(ref, widget)
            return ref
        }
        val ref = update.add(widget)
        val annots = PdfArray(found.items.toMutableList().apply { set(found.index, ref) })
        if (found.annots != null) {
            update.set(found.annots, annots)
        } else {
            update.set(found.page, (file.resolve(found.page) as PdfDict).with("Annots", annots))
        }
        return ref
    }

    /** The stamp annotation named [name] on any page, walking the page tree. */
    private fun findStamp(file: PdfFile, catalog: PdfDict, name: String): FoundStamp? {
        val wanted = PdfString(name.toByteArray(Charsets.ISO_8859_1))
        val pending = ArrayDeque<PdfObject>().apply { catalog["Pages"]?.let(::add) }
        val seen = HashSet<PdfObject>()
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (!seen.add(node)) continue
            val dict = file.resolve(node) as? PdfDict ?: continue
            (dict["Kids"]?.let { file.resolve(it) } as? PdfArray)?.items?.forEach(pending::add)
            val page = node as? PdfRef ?: continue
            val annotsValue = dict["Annots"]
            val annots = (annotsValue?.let { file.resolve(it) } as? PdfArray)?.items.orEmpty()
            annots.forEachIndexed { index, annot ->
                val value = file.resolve(annot) as? PdfDict ?: return@forEachIndexed
                if (value["Subtype"] == PdfName("Stamp") && value["NM"] == wanted) {
                    return FoundStamp(page, annotsValue as? PdfRef, annots, index, annot as? PdfRef, value)
                }
            }
        }
        return null
    }

    private fun hole(): ByteArray = ("<" + "0".repeat(SIGNATURE_BYTES * 2) + ">").toByteArray(Charsets.ISO_8859_1)

    private fun rangePlaceholder(): ByteArray = "[$RANGE_PLACEHOLDER $RANGE_PLACEHOLDER $RANGE_PLACEHOLDER $RANGE_PLACEHOLDER]".toByteArray(Charsets.ISO_8859_1)

    private fun lastIndexOf(bytes: ByteArray, part: ByteArray, from: Int): Int {
        var at = bytes.size - part.size
        while (at >= from) {
            if (part.indices.all { bytes[at + it] == part[it] }) return at
            at--
        }
        throw PdfFormatException("Signature placeholder not written")
    }

    companion object {
        /** Room for the CMS: a certificate chain of a few certificates fits. */
        const val SIGNATURE_BYTES = 8192

        /** As wide as the offsets that replace it, so nothing moves. */
        private const val RANGE_PLACEHOLDER = "0000000000"

        /** `D:YYYYMMDDHHmmSS+HH'mm'`. */
        fun pdfDate(time: ZonedDateTime): String {
            val offset = time.offset.totalSeconds / 60
            val sign = if (offset < 0) '-' else '+'
            return "D:" + time.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                "%c%02d'%02d'".format(sign, kotlin.math.abs(offset) / 60, kotlin.math.abs(offset) % 60)
        }

        /** A PDF text string: plain bytes when ASCII, else UTF-16BE with a byte order mark. */
        fun textString(text: String): PdfString =
            if (text.all { it.code < 0x80 }) {
                PdfString(text.toByteArray(Charsets.ISO_8859_1))
            } else {
                PdfString(byteArrayOf(-2, -1) + text.toByteArray(Charsets.UTF_16BE))
            }
    }
}
