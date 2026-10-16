// JNI bridge to PDFium. Every entry point is called from Kotlin's single PDF
// dispatcher, so PDFium's global state is never touched concurrently.

#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <cerrno>
#include <cmath>
#include <cstdint>
#include <unistd.h>
#include <vector>

#include "fpdf_doc.h"
#include "fpdf_text.h"
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

// Device space is PDFium's page space mapped to a top-left origin with crop
// box and /Rotate applied. FPDF_PageToDevice only returns ints, so the page
// is mapped at 64x and scaled back, keeping 1/64 pt precision.
constexpr int kDeviceScale = 64;

void PageToTopLeft(FPDF_PAGE page, double x, double y, float* outX, float* outY) {
    const int width = static_cast<int>(FPDF_GetPageWidthF(page) * kDeviceScale + 0.5f);
    const int height = static_cast<int>(FPDF_GetPageHeightF(page) * kDeviceScale + 0.5f);
    int deviceX = 0;
    int deviceY = 0;
    FPDF_PageToDevice(page, 0, 0, width, height, 0, x, y, &deviceX, &deviceY);
    *outX = static_cast<float>(deviceX) / kDeviceScale;
    *outY = static_cast<float>(deviceY) / kDeviceScale;
}

// Owns a loaded page and its text page for the duration of one call.
struct TextPage {
    FPDF_PAGE page;
    FPDF_TEXTPAGE text;

    TextPage(FPDF_DOCUMENT document, int index)
        : page(FPDF_LoadPage(document, index)), text(page ? FPDFText_LoadPage(page) : nullptr) {}

    ~TextPage() {
        if (text) FPDFText_ClosePage(text);
        if (page) FPDF_ClosePage(page);
    }

    TextPage(const TextPage&) = delete;
    TextPage& operator=(const TextPage&) = delete;
};

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

// The page's text, one Java char per PDFium character index, or null if the
// page has no text layer. Characters outside the BMP become U+FFFD so the
// indices stay aligned with nativeCharBoxes.
JNIEXPORT jstring JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativePageText(
        JNIEnv* env, jclass, jlong handle, jint index) {
    TextPage page(FromHandle(handle)->document, index);
    if (!page.text) return nullptr;
    const int count = FPDFText_CountChars(page.text);
    if (count <= 0) return env->NewString(nullptr, 0);
    std::vector<jchar> chars(static_cast<size_t>(count));
    for (int i = 0; i < count; ++i) {
        const unsigned int unicode = FPDFText_GetUnicode(page.text, i);
        chars[static_cast<size_t>(i)] = unicode > 0xFFFF ? 0xFFFD : static_cast<jchar>(unicode);
    }
    return env->NewString(chars.data(), count);
}

// Four floats per character in nativePageText's order: left, top, right,
// bottom in top-left points. NaN for generated characters and those without
// a box. Null if the page has no text layer.
JNIEXPORT jfloatArray JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeCharBoxes(
        JNIEnv* env, jclass, jlong handle, jint index) {
    TextPage page(FromHandle(handle)->document, index);
    if (!page.text) return nullptr;
    const int count = FPDFText_CountChars(page.text);
    std::vector<jfloat> boxes(static_cast<size_t>(count > 0 ? count : 0) * 4, NAN);
    for (int i = 0; i < count; ++i) {
        double left = 0, right = 0, bottom = 0, top = 0;
        if (FPDFText_IsGenerated(page.text, i) != 0 ||
            !FPDFText_GetCharBox(page.text, i, &left, &right, &bottom, &top)) {
            continue;
        }
        float x1, y1, x2, y2;
        PageToTopLeft(page.page, left, top, &x1, &y1);
        PageToTopLeft(page.page, right, bottom, &x2, &y2);
        jfloat* box = &boxes[static_cast<size_t>(i) * 4];
        box[0] = std::min(x1, x2);
        box[1] = std::min(y1, y2);
        box[2] = std::max(x1, x2);
        box[3] = std::max(y1, y2);
    }
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(boxes.size()));
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(boxes.size()), boxes.data());
    return result;
}

// First child of [parent], or of the outline root when [parent] is 0; 0 if none.
JNIEXPORT jlong JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeBookmarkFirstChild(
        JNIEnv*, jclass, jlong handle, jlong parent) {
    return reinterpret_cast<jlong>(FPDFBookmark_GetFirstChild(
            FromHandle(handle)->document, reinterpret_cast<FPDF_BOOKMARK>(parent)));
}

JNIEXPORT jlong JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeBookmarkNextSibling(
        JNIEnv*, jclass, jlong handle, jlong bookmark) {
    return reinterpret_cast<jlong>(FPDFBookmark_GetNextSibling(
            FromHandle(handle)->document, reinterpret_cast<FPDF_BOOKMARK>(bookmark)));
}

JNIEXPORT jstring JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeBookmarkTitle(JNIEnv* env, jclass, jlong bookmark) {
    auto* native = reinterpret_cast<FPDF_BOOKMARK>(bookmark);
    // Byte length of the UTF-16LE title, including the terminating NUL.
    const unsigned long bytes = FPDFBookmark_GetTitle(native, nullptr, 0);
    if (bytes <= sizeof(jchar)) return env->NewString(nullptr, 0);
    std::vector<jchar> title(bytes / sizeof(jchar));
    FPDFBookmark_GetTitle(native, title.data(), bytes);
    return env->NewString(title.data(), static_cast<jsize>(title.size() - 1));
}

// [pageIndex, topPt]: pageIndex -1 if the bookmark points nowhere, topPt NaN
// unless it is an XYZ destination with a y. Follows GoTo actions.
JNIEXPORT jfloatArray JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeBookmarkTarget(
        JNIEnv* env, jclass, jlong handle, jlong bookmark) {
    FPDF_DOCUMENT document = FromHandle(handle)->document;
    auto* native = reinterpret_cast<FPDF_BOOKMARK>(bookmark);
    FPDF_DEST dest = FPDFBookmark_GetDest(document, native);
    if (!dest) {
        FPDF_ACTION action = FPDFBookmark_GetAction(native);
        if (action && FPDFAction_GetType(action) == PDFACTION_GOTO) dest = FPDFAction_GetDest(document, action);
    }
    jfloat target[] = {-1, NAN};
    if (dest) {
        const int pageIndex = FPDFDest_GetDestPageIndex(document, dest);
        target[0] = static_cast<jfloat>(pageIndex);
        FPDF_BOOL hasX = 0, hasY = 0, hasZoom = 0;
        FS_FLOAT x = 0, y = 0, zoom = 0;
        if (pageIndex >= 0 && FPDFDest_GetLocationInPage(dest, &hasX, &hasY, &hasZoom, &x, &y, &zoom) && hasY) {
            FPDF_PAGE page = FPDF_LoadPage(document, pageIndex);
            if (page) {
                float ignoredX = 0;
                PageToTopLeft(page, hasX ? x : 0, y, &ignoredX, &target[1]);
                FPDF_ClosePage(page);
            }
        }
    }
    jfloatArray result = env->NewFloatArray(2);
    env->SetFloatArrayRegion(result, 0, 2, target);
    return result;
}

}  // extern "C"
