// JNI bridge to PDFium. Every entry point is called from Kotlin's single PDF
// dispatcher, so PDFium's global state is never touched concurrently.

#include <jni.h>

#include "fpdfview.h"

namespace {

bool g_initialized = false;

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeInit(JNIEnv*, jclass) {
    if (g_initialized) return;
    FPDF_LIBRARY_CONFIG config{};
    config.version = 2;
    FPDF_InitLibraryWithConfig(&config);
    g_initialized = true;
}

}  // extern "C"
