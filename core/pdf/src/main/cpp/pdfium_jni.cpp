// JNI bridge to PDFium. Every entry point is called from Kotlin's single PDF
// dispatcher, so PDFium's global state is never touched concurrently.

#include <android/bitmap.h>
#include <jni.h>

#include <cerrno>
#include <cstdint>
#include <unistd.h>

#include "fpdfview.h"

namespace {

bool g_initialized = false;

// PDFium reads the file lazily for as long as the document is open, so the
// read callback and the descriptor it uses live as long as the document.
struct NativeDocument {
    FPDF_FILEACCESS access{};
    FPDF_DOCUMENT document = nullptr;
};

int ReadBlock(void* param, unsigned long position, unsigned char* buffer, unsigned long size) {
    const int fd = static_cast<int>(reinterpret_cast<intptr_t>(param));
    unsigned long done = 0;
    while (done < size) {
        const ssize_t read = pread(fd, buffer + done, size - done, static_cast<off_t>(position + done));
        if (read < 0 && errno == EINTR) continue;
        if (read <= 0) return 0;
        done += static_cast<unsigned long>(read);
    }
    return 1;
}

NativeDocument* FromHandle(jlong handle) {
    return reinterpret_cast<NativeDocument*>(handle);
}

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

// Returns a document handle (> 0), or minus PDFium's error code on failure.
JNIEXPORT jlong JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeOpen(
        JNIEnv* env, jclass, jint fd, jlong size, jstring password) {
    auto* native = new NativeDocument();
    native->access.m_FileLen = static_cast<unsigned long>(size);
    native->access.m_GetBlock = ReadBlock;
    native->access.m_Param = reinterpret_cast<void*>(static_cast<intptr_t>(fd));

    const char* passwordChars = password ? env->GetStringUTFChars(password, nullptr) : nullptr;
    native->document = FPDF_LoadCustomDocument(&native->access, passwordChars);
    if (passwordChars) env->ReleaseStringUTFChars(password, passwordChars);

    if (!native->document) {
        const auto error = static_cast<jlong>(FPDF_GetLastError());
        delete native;
        return -(error > 0 ? error : FPDF_ERR_UNKNOWN);
    }
    return reinterpret_cast<jlong>(native);
}

JNIEXPORT void JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeCloseDocument(JNIEnv*, jclass, jlong handle) {
    NativeDocument* native = FromHandle(handle);
    FPDF_CloseDocument(native->document);
    delete native;
}

JNIEXPORT jint JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativePageCount(JNIEnv*, jclass, jlong handle) {
    return FPDF_GetPageCount(FromHandle(handle)->document);
}

// [width, height] in points with /Rotate applied, or an empty array.
JNIEXPORT jfloatArray JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativePageSize(
        JNIEnv* env, jclass, jlong handle, jint index) {
    FS_SIZEF size{};
    if (!FPDF_GetPageSizeByIndexF(FromHandle(handle)->document, index, &size)) {
        return env->NewFloatArray(0);
    }
    jfloatArray result = env->NewFloatArray(2);
    const jfloat values[] = {size.width, size.height};
    env->SetFloatArrayRegion(result, 0, 2, values);
    return result;
}

// Renders page [index] into an RGBA_8888 Android bitmap. [matrix] is
// {a, b, c, d, e, f} mapping top-left page points to bitmap pixels. The
// bitmap is not cleared first. Returns false if nothing could be rendered.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeRender(
        JNIEnv* env, jclass, jlong handle, jint index, jobject bitmap, jfloatArray matrix) {
    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || env->GetArrayLength(matrix) != 6) {
        return JNI_FALSE;
    }
    FPDF_PAGE page = FPDF_LoadPage(FromHandle(handle)->document, index);
    if (!page) return JNI_FALSE;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        FPDF_ClosePage(page);
        return JNI_FALSE;
    }
    jfloat m[6];
    env->GetFloatArrayRegion(matrix, 0, 6, m);
    const FS_MATRIX transform{m[0], m[1], m[2], m[3], m[4], m[5]};
    const FS_RECTF clip{0, 0, static_cast<float>(info.width), static_cast<float>(info.height)};

    FPDF_BITMAP target = FPDFBitmap_CreateEx(
            static_cast<int>(info.width), static_cast<int>(info.height), FPDFBitmap_BGRA,
            pixels, static_cast<int>(info.stride));
    if (target) {
        // REVERSE_BYTE_ORDER writes RGBA, which is Android's ARGB_8888 layout.
        FPDF_RenderPageBitmapWithMatrix(target, page, &transform, &clip, FPDF_ANNOT | FPDF_REVERSE_BYTE_ORDER);
        FPDFBitmap_Destroy(target);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    FPDF_ClosePage(page);
    return target ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
