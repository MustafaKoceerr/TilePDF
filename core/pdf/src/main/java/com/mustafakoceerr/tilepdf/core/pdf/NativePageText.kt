package com.mustafakoceerr.tilepdf.core.pdf

import androidx.annotation.Keep

/**
 * One page's text as PDFium returns it: one char per character index and
 * four floats (left, top, right, bottom; NaN if none) per char. Built by
 * native code, hence [Keep].
 */
@Keep
internal class NativePageText(@JvmField val text: String, @JvmField val boxes: FloatArray)
