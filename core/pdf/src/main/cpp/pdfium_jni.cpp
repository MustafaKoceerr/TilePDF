// JNI bridge to PDFium. Every entry point is called from Kotlin's single PDF
// dispatcher, so PDFium's global state is never touched concurrently.

#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <cerrno>
#include <cmath>
#include <cstdint>
#include <list>
#include <unistd.h>
#include <utility>
#include <vector>

#include "fpdf_doc.h"
#include "fpdf_text.h"
#include "fpdfview.h"

namespace {

bool g_initialized = false;

// NativePageText(String, float[]), resolved once in JNI_OnLoad.
jclass g_pageTextClass = nullptr;
jmethodID g_pageTextConstructor = nullptr;

// Pages kept loaded per document. Loading parses the page's content, so a
// page drawn as a dozen tiles is parsed once instead of once per tile.
constexpr size_t kLoadedPages = 4;

// PDFium reads the file lazily for as long as the document is open, so the
// read callback and the descriptor it uses live as long as the document.
struct NativeDocument {
    FPDF_FILEACCESS access{};
    FPDF_DOCUMENT document = nullptr;
    // Most recently used first.
    std::list<std::pair<int, FPDF_PAGE>> pages;

    // The loaded page [index], from the cache when possible; null if it
    // can't be loaded. Owned by this document: callers must not close it.
    FPDF_PAGE Page(int index) {
        for (auto it = pages.begin(); it != pages.end(); ++it) {
            if (it->first == index) {
                pages.splice(pages.begin(), pages, it);
                return it->second;
            }
        }
        FPDF_PAGE page = FPDF_LoadPage(document, index);
        if (!page) return nullptr;
        pages.emplace_front(index, page);
        if (pages.size() > kLoadedPages) {
            FPDF_ClosePage(pages.back().second);
            pages.pop_back();
        }
        return page;
    }

    // Closes every loaded page; later calls load them again.
    void Trim() {
        for (auto& entry : pages) FPDF_ClosePage(entry.second);
        pages.clear();
    }

    // Pages first: PDFium requires them closed before their document.
    void Close() {
        Trim();
        FPDF_CloseDocument(document);
    }
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

// A cached page and its text page; the text page lives for one call.
struct TextPage {
    FPDF_PAGE page;
    FPDF_TEXTPAGE text;

    TextPage(NativeDocument* document, int index)
        : page(document->Page(index)), text(page ? FPDFText_LoadPage(page) : nullptr) {}

    ~TextPage() {
        if (text) FPDFText_ClosePage(text);
    }

