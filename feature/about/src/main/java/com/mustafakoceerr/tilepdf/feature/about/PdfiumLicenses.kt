package com.mustafakoceerr.tilepdf.feature.about

/** Where core:pdf's build puts the license texts of the bundled PDFium. */
internal const val PDFIUM_LICENSES_DIR = "licenses/pdfium"

private val LICENSE_EXTENSIONS = setOf("txt", "md", "ijg")

/** The usual spelling of each component; file names are lower-case ids. */
private val DISPLAY_NAMES = mapOf(
    "abseil" to "Abseil",
    "agg23" to "AGG",
    "catapult" to "Catapult",
    "cpu_features" to "cpu_features",
    "fast_float" to "fast_float",
    "freetype" to "FreeType",
    "icu" to "ICU",
    "lcms" to "Little CMS",
    "libjpeg_turbo" to "libjpeg-turbo",
    "libopenjpeg" to "OpenJPEG",
    "libpng" to "libpng",
    "libunwind" to "libunwind",
    "llvm-libc" to "LLVM libc",
    "pdfium" to "PDFium",
    "pdfium-binaries" to "pdfium-binaries",
    "simdutf" to "simdutf",
    "zlib" to "zlib",
)

/** The component a license file belongs to; libjpeg-turbo ships a second, IJG license. */
internal fun componentName(fileName: String): String {
    val id = fileName.substringBeforeLast('.')
    val name = DISPLAY_NAMES[id] ?: id
    return if (fileName.endsWith(".ijg")) "$name (IJG)" else name
}

/** License files in display order: PDFium first, then the rest by name; anything else is dropped. */
internal fun pdfiumLicenseFiles(names: List<String>): List<String> =
    names.filter { it.substringAfterLast('.', "") in LICENSE_EXTENSIONS }
        .sortedWith(compareBy<String> { it != "pdfium.txt" }.thenBy { it })