    TextPage(const TextPage&) = delete;
    TextPage& operator=(const TextPage&) = delete;
};

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass local = env->FindClass("com/mustafakoceerr/tilepdf/core/pdf/NativePageText");
    if (!local) return JNI_ERR;
    g_pageTextClass = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    g_pageTextConstructor = env->GetMethodID(g_pageTextClass, "<init>", "(Ljava/lang/String;[F)V");
    return g_pageTextConstructor ? JNI_VERSION_1_6 : JNI_ERR;
}

JNIEXPORT void JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeInit(JNIEnv*, jclass) {
    if (g_initialized) return;
    FPDF_LIBRARY_CONFIG config{};
    config.version = 2;
    FPDF_InitLibraryWithConfig(&config);
    g_initialized = true;
}

// Returns a document handle, or 0 with PDFium's error code in error[0]. A
// handle is a pointer and may be negative as a jlong: arm64 tags heap pointers.
JNIEXPORT jlong JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeOpen(
        JNIEnv* env, jclass, jint fd, jlong size, jstring password, jintArray error) {
    const char* passwordChars = nullptr;
    if (password) {
        passwordChars = env->GetStringUTFChars(password, nullptr);
        if (!passwordChars) return 0;  // OutOfMemoryError is pending.
    }
    auto* native = new NativeDocument();
    native->access.m_FileLen = static_cast<unsigned long>(size);
    native->access.m_GetBlock = ReadBlock;
    native->access.m_Param = reinterpret_cast<void*>(static_cast<intptr_t>(fd));

    native->document = FPDF_LoadCustomDocument(&native->access, passwordChars);
    if (passwordChars) env->ReleaseStringUTFChars(password, passwordChars);

    if (!native->document) {
        const auto code = static_cast<jint>(FPDF_GetLastError());
        const jint reported = code > 0 ? code : FPDF_ERR_UNKNOWN;
        env->SetIntArrayRegion(error, 0, 1, &reported);
        delete native;
        return 0;
    }
    return reinterpret_cast<jlong>(native);
}

JNIEXPORT void JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeCloseDocument(JNIEnv*, jclass, jlong handle) {
    NativeDocument* native = FromHandle(handle);
    native->Close();
    delete native;
}

JNIEXPORT void JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeTrim(JNIEnv*, jclass, jlong handle) {
    FromHandle(handle)->Trim();
}

JNIEXPORT jint JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeLoadedPageCount(JNIEnv*, jclass, jlong handle) {
    return static_cast<jint>(FromHandle(handle)->pages.size());
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
    if (!result) return nullptr;  // OutOfMemoryError is pending.
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
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return JNI_FALSE;

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
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
    return target ? JNI_TRUE : JNI_FALSE;
}

// The page's text and boxes as a NativePageText, loading the text page once;
// null if the page has no text layer. One Java char per PDFium character
// index (outside the BMP becomes U+FFFD) and four floats per char: left,
// top, right, bottom in top-left points, NaN for generated characters and
// those without a box.
JNIEXPORT jobject JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativePageText(
        JNIEnv* env, jclass, jlong handle, jint index) {
    TextPage page(FromHandle(handle), index);
    if (!page.text) return nullptr;
    const int count = std::max(FPDFText_CountChars(page.text), 0);
    std::vector<jchar> chars(static_cast<size_t>(count));
    std::vector<jfloat> boxes(static_cast<size_t>(count) * 4, NAN);
    for (int i = 0; i < count; ++i) {
        const unsigned int unicode = FPDFText_GetUnicode(page.text, i);
        chars[static_cast<size_t>(i)] = unicode > 0xFFFF ? 0xFFFD : static_cast<jchar>(unicode);
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
    jstring text = env->NewString(chars.data(), count);
    if (!text) return nullptr;  // OutOfMemoryError is pending.
    jfloatArray boxArray = env->NewFloatArray(count * 4);
    if (!boxArray) return nullptr;  // OutOfMemoryError is pending.
    env->SetFloatArrayRegion(boxArray, 0, count * 4, boxes.data());
    return env->NewObject(g_pageTextClass, g_pageTextConstructor, text, boxArray);
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

// [pageIndex, x, y] in PDF user space: pageIndex -1 if the bookmark points
// nowhere; y NaN unless it is an XYZ destination with a y. Follows GoTo
// actions. Loads no page, so walking a long outline stays cheap.
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
    jfloat target[] = {-1, 0, NAN};
    if (dest) {
        target[0] = static_cast<jfloat>(FPDFDest_GetDestPageIndex(document, dest));
        FPDF_BOOL hasX = 0, hasY = 0, hasZoom = 0;
        FS_FLOAT x = 0, y = 0, zoom = 0;
        if (target[0] >= 0 && FPDFDest_GetLocationInPage(dest, &hasX, &hasY, &hasZoom, &x, &y, &zoom) && hasY) {
            target[1] = hasX ? x : 0;
            target[2] = y;
        }
    }
    jfloatArray result = env->NewFloatArray(3);
    if (!result) return nullptr;  // OutOfMemoryError is pending.
    env->SetFloatArrayRegion(result, 0, 3, target);
    return result;
}

// Maps (x, y) pairs in page [index]'s user space to top-left points, in place
// order: returns [x0, y0, x1, y1, ...], or null if the page can't be loaded.
JNIEXPORT jfloatArray JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativePageToTopLeft(
        JNIEnv* env, jclass, jlong handle, jint index, jfloatArray points) {
    const jsize count = env->GetArrayLength(points);
    std::vector<jfloat> values(static_cast<size_t>(count));
    env->GetFloatArrayRegion(points, 0, count, values.data());
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return nullptr;
    for (jsize i = 0; i + 1 < count; i += 2) {
        PageToTopLeft(page, values[static_cast<size_t>(i)], values[static_cast<size_t>(i) + 1],
                      &values[static_cast<size_t>(i)], &values[static_cast<size_t>(i) + 1]);
    }
    jfloatArray result = env->NewFloatArray(count);
    if (!result) return nullptr;  // OutOfMemoryError is pending.
    env->SetFloatArrayRegion(result, 0, count, values.data());
    return result;
}

}  // extern "C"
