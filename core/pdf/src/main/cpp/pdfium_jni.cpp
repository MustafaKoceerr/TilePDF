// JNI bridge to PDFium. Every entry point is called from Kotlin's single PDF
// dispatcher, so PDFium's global state is never touched concurrently.

#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <cerrno>
#include <cmath>
#include <cstdint>
#include <list>
#include <set>
#include <string>
#include <unistd.h>
#include <utility>
#include <vector>

#include "fpdf_annot.h"
#include "fpdf_doc.h"
#include "fpdf_edit.h"
#include "fpdf_formfill.h"
#include "fpdf_save.h"
#include "fpdf_text.h"
#include "fpdfview.h"

namespace {

bool g_initialized = false;

// For calls from PDFium back into Java, which come on the PDF thread.
JavaVM* g_vm = nullptr;

// FormScriptHost's methods, resolved once in JNI_OnLoad.
jmethodID g_hostAlert = nullptr;
jmethodID g_hostResponse = nullptr;
jmethodID g_hostPrint = nullptr;
jmethodID g_hostGoToPage = nullptr;

// The fallback font's bytes, also the font recognised text is written in.
std::vector<uint8_t> g_textFont;

// The content mark on the invisible text objects of recognised words.
constexpr char kOcrMark[] = "TilePdfOcr";

// NativePageText(String, float[]), resolved once in JNI_OnLoad.
jclass g_pageTextClass = nullptr;
jmethodID g_pageTextConstructor = nullptr;

// NativeAnnotation(int, int, String, String, int, float, float[], float[], int[], boolean).
jclass g_annotationClass = nullptr;
jmethodID g_annotationConstructor = nullptr;

// NativeFormField(int, int, String, String, int, float[], String[], boolean, String).
jclass g_formFieldClass = nullptr;
jmethodID g_formFieldConstructor = nullptr;

// /NM prefix of the annotations TilePDF creates.
constexpr char kOwnPrefix[] = "tilepdf-";

// Closes an annotation handle when it goes out of scope.
struct Annotation {
    FPDF_ANNOTATION handle;

    explicit Annotation(FPDF_ANNOTATION annotation) : handle(annotation) {}
    ~Annotation() {
        if (handle) FPDFPage_CloseAnnot(handle);
    }

    Annotation(const Annotation&) = delete;
    Annotation& operator=(const Annotation&) = delete;
};

// The UTF-16 value of string [key] without its terminating NUL; empty if absent.
std::vector<jchar> StringValue(FPDF_ANNOTATION annotation, const char* key) {
    const unsigned long bytes = FPDFAnnot_GetStringValue(annotation, key, nullptr, 0);
    if (bytes <= sizeof(FPDF_WCHAR)) return {};
    std::vector<jchar> value(bytes / sizeof(FPDF_WCHAR));
    FPDFAnnot_GetStringValue(annotation, key, reinterpret_cast<FPDF_WCHAR*>(value.data()), bytes);
    value.pop_back();
    return value;
}

bool IsOwn(const std::vector<jchar>& name) {
    const size_t length = sizeof(kOwnPrefix) - 1;
    if (name.size() < length) return false;
    for (size_t i = 0; i < length; ++i) {
        if (name[i] != static_cast<jchar>(kOwnPrefix[i])) return false;
    }
    return true;
}

// Hides the annotations TilePDF made: the reader draws those itself. Only
// ever done to documents that are not saved.
void HideOwnAnnotations(FPDF_PAGE page) {
    const int count = FPDFPage_GetAnnotCount(page);
    for (int i = 0; i < count; ++i) {
        Annotation annotation(FPDFPage_GetAnnot(page, i));
        if (annotation.handle && IsOwn(StringValue(annotation.handle, "NM"))) {
            FPDFAnnot_SetFlags(annotation.handle, FPDFAnnot_GetFlags(annotation.handle) | FPDF_ANNOT_FLAG_HIDDEN);
        }
    }
}

// Pages kept loaded per document. Loading parses the page's content, so a
// page drawn as a dozen tiles is parsed once instead of once per tile: on a
// Galaxy A52 a 1080 px page of a 300 dpi scan draws in about 300 ms instead
// of 1450 ms. A loaded page also keeps its decoded images (~29 MB per
// scanned page), and Android 14+ sends no trim callbacks while the app is
// visible, so the cap stays small. Tiles are requested page by page, so two
// pages cover the pages on screen.
constexpr size_t kLoadedPages = 2;

struct NativeDocument;

// The form-fill callbacks of one document; PDFium hands them back to every
// callback, and they lead to the document.
struct FormCallbacks : FPDF_FORMFILLINFO {
    NativeDocument* owner = nullptr;
};

// The JavaScript platform of one document: PDFium hands it back to every
// callback, and it leads to the document's FormScriptHost.
struct ScriptPlatform : IPDF_JSPLATFORM {
    NativeDocument* owner = nullptr;
};

// PDFium reads the file lazily for as long as the document is open, so the
// read callback and the descriptor it uses live as long as the document.
struct NativeDocument {
    FPDF_FILEACCESS access{};
    FPDF_DOCUMENT document = nullptr;
    // A reader's document: our annotations are hidden as pages load.
    bool hideOwn = false;
    // Most recently used first.
    std::list<std::pair<int, FPDF_PAGE>> pages;
    // The form-fill environment of a document with an AcroForm, else null.
    // PDFium keeps a pointer to formInfo, so it lives as long as the form.
    FormCallbacks formInfo{};
    FPDF_FORMHANDLE form = nullptr;
    // The embedded font of recognised text, loaded when first needed.
    FPDF_FONT textFont = nullptr;
    // Form scripts' platform, and the reader's FormScriptHost (a global ref)
    // that answers them; without a host they get default answers.
    ScriptPlatform scripts{};
    jobject scriptHost = nullptr;
    bool openActionDone = false;
    // An XFA form whose pages PDFium lays out from its template.
    bool xfa = false;

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
        if (form) FORM_OnAfterLoadPage(page, form);
        if (hideOwn) HideOwnAnnotations(page);
        pages.emplace_front(index, page);
        if (pages.size() > kLoadedPages) {
            ClosePage(pages.back().second);
            pages.pop_back();
        }
        return page;
    }

    void ClosePage(FPDF_PAGE page) {
        if (form) FORM_OnBeforeClosePage(page, form);
        FPDF_ClosePage(page);
    }

    // Closes every loaded page; later calls load them again.
    void Trim() {
        for (auto& entry : pages) ClosePage(entry.second);
        pages.clear();
    }

    // Pages, then the form, then the document, as PDFium requires.
    void Close() {
        Trim();
        if (form) FPDFDOC_ExitFormFillEnvironment(form);
        if (textFont) FPDFFont_Close(textFont);
        FPDF_CloseDocument(document);
        if (scriptHost) {
            JNIEnv* env = nullptr;
            if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) env->DeleteGlobalRef(scriptHost);
            scriptHost = nullptr;
        }
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

// The JNIEnv of the PDF thread, a Java thread; null if there is none.
JNIEnv* CurrentEnv() {
    JNIEnv* env = nullptr;
    return g_vm && g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK ? env : nullptr;
}

// A NUL-terminated UTF-16 string from PDFium as a Java string.
jstring WideToJava(JNIEnv* env, FPDF_WIDESTRING text) {
    if (!text) return env->NewString(nullptr, 0);
    jsize length = 0;
    while (text[length]) ++length;
    return env->NewString(reinterpret_cast<const jchar*>(text), length);
}

// The reader's host of the document this platform belongs to, with an env to call it.
jobject HostOf(IPDF_JSPLATFORM* platform, JNIEnv** env) {
    auto* scripts = static_cast<ScriptPlatform*>(platform);
    *env = CurrentEnv();
    return scripts->owner && *env ? scripts->owner->scriptHost : nullptr;
}

// Whatever a host call threw is dropped: a script gets the default answer.
bool Threw(JNIEnv* env) {
    if (!env->ExceptionCheck()) return false;
    env->ExceptionClear();
    return true;
}

int ScriptAlert(IPDF_JSPLATFORM* platform, FPDF_WIDESTRING message, FPDF_WIDESTRING title, int type, int icon) {
    const int fallback = type == JSPLATFORM_ALERT_BUTTON_YESNO || type == JSPLATFORM_ALERT_BUTTON_YESNOCANCEL
                             ? JSPLATFORM_ALERT_RETURN_YES : JSPLATFORM_ALERT_RETURN_OK;
    JNIEnv* env = nullptr;
    jobject host = HostOf(platform, &env);
    if (!host) return fallback;
    jstring jMessage = WideToJava(env, message);
    jstring jTitle = WideToJava(env, title);
    const jint answer = env->CallIntMethod(host, g_hostAlert, jMessage, jTitle, type, icon);
    env->DeleteLocalRef(jMessage);
    env->DeleteLocalRef(jTitle);
    return Threw(env) ? fallback : answer;
}

// Writes the answer as UTF-16LE into [response] when it fits; returns its size in bytes.
int ScriptResponse(IPDF_JSPLATFORM* platform, FPDF_WIDESTRING question, FPDF_WIDESTRING title,
                   FPDF_WIDESTRING defaultValue, FPDF_WIDESTRING label, FPDF_BOOL password,
                   void* response, int length) {
    JNIEnv* env = nullptr;
    jobject host = HostOf(platform, &env);
    if (!host) return 0;
    jstring jQuestion = WideToJava(env, question);
    jstring jTitle = WideToJava(env, title);
    jstring jDefault = WideToJava(env, defaultValue);
    jstring jLabel = WideToJava(env, label);
    auto answer = static_cast<jstring>(
            env->CallObjectMethod(host, g_hostResponse, jQuestion, jTitle, jDefault, jLabel, password ? JNI_TRUE : JNI_FALSE));
    env->DeleteLocalRef(jQuestion);
    env->DeleteLocalRef(jTitle);
    env->DeleteLocalRef(jDefault);
    env->DeleteLocalRef(jLabel);
    if (Threw(env) || !answer) return 0;
    const jsize chars = env->GetStringLength(answer);
    const int bytes = static_cast<int>(chars) * static_cast<int>(sizeof(jchar));
    if (response && length >= bytes) env->GetStringRegion(answer, 0, chars, static_cast<jchar*>(response));
    env->DeleteLocalRef(answer);
    return bytes;
}

void ScriptPrint(IPDF_JSPLATFORM* platform, FPDF_BOOL, int, int, FPDF_BOOL, FPDF_BOOL, FPDF_BOOL, FPDF_BOOL, FPDF_BOOL) {
    JNIEnv* env = nullptr;
    jobject host = HostOf(platform, &env);
    if (!host) return;
    env->CallVoidMethod(host, g_hostPrint);
    Threw(env);
}

void ScriptGoToPage(IPDF_JSPLATFORM* platform, int pageIndex) {
    JNIEnv* env = nullptr;
    jobject host = HostOf(platform, &env);
    if (!host) return;
    env->CallVoidMethod(host, g_hostGoToPage, pageIndex);
    Threw(env);
}

// XFA widgets one types into: date and time, numeric, password and text
// edits, as FORM_TilePdfGetFocusedXfaWidgetType reports them.
bool IsXfaTextWidget(int type) {
    return type == 5 || type == 7 || type == 8 || type == 10;
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

// The inverse of PageToTopLeft: top-left points to page user space.
void TopLeftToPage(FPDF_PAGE page, float x, float y, double* outX, double* outY) {
    const int width = static_cast<int>(FPDF_GetPageWidthF(page) * kDeviceScale + 0.5f);
    const int height = static_cast<int>(FPDF_GetPageHeightF(page) * kDeviceScale + 0.5f);
    FPDF_DeviceToPage(page, 0, 0, width, height, 0,
                      static_cast<int>(x * kDeviceScale + 0.5f), static_cast<int>(y * kDeviceScale + 0.5f),
                      outX, outY);
}

// The link annotation under top-left point (x, y) of page [index], or null.
FPDF_LINK LinkAt(NativeDocument* document, int index, float x, float y) {
    FPDF_PAGE page = document->Page(index);
    if (!page) return nullptr;
    double pageX = 0;
    double pageY = 0;
    TopLeftToPage(page, x, y, &pageX, &pageY);
    return FPDFLink_GetLinkAtPoint(page, pageX, pageY);
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

// Whether the annotation at [index] has foreign [key]: its /NM, or "#index"
// when it has none.
bool MatchesKey(FPDF_ANNOTATION annotation, int index, const std::vector<jchar>& key) {
    const std::vector<jchar> name = StringValue(annotation, "NM");
    if (!name.empty()) return name == key;
    const std::string byIndex = "#" + std::to_string(index);
    return key.size() == byIndex.size() && std::equal(key.begin(), key.end(), byIndex.begin());
}

std::vector<jchar> Chars(JNIEnv* env, jstring string) {
    const jsize length = env->GetStringLength(string);
    std::vector<jchar> chars(static_cast<size_t>(length));
    env->GetStringRegion(string, 0, length, chars.data());
    return chars;
}

// Markup annotations a user may delete; links, popups and widgets are not.
bool IsDeletable(int subtype) {
    switch (subtype) {
        case FPDF_ANNOT_TEXT:
        case FPDF_ANNOT_FREETEXT:
        case FPDF_ANNOT_LINE:
        case FPDF_ANNOT_SQUARE:
        case FPDF_ANNOT_CIRCLE:
        case FPDF_ANNOT_POLYGON:
        case FPDF_ANNOT_POLYLINE:
        case FPDF_ANNOT_HIGHLIGHT:
        case FPDF_ANNOT_UNDERLINE:
        case FPDF_ANNOT_SQUIGGLY:
        case FPDF_ANNOT_STRIKEOUT:
        case FPDF_ANNOT_STAMP:
        case FPDF_ANNOT_CARET:
        case FPDF_ANNOT_INK:
        case FPDF_ANNOT_FILEATTACHMENT:
            return true;
        default:
            return false;
    }
}

std::vector<std::vector<jchar>> StringList(JNIEnv* env, jobjectArray strings) {
    const jsize count = env->GetArrayLength(strings);
    std::vector<std::vector<jchar>> result;
    for (jsize i = 0; i < count; ++i) {
        auto string = static_cast<jstring>(env->GetObjectArrayElement(strings, i));
        result.push_back(Chars(env, string));
        env->DeleteLocalRef(string);
    }
    return result;
}

jobjectArray NewStringArray(JNIEnv* env, const std::vector<std::vector<jchar>>& strings) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass) return nullptr;
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(strings.size()), stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    if (!result) return nullptr;
    for (size_t i = 0; i < strings.size(); ++i) {
        jstring string = env->NewString(strings[i].data(), static_cast<jsize>(strings[i].size()));
        if (!string) return nullptr;
        env->SetObjectArrayElement(result, static_cast<jsize>(i), string);
        env->DeleteLocalRef(string);
    }
    return result;
}

// Sets /NM, /Contents (when not empty), the color, and the Print flag.
bool Describe(JNIEnv* env, FPDF_ANNOTATION annotation, jint color, jstring id, jstring contents) {
    const auto argb = static_cast<uint32_t>(color);
    if (!FPDFAnnot_SetColor(annotation, FPDFANNOT_COLORTYPE_Color, (argb >> 16) & 0xFF, (argb >> 8) & 0xFF,
                            argb & 0xFF, argb >> 24)) {
        return false;
    }
    std::vector<jchar> name = Chars(env, id);
    name.push_back(0);
    if (!FPDFAnnot_SetStringValue(annotation, "NM", reinterpret_cast<FPDF_WIDESTRING>(name.data()))) return false;
    if (contents) {
        std::vector<jchar> text = Chars(env, contents);
        if (!text.empty()) {
            text.push_back(0);
            if (!FPDFAnnot_SetStringValue(annotation, "Contents", reinterpret_cast<FPDF_WIDESTRING>(text.data()))) {
                return false;
            }
        }
    }
    return FPDFAnnot_SetFlags(annotation, FPDF_ANNOT_FLAG_PRINT);
}

// Page-space bounds of points added with Add().
struct Bounds {
    float left = INFINITY, bottom = INFINITY, right = -INFINITY, top = -INFINITY;

    void Add(float x, float y) {
        left = std::min(left, x);
        right = std::max(right, x);
        bottom = std::min(bottom, y);
        top = std::max(top, y);
    }

    FS_RECTF Rect(float inset) const { return FS_RECTF{left - inset, top + inset, right + inset, bottom - inset}; }
};

FS_POINTF ToPage(FPDF_PAGE page, float x, float y) {
    double pageX = 0, pageY = 0;
    TopLeftToPage(page, x, y, &pageX, &pageY);
    return FS_POINTF{static_cast<float>(pageX), static_cast<float>(pageY)};
}

// Whether [object] carries the content mark [name].
bool HasMark(FPDF_PAGEOBJECT object, const char* name) {
    const int count = FPDFPageObj_CountMarks(object);
    for (int i = 0; i < count; ++i) {
        FPDF_PAGEOBJECTMARK mark = FPDFPageObj_GetMark(object, static_cast<unsigned long>(i));
        unsigned long bytes = 0;
        if (!mark || !FPDFPageObjMark_GetName(mark, nullptr, 0, &bytes) || bytes <= sizeof(FPDF_WCHAR)) continue;
        std::vector<FPDF_WCHAR> value(bytes / sizeof(FPDF_WCHAR));
        if (!FPDFPageObjMark_GetName(mark, value.data(), bytes, &bytes)) continue;
        value.pop_back();
        size_t length = 0;
        while (name[length]) ++length;
        if (value.size() != length) continue;
        bool same = true;
        for (size_t c = 0; c < length && same; ++c) same = value[c] == static_cast<FPDF_WCHAR>(name[c]);
        if (same) return true;
    }
    return false;
}

// Puts [text] on [page] as an invisible text object filling the quad whose
// corners, top-left, top-right, bottom-right and bottom-left as read, are in
// page user space.
bool AddInvisibleWord(FPDF_DOCUMENT document, FPDF_PAGE page, FPDF_FONT font,
                      const std::vector<FPDF_WCHAR>& text, const FS_POINTF quad[4]) {
    FPDF_PAGEOBJECT object = FPDFPageObj_CreateTextObj(document, font, 1.0f);
    if (!object) return false;
    float left = 0, bottom = 0, right = 0, top = 0;
    if (!FPDFText_SetText(object, text.data()) ||
        !FPDFTextObj_SetTextRenderMode(object, FPDF_TEXTRENDERMODE_INVISIBLE) ||
        !FPDFPageObj_AddMark(object, kOcrMark) ||
        !FPDFPageObj_GetBounds(object, &left, &bottom, &right, &top) || right <= left || top <= bottom) {
        FPDFPageObj_Destroy(object);
        return false;
    }
    // The object's box, at size 1 at the origin, onto the quad: its bottom
    // edge runs from the bottom-left to the bottom-right corner, its left
    // edge from the bottom-left to the top-left one.
    const FS_POINTF& origin = quad[3];
    const double width = right - left;
    const double height = top - bottom;
    const double a = (quad[2].x - origin.x) / width;
    const double b = (quad[2].y - origin.y) / width;
    const double c = (quad[0].x - origin.x) / height;
    const double d = (quad[0].y - origin.y) / height;
    FPDFPageObj_Transform(object, a, b, c, d, origin.x - a * left - c * bottom, origin.y - b * left - d * bottom);
    FPDFPage_InsertObject(page, object);
    return true;
}

constexpr float kNoteIconSize = 20;

// Writes the file through an open descriptor; failed is set on a write error.
struct FdWriter : FPDF_FILEWRITE {
    int fd = -1;
    bool failed = false;
};

int WriteBlock(FPDF_FILEWRITE* self, const void* data, unsigned long size) {
    auto* writer = static_cast<FdWriter*>(self);
    const auto* bytes = static_cast<const unsigned char*>(data);
    unsigned long done = 0;
    while (done < size) {
        const ssize_t written = write(writer->fd, bytes + done, size - done);
        if (written < 0 && errno == EINTR) continue;
        if (written <= 0) {
            writer->failed = true;
            return 0;
        }
        done += static_cast<unsigned long>(written);
    }
    return 1;
}

jstring NewStringOrNull(JNIEnv* env, const std::vector<jchar>& chars) {
    return chars.empty() ? nullptr : env->NewString(chars.data(), static_cast<jsize>(chars.size()));
}

// A UTF-16LE string from a PDFium getter taking (buffer, byte length) and
// returning the byte length with its NUL; empty when absent.
template <typename Getter>
std::vector<jchar> Utf16(Getter get) {
    const unsigned long bytes = get(nullptr, 0);
    if (bytes <= sizeof(FPDF_WCHAR)) return {};
    std::vector<jchar> value(bytes / sizeof(FPDF_WCHAR));
    get(reinterpret_cast<FPDF_WCHAR*>(value.data()), bytes);
    value.pop_back();
    return value;
}

// Draws the form's widgets over a render made with [m]: FPDF_RenderPage
// leaves widgets to the form-fill environment. FPDF_FFLDraw takes a box and
// a quarter turn instead of a matrix, so both are read off [m], which only
// ever scales, turns by quarter turns and translates.
void DrawForm(FPDF_FORMHANDLE form, FPDF_BITMAP bitmap, FPDF_PAGE page, const jfloat* m, int flags) {
    const float width = FPDF_GetPageWidthF(page);
    const float height = FPDF_GetPageHeightF(page);
    float minX = 0, minY = 0, maxX = 0, maxY = 0;
    bool first = true;
    for (const auto& corner : {std::pair<float, float>{0, 0}, {width, 0}, {0, height}, {width, height}}) {
        const float x = m[0] * corner.first + m[2] * corner.second + m[4];
        const float y = m[1] * corner.first + m[3] * corner.second + m[5];
        minX = first ? x : std::min(minX, x);
        maxX = first ? x : std::max(maxX, x);
        minY = first ? y : std::min(minY, y);
        maxY = first ? y : std::max(maxY, y);
        first = false;
    }
    int rotate = 0;
    if (m[1] > 0 && m[2] < 0) rotate = 1;
    else if (m[0] < 0 && m[3] < 0) rotate = 2;
    else if (m[1] < 0 && m[2] > 0) rotate = 3;
    FPDF_FFLDraw(form, bitmap, page, static_cast<int>(std::lround(minX)), static_cast<int>(std::lround(minY)),
                 static_cast<int>(std::lround(maxX - minX)), static_cast<int>(std::lround(maxY - minY)), rotate, flags);
}

// The widget at [annotIndex] of page [index] as a NativeFormField, or null
// for widgets TilePDF doesn't fill (push buttons, signatures, XFA).
jobject ReadFormField(JNIEnv* env, FPDF_FORMHANDLE form, FPDF_PAGE page, FPDF_ANNOTATION annotation, int annotIndex) {
    const int type = FPDFAnnot_GetFormFieldType(form, annotation);
    if (type < FPDF_FORMFIELD_CHECKBOX || type > FPDF_FORMFIELD_TEXTFIELD) return nullptr;
    const std::vector<jchar> name = Utf16([&](FPDF_WCHAR* buffer, unsigned long length) {
        return FPDFAnnot_GetFormFieldName(form, annotation, buffer, length);
    });
    const std::vector<jchar> value = Utf16([&](FPDF_WCHAR* buffer, unsigned long length) {
        return FPDFAnnot_GetFormFieldValue(form, annotation, buffer, length);
    });
    const std::vector<jchar> exportValue = Utf16([&](FPDF_WCHAR* buffer, unsigned long length) {
        return FPDFAnnot_GetFormFieldExportValue(form, annotation, buffer, length);
    });
    std::vector<std::vector<jchar>> options;
    if (type == FPDF_FORMFIELD_COMBOBOX || type == FPDF_FORMFIELD_LISTBOX) {
        const int count = FPDFAnnot_GetOptionCount(form, annotation);
        for (int i = 0; i < count; ++i) {
            options.push_back(Utf16([&](FPDF_WCHAR* buffer, unsigned long length) {
                return FPDFAnnot_GetOptionLabel(form, annotation, i, buffer, length);
            }));
        }
    }
    jfloat rect[4] = {0, 0, 0, 0};
    FS_RECTF pageRect{};
    if (FPDFAnnot_GetRect(annotation, &pageRect)) {
        float x1, y1, x2, y2;
        PageToTopLeft(page, pageRect.left, pageRect.top, &x1, &y1);
        PageToTopLeft(page, pageRect.right, pageRect.bottom, &x2, &y2);
        rect[0] = std::min(x1, x2);
        rect[1] = std::min(y1, y2);
        rect[2] = std::max(x1, x2);
        rect[3] = std::max(y1, y2);
    }
    const bool checked = FPDFAnnot_IsChecked(form, annotation);

    jstring nameString = env->NewString(name.data(), static_cast<jsize>(name.size()));
    jstring valueString = env->NewString(value.data(), static_cast<jsize>(value.size()));
    jstring exportString = env->NewString(exportValue.data(), static_cast<jsize>(exportValue.size()));
    jobjectArray optionArray = NewStringArray(env, options);
    jfloatArray rectArray = env->NewFloatArray(4);
    jobject result = nullptr;
    if (nameString && valueString && exportString && optionArray && rectArray) {
        env->SetFloatArrayRegion(rectArray, 0, 4, rect);
        result = env->NewObject(g_formFieldClass, g_formFieldConstructor, annotIndex, type, nameString, valueString,
                                FPDFAnnot_GetFormFieldFlags(form, annotation), rectArray, optionArray,
                                checked ? JNI_TRUE : JNI_FALSE, exportString);
    }
    if (nameString) env->DeleteLocalRef(nameString);
    if (valueString) env->DeleteLocalRef(valueString);
    if (exportString) env->DeleteLocalRef(exportString);
    if (optionArray) env->DeleteLocalRef(optionArray);
    if (rectArray) env->DeleteLocalRef(rectArray);
    return result;
}

// The annotation at [index] as a NativeAnnotation in top-left points.
jobject ReadAnnotation(JNIEnv* env, NativeDocument* document, FPDF_PAGE page, FPDF_ANNOTATION annotation, int index) {
    const int subtype = FPDFAnnot_GetSubtype(annotation);
    const std::vector<jchar> name = StringValue(annotation, "NM");
    const std::vector<jchar> contents = StringValue(annotation, "Contents");
    const bool hasAppearance =
            FPDFAnnot_GetAP(annotation, FPDF_ANNOT_APPEARANCEMODE_NORMAL, nullptr, 0) > sizeof(FPDF_WCHAR);
    // FPDFAnnot_GetColor fails while an appearance stream exists. A reader's
    // document never draws our annotations and is never saved, so dropping
    // their appearance stream there costs nothing.
    if (document->hideOwn && IsOwn(name)) {
        FPDFAnnot_SetAP(annotation, FPDF_ANNOT_APPEARANCEMODE_NORMAL, nullptr);
    }
    unsigned int r = 0, g = 0, b = 0, a = 255;
    if (!FPDFAnnot_GetColor(annotation, FPDFANNOT_COLORTYPE_Color, &r, &g, &b, &a)) {
        r = g = b = 0;
        a = 255;
    }
    const auto color = static_cast<jint>((a << 24) | (r << 16) | (g << 8) | b);
    float radiusX = 0, radiusY = 0, width = 1;
    if (!FPDFAnnot_GetBorder(annotation, &radiusX, &radiusY, &width)) width = 1;

    jfloat rect[4] = {0, 0, 0, 0};
    FS_RECTF pageRect{};
    if (FPDFAnnot_GetRect(annotation, &pageRect)) {
        float x1, y1, x2, y2;
        PageToTopLeft(page, pageRect.left, pageRect.top, &x1, &y1);
        PageToTopLeft(page, pageRect.right, pageRect.bottom, &x2, &y2);
        rect[0] = std::min(x1, x2);
        rect[1] = std::min(y1, y2);
        rect[2] = std::max(x1, x2);
        rect[3] = std::max(y1, y2);
    }

    std::vector<jfloat> points;
    std::vector<jint> pathSizes;
    auto addPoint = [&](float x, float y) {
        float topLeftX, topLeftY;
        PageToTopLeft(page, x, y, &topLeftX, &topLeftY);
        points.push_back(topLeftX);
        points.push_back(topLeftY);
    };
    if (subtype == FPDF_ANNOT_INK) {
        const unsigned long paths = FPDFAnnot_GetInkListCount(annotation);
        for (unsigned long path = 0; path < paths; ++path) {
            const unsigned long count = FPDFAnnot_GetInkListPath(annotation, path, nullptr, 0);
            std::vector<FS_POINTF> pathPoints(count);
            FPDFAnnot_GetInkListPath(annotation, path, pathPoints.data(), count);
            for (const FS_POINTF& point : pathPoints) addPoint(point.x, point.y);
            pathSizes.push_back(static_cast<jint>(count));
        }
    } else if (subtype >= FPDF_ANNOT_HIGHLIGHT && subtype <= FPDF_ANNOT_STRIKEOUT) {
        const size_t quads = FPDFAnnot_CountAttachmentPoints(annotation);
        for (size_t quad = 0; quad < quads; ++quad) {
            FS_QUADPOINTSF q{};
            if (!FPDFAnnot_GetAttachmentPoints(annotation, quad, &q)) continue;
            addPoint(q.x1, q.y1);
            addPoint(q.x2, q.y2);
            addPoint(q.x3, q.y3);
            addPoint(q.x4, q.y4);
        }
    }

    jstring id = NewStringOrNull(env, name);
    jstring text = NewStringOrNull(env, contents);
    jfloatArray rectArray = env->NewFloatArray(4);
    jfloatArray pointArray = env->NewFloatArray(static_cast<jsize>(points.size()));
    jintArray sizeArray = env->NewIntArray(static_cast<jsize>(pathSizes.size()));
    jobject result = nullptr;
    if (rectArray && pointArray && sizeArray) {
        env->SetFloatArrayRegion(rectArray, 0, 4, rect);
        env->SetFloatArrayRegion(pointArray, 0, static_cast<jsize>(points.size()), points.data());
        env->SetIntArrayRegion(sizeArray, 0, static_cast<jsize>(pathSizes.size()), pathSizes.data());
        result = env->NewObject(g_annotationClass, g_annotationConstructor, subtype, index, id, text, color,
                                width, rectArray, pointArray, sizeArray, hasAppearance ? JNI_TRUE : JNI_FALSE);
    }
    if (id) env->DeleteLocalRef(id);
    if (text) env->DeleteLocalRef(text);
    if (rectArray) env->DeleteLocalRef(rectArray);
    if (pointArray) env->DeleteLocalRef(pointArray);
    if (sizeArray) env->DeleteLocalRef(sizeArray);
    return result;
}

}  // namespace

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    g_vm = vm;
    jclass host = env->FindClass("com/mustafakoceerr/tilepdf/core/pdf/FormScriptHost");
    if (!host) return JNI_ERR;
    g_hostAlert = env->GetMethodID(host, "alert", "(Ljava/lang/String;Ljava/lang/String;II)I");
    g_hostResponse = env->GetMethodID(
            host, "response", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)Ljava/lang/String;");
    g_hostPrint = env->GetMethodID(host, "print", "()V");
    g_hostGoToPage = env->GetMethodID(host, "goToPage", "(I)V");
    env->DeleteLocalRef(host);
    if (!g_hostAlert || !g_hostResponse || !g_hostPrint || !g_hostGoToPage) return JNI_ERR;
    jclass local = env->FindClass("com/mustafakoceerr/tilepdf/core/pdf/NativePageText");
    if (!local) return JNI_ERR;
    g_pageTextClass = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    g_pageTextConstructor = env->GetMethodID(g_pageTextClass, "<init>", "(Ljava/lang/String;[F)V");
    if (!g_pageTextConstructor) return JNI_ERR;
    local = env->FindClass("com/mustafakoceerr/tilepdf/core/pdf/NativeAnnotation");
    if (!local) return JNI_ERR;
    g_annotationClass = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    g_annotationConstructor = env->GetMethodID(
            g_annotationClass, "<init>", "(IILjava/lang/String;Ljava/lang/String;IF[F[F[IZ)V");
    if (!g_annotationConstructor) return JNI_ERR;
    local = env->FindClass("com/mustafakoceerr/tilepdf/core/pdf/NativeFormField");
    if (!local) return JNI_ERR;
    g_formFieldClass = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    g_formFieldConstructor = env->GetMethodID(
            g_formFieldClass, "<init>", "(IILjava/lang/String;Ljava/lang/String;I[F[Ljava/lang/String;ZLjava/lang/String;)V");
    return g_formFieldConstructor ? JNI_VERSION_1_6 : JNI_ERR;
}

JNIEXPORT void JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeInit(JNIEnv* env, jclass, jbyteArray formFallbackFont) {
    if (g_initialized) return;
    FPDF_LIBRARY_CONFIG config{};
    config.version = 2;
    FPDF_InitLibraryWithConfig(&config);
    if (formFallbackFont) {
        // PDFium copies the bytes; so do we, for recognised text.
        const jsize size = env->GetArrayLength(formFallbackFont);
        jbyte* bytes = env->GetByteArrayElements(formFallbackFont, nullptr);
        if (bytes) {
            FPDF_TilePdfSetFormFallbackFont(reinterpret_cast<const uint8_t*>(bytes), static_cast<uint32_t>(size));
            g_textFont.assign(reinterpret_cast<const uint8_t*>(bytes), reinterpret_cast<const uint8_t*>(bytes) + size);
            env->ReleaseByteArrayElements(formFallbackFont, bytes, JNI_ABORT);
        }
    }
    g_initialized = true;
}

// Returns a document handle, or 0 with PDFium's error code in error[0]. A
// handle is a pointer and may be negative as a jlong: arm64 tags heap pointers.
JNIEXPORT jlong JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeOpen(
        JNIEnv* env, jclass, jint fd, jlong size, jstring password, jboolean hideOwn, jintArray error) {
    const char* passwordChars = nullptr;
    if (password) {
        passwordChars = env->GetStringUTFChars(password, nullptr);
        if (!passwordChars) return 0;  // OutOfMemoryError is pending.
    }
    auto* native = new NativeDocument();
    native->hideOwn = hideOwn == JNI_TRUE;
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
    const int formType = FPDF_GetFormType(native->document);
    if (formType != FORMTYPE_NONE) {
        const bool xfa = formType == FORMTYPE_XFA_FULL || formType == FORMTYPE_XFA_FOREGROUND;
        // Version 2 is the only one our XFA-enabled PDFium accepts.
        native->formInfo.version = 2;
        native->formInfo.xfa_disabled = xfa ? 0 : 1;
        native->formInfo.owner = native;
        native->scripts.version = 3;
        native->scripts.app_alert = ScriptAlert;
        native->scripts.app_response = ScriptResponse;
        native->scripts.Doc_print = ScriptPrint;
        native->scripts.Doc_gotoPage = ScriptGoToPage;
        native->scripts.owner = native;
        native->formInfo.m_pJsPlatform = &native->scripts;
        native->form = FPDFDOC_InitFormFillEnvironment(native->document, &native->formInfo);
        // An XFA form lays out its own pages, replacing the PDF's placeholder ones.
        if (native->form && xfa) native->xfa = FPDF_LoadXFA(native->document);
        // Field scripts call the functions the document-level scripts define.
        if (native->form) FORM_DoDocumentJSAction(native->form);
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
    NativeDocument* document = FromHandle(handle);
    FPDF_PAGE page = document->Page(index);
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
        if (document->form) DrawForm(document->form, target, page, m, FPDF_ANNOT | FPDF_REVERSE_BYTE_ORDER);
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

// [pageIndex, userX, userY] of the GoTo link at top-left (x, y) on page
// [index]; userY is NaN when the destination gives no position. Null if there
// is no link there or it doesn't go to a page.
JNIEXPORT jfloatArray JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeLinkTargetAt(
        JNIEnv* env, jclass, jlong handle, jint index, jfloat x, jfloat y) {
    NativeDocument* native = FromHandle(handle);
    FPDF_LINK link = LinkAt(native, index, x, y);
    if (!link) return nullptr;
    FPDF_DEST dest = FPDFLink_GetDest(native->document, link);
    if (!dest) {
        FPDF_ACTION action = FPDFLink_GetAction(link);
        if (action && FPDFAction_GetType(action) == PDFACTION_GOTO) dest = FPDFAction_GetDest(native->document, action);
    }
    if (!dest) return nullptr;
    jfloat target[] = {static_cast<jfloat>(FPDFDest_GetDestPageIndex(native->document, dest)), 0, NAN};
    FPDF_BOOL hasX = 0, hasY = 0, hasZoom = 0;
    FS_FLOAT destX = 0, destY = 0, zoom = 0;
    if (FPDFDest_GetLocationInPage(dest, &hasX, &hasY, &hasZoom, &destX, &destY, &zoom) && hasY) {
        target[1] = hasX ? destX : 0;
        target[2] = destY;
    }
    jfloatArray result = env->NewFloatArray(3);
    if (!result) return nullptr;  // OutOfMemoryError is pending.
    env->SetFloatArrayRegion(result, 0, 3, target);
    return result;
}

// The URI of the link at top-left (x, y) on page [index], or null. Bytes
// outside 7-bit ASCII are percent-encoded: NewStringUTF aborts on invalid input.
JNIEXPORT jstring JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeLinkUriAt(
        JNIEnv* env, jclass, jlong handle, jint index, jfloat x, jfloat y) {
    NativeDocument* native = FromHandle(handle);
    FPDF_LINK link = LinkAt(native, index, x, y);
    if (!link) return nullptr;
    FPDF_ACTION action = FPDFLink_GetAction(link);
    if (!action || FPDFAction_GetType(action) != PDFACTION_URI) return nullptr;
    const unsigned long length = FPDFAction_GetURIPath(native->document, action, nullptr, 0);
    if (length <= 1) return nullptr;
    std::vector<char> raw(length);
    FPDFAction_GetURIPath(native->document, action, raw.data(), length);
    std::string uri;
    static const char kHex[] = "0123456789ABCDEF";
    for (unsigned long i = 0; i + 1 < length && raw[i] != '\0'; ++i) {
        const auto byte = static_cast<unsigned char>(raw[i]);
        if (byte >= 0x80 || byte < 0x20) {
            uri += '%';
            uri += kHex[byte >> 4];
            uri += kHex[byte & 0x0F];
        } else {
            uri += static_cast<char>(byte);
        }
    }
    return env->NewStringUTF(uri.c_str());
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

// Every annotation on page [index] as NativeAnnotation (an element is null if
// it couldn't be read), or null if the page can't be loaded.
JNIEXPORT jobjectArray JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativePageAnnotations(
        JNIEnv* env, jclass, jlong handle, jint index) {
    NativeDocument* native = FromHandle(handle);
    FPDF_PAGE page = native->Page(index);
    if (!page) return nullptr;
    const int count = std::max(FPDFPage_GetAnnotCount(page), 0);
    jobjectArray result = env->NewObjectArray(count, g_annotationClass, nullptr);
    if (!result) return nullptr;  // OutOfMemoryError is pending.
    for (int i = 0; i < count; ++i) {
        Annotation annotation(FPDFPage_GetAnnot(page, i));
        if (!annotation.handle) continue;
        jobject element = ReadAnnotation(env, native, page, annotation.handle, i);
        if (!element) {
            if (env->ExceptionCheck()) return nullptr;
            continue;
        }
        env->SetObjectArrayElement(result, i, element);
        env->DeleteLocalRef(element);
    }
    return result;
}

// Hides or shows the foreign annotation with [key] on page [index], in memory.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeSetForeignHidden(
        JNIEnv* env, jclass, jlong handle, jint index, jstring key, jboolean hidden) {
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return JNI_FALSE;
    const std::vector<jchar> wanted = Chars(env, key);
    const int count = FPDFPage_GetAnnotCount(page);
    for (int i = 0; i < count; ++i) {
        Annotation annotation(FPDFPage_GetAnnot(page, i));
        if (!annotation.handle || IsOwn(StringValue(annotation.handle, "NM")) ||
            !MatchesKey(annotation.handle, i, wanted)) {
            continue;
        }
        const int flags = FPDFAnnot_GetFlags(annotation.handle);
        FPDFAnnot_SetFlags(annotation.handle,
                           hidden ? flags | FPDF_ANNOT_FLAG_HIDDEN : flags & ~FPDF_ANNOT_FLAG_HIDDEN);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeAnnotationCount(JNIEnv*, jclass, jlong handle, jint index) {
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    return page ? FPDFPage_GetAnnotCount(page) : -1;
}

// /NM of every annotation TilePDF made on page [index]; null if the page can't load.
JNIEXPORT jobjectArray JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeOwnAnnotationIds(
        JNIEnv* env, jclass, jlong handle, jint index) {
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return nullptr;
    std::vector<std::vector<jchar>> ids;
    const int count = FPDFPage_GetAnnotCount(page);
    for (int i = 0; i < count; ++i) {
        Annotation annotation(FPDFPage_GetAnnot(page, i));
        if (!annotation.handle) continue;
        std::vector<jchar> name = StringValue(annotation.handle, "NM");
        if (IsOwn(name)) ids.push_back(std::move(name));
    }
    return NewStringArray(env, ids);
}

// Keys of the deletable foreign annotations on page [index]: /NM, or "#index".
JNIEXPORT jobjectArray JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeForeignKeys(
        JNIEnv* env, jclass, jlong handle, jint index) {
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return nullptr;
    std::vector<std::vector<jchar>> keys;
    const int count = FPDFPage_GetAnnotCount(page);
    for (int i = 0; i < count; ++i) {
        Annotation annotation(FPDFPage_GetAnnot(page, i));
        if (!annotation.handle || !IsDeletable(FPDFAnnot_GetSubtype(annotation.handle))) continue;
        std::vector<jchar> name = StringValue(annotation.handle, "NM");
        if (IsOwn(name)) continue;
        if (name.empty()) {
            const std::string byIndex = "#" + std::to_string(i);
            name.assign(byIndex.begin(), byIndex.end());
        }
        keys.push_back(std::move(name));
    }
    return NewStringArray(env, keys);
}

// Removes our annotations [ownIds] and the foreign ones with [foreignKeys]
// from page [index], each with its popup. Every target is found before any
// is removed, so "#index" keys refer to the page as it was.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeRemoveAnnotations(
        JNIEnv* env, jclass, jlong handle, jint index, jobjectArray ownIds, jobjectArray foreignKeys) {
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return JNI_FALSE;
    const auto own = StringList(env, ownIds);
    const auto foreign = StringList(env, foreignKeys);
    std::set<int> targets;
    const int count = FPDFPage_GetAnnotCount(page);
    for (int i = 0; i < count; ++i) {
        Annotation annotation(FPDFPage_GetAnnot(page, i));
        if (!annotation.handle) continue;
        const std::vector<jchar> name = StringValue(annotation.handle, "NM");
        bool hit;
        if (IsOwn(name)) {
            hit = std::find(own.begin(), own.end(), name) != own.end();
        } else {
            hit = IsDeletable(FPDFAnnot_GetSubtype(annotation.handle)) &&
                  std::any_of(foreign.begin(), foreign.end(),
                              [&](const std::vector<jchar>& key) { return MatchesKey(annotation.handle, i, key); });
        }
        if (!hit) continue;
        targets.insert(i);
        Annotation popup(FPDFAnnot_GetLinkedAnnot(annotation.handle, "Popup"));
        if (popup.handle) {
            const int popupIndex = FPDFPage_GetAnnotIndex(page, popup.handle);
            if (popupIndex >= 0) targets.insert(popupIndex);
        }
    }
    for (auto it = targets.rbegin(); it != targets.rend(); ++it) {
        if (!FPDFPage_RemoveAnnot(page, *it)) return JNI_FALSE;
    }
    return JNI_TRUE;
}

// Adds a highlight, underline or strikeout. [quads] holds 8 floats per line:
// its top-left, top-right, bottom-left and bottom-right corners in top-left points.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeAddMarkup(
        JNIEnv* env, jclass, jlong handle, jint index, jint subtype, jint color, jfloatArray quads, jstring id,
        jstring contents) {
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return JNI_FALSE;
    const jsize length = env->GetArrayLength(quads);
    if (length == 0 || length % 8 != 0) return JNI_FALSE;
    std::vector<jfloat> values(static_cast<size_t>(length));
    env->GetFloatArrayRegion(quads, 0, length, values.data());
    Annotation annotation(FPDFPage_CreateAnnot(page, subtype));
    if (!annotation.handle) return JNI_FALSE;
    Bounds bounds;
    for (jsize quad = 0; quad < length; quad += 8) {
        FS_POINTF corners[4];
        for (int corner = 0; corner < 4; ++corner) {
            corners[corner] = ToPage(page, values[static_cast<size_t>(quad + corner * 2)],
                                     values[static_cast<size_t>(quad + corner * 2 + 1)]);
            bounds.Add(corners[corner].x, corners[corner].y);
        }
        const FS_QUADPOINTSF points{corners[0].x, corners[0].y, corners[1].x, corners[1].y,
                                    corners[2].x, corners[2].y, corners[3].x, corners[3].y};
        if (!FPDFAnnot_AppendAttachmentPoints(annotation.handle, &points)) return JNI_FALSE;
    }
    const FS_RECTF rect = bounds.Rect(0);
    return FPDFAnnot_SetRect(annotation.handle, &rect) && Describe(env, annotation.handle, color, id, contents)
               ? JNI_TRUE : JNI_FALSE;
}

// Adds a pen annotation: [points] holds every path's top-left points in turn,
// [pathSizes] the number of points in each.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeAddInk(
        JNIEnv* env, jclass, jlong handle, jint index, jint color, jfloat width, jfloatArray points,
        jintArray pathSizes, jstring id) {
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return JNI_FALSE;
    const jsize length = env->GetArrayLength(points);
    const jsize paths = env->GetArrayLength(pathSizes);
    std::vector<jfloat> values(static_cast<size_t>(length));
    std::vector<jint> sizes(static_cast<size_t>(paths));
    env->GetFloatArrayRegion(points, 0, length, values.data());
    env->GetIntArrayRegion(pathSizes, 0, paths, sizes.data());
    Annotation annotation(FPDFPage_CreateAnnot(page, FPDF_ANNOT_INK));
    if (!annotation.handle) return JNI_FALSE;
    Bounds bounds;
    size_t offset = 0;
    for (jint size : sizes) {
        if (size <= 0 || (offset + static_cast<size_t>(size)) * 2 > values.size()) return JNI_FALSE;
        std::vector<FS_POINTF> path;
        for (jint i = 0; i < size; ++i, ++offset) {
            path.push_back(ToPage(page, values[offset * 2], values[offset * 2 + 1]));
            bounds.Add(path.back().x, path.back().y);
        }
        if (FPDFAnnot_AddInkStroke(annotation.handle, path.data(), path.size()) < 0) return JNI_FALSE;
    }
    if (bounds.left > bounds.right) return JNI_FALSE;
    const FS_RECTF rect = bounds.Rect(width);
    return FPDFAnnot_SetBorder(annotation.handle, 0, 0, width) && FPDFAnnot_SetRect(annotation.handle, &rect) &&
                   Describe(env, annotation.handle, color, id, nullptr)
               ? JNI_TRUE : JNI_FALSE;
}

// Adds a sticky note whose icon has its top-left corner at (x, y). The icon
// is 20 pt square, the size PDFium gives a note when it draws its appearance.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeAddNote(
        JNIEnv* env, jclass, jlong handle, jint index, jint color, jfloat x, jfloat y, jstring id, jstring contents) {
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return JNI_FALSE;
    Annotation annotation(FPDFPage_CreateAnnot(page, FPDF_ANNOT_TEXT));
    if (!annotation.handle) return JNI_FALSE;
    Bounds bounds;
    const FS_POINTF topLeft = ToPage(page, x, y);
    const FS_POINTF bottomRight = ToPage(page, x + kNoteIconSize, y + kNoteIconSize);
    bounds.Add(topLeft.x, topLeft.y);
    bounds.Add(bottomRight.x, bottomRight.y);
    const FS_RECTF rect = bounds.Rect(0);
    return FPDFAnnot_SetRect(annotation.handle, &rect) && Describe(env, annotation.handle, color, id, contents)
               ? JNI_TRUE : JNI_FALSE;
}

// Renders page [index] once with annotations, which makes PDFium write an
// appearance stream for every annotation that lacks one, so other viewers
// can draw what TilePDF added.
JNIEXPORT void JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeGenerateAppearances(
        JNIEnv*, jclass, jlong handle, jint index) {
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return;
    FPDF_BITMAP bitmap = FPDFBitmap_Create(1, 1, 0);
    if (!bitmap) return;
    FPDF_RenderPageBitmap(bitmap, page, 0, 0, 1, 1, 0, FPDF_ANNOT);
    FPDFBitmap_Destroy(bitmap);
}

// Writes the document with its changes appended after the original bytes.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeSaveIncremental(
        JNIEnv*, jclass, jlong handle, jint fd) {
    FdWriter writer;
    writer.version = 1;
    writer.WriteBlock = WriteBlock;
    writer.fd = fd;
    const bool saved = FPDF_SaveAsCopy(FromHandle(handle)->document, &writer, FPDF_INCREMENTAL);
    return saved && !writer.failed ? JNI_TRUE : JNI_FALSE;
}

// Whether the document has a form TilePDF can fill.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeHasForm(JNIEnv*, jclass, jlong handle) {
    return FromHandle(handle)->form ? JNI_TRUE : JNI_FALSE;
}

// The fillable widgets of page [index]; null if the page can't be loaded.
JNIEXPORT jobjectArray JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeFormFields(JNIEnv* env, jclass, jlong handle, jint index) {
    NativeDocument* document = FromHandle(handle);
    FPDF_PAGE page = document->Page(index);
    if (!page) return nullptr;
    std::vector<jobject> fields;
    if (document->form) {
        const int count = FPDFPage_GetAnnotCount(page);
        for (int i = 0; i < count; ++i) {
            Annotation annotation(FPDFPage_GetAnnot(page, i));
            if (!annotation.handle || FPDFAnnot_GetSubtype(annotation.handle) != FPDF_ANNOT_WIDGET) continue;
            jobject field = ReadFormField(env, document->form, page, annotation.handle, i);
            if (field) fields.push_back(field);
        }
    }
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(fields.size()), g_formFieldClass, nullptr);
    for (size_t i = 0; i < fields.size(); ++i) {
        if (result) env->SetObjectArrayElement(result, static_cast<jsize>(i), fields[i]);
        env->DeleteLocalRef(fields[i]);
    }
    return result;
}

// Replaces the text of the text field widget [annotIndex], as typing would,
// so PDFium writes /V and a new appearance.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeSetFormText(
        JNIEnv* env, jclass, jlong handle, jint index, jint annotIndex, jstring text) {
    NativeDocument* document = FromHandle(handle);
    FPDF_PAGE page = document->Page(index);
    if (!page || !document->form) return JNI_FALSE;
    Annotation annotation(FPDFPage_GetAnnot(page, annotIndex));
    if (!annotation.handle || !FORM_SetFocusedAnnot(document->form, annotation.handle)) return JNI_FALSE;
    std::vector<jchar> chars = Chars(env, text);
    chars.push_back(0);
    FORM_SelectAllText(document->form, page);
    FORM_ReplaceSelection(document->form, page, reinterpret_cast<FPDF_WIDESTRING>(chars.data()));
    return FORM_ForceToKillFocus(document->form) ? JNI_TRUE : JNI_FALSE;
}

// Turns the check box or radio button widget [annotIndex] on or off with a
// click at its centre, as a user would; nothing happens if it already is.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeSetFormChecked(
        JNIEnv*, jclass, jlong handle, jint index, jint annotIndex, jboolean checked) {
    NativeDocument* document = FromHandle(handle);
    FPDF_PAGE page = document->Page(index);
    if (!page || !document->form) return JNI_FALSE;
    Annotation annotation(FPDFPage_GetAnnot(page, annotIndex));
    FS_RECTF rect{};
    if (!annotation.handle || !FPDFAnnot_GetRect(annotation.handle, &rect)) return JNI_FALSE;
    const bool wanted = checked == JNI_TRUE;
    if (FPDFAnnot_IsChecked(document->form, annotation.handle) != wanted) {
        const double x = (rect.left + rect.right) / 2;
        const double y = (rect.top + rect.bottom) / 2;
        FORM_OnMouseMove(document->form, page, 0, x, y);
        FORM_OnLButtonDown(document->form, page, 0, x, y);
        FORM_OnLButtonUp(document->form, page, 0, x, y);
        FORM_ForceToKillFocus(document->form);
    }
    return FPDFAnnot_IsChecked(document->form, annotation.handle) == wanted ? JNI_TRUE : JNI_FALSE;
}

// Chooses option [optionIndex] of the combo or list box widget [annotIndex].
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeSetFormChoice(
        JNIEnv*, jclass, jlong handle, jint index, jint annotIndex, jint optionIndex) {
    NativeDocument* document = FromHandle(handle);
    FPDF_PAGE page = document->Page(index);
    if (!page || !document->form) return JNI_FALSE;
    Annotation annotation(FPDFPage_GetAnnot(page, annotIndex));
    if (!annotation.handle || !FORM_SetFocusedAnnot(document->form, annotation.handle)) return JNI_FALSE;
    const bool selected = FORM_SetIndexSelected(document->form, page, optionIndex, true);
    FORM_ForceToKillFocus(document->form);
    return selected ? JNI_TRUE : JNI_FALSE;
}

// Adds recognised words to page [index] as invisible text: [texts] holds the
// words, [quads] each word's four top-left corners in reading order as x, y
// pairs. The page then draws as before but has their text.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeAddRecognizedText(
        JNIEnv* env, jclass, jlong handle, jint index, jobjectArray texts, jfloatArray quads) {
    NativeDocument* document = FromHandle(handle);
    FPDF_PAGE page = document->Page(index);
    if (!page || g_textFont.empty()) return JNI_FALSE;
    const jsize count = env->GetArrayLength(texts);
    if (env->GetArrayLength(quads) != count * 8) return JNI_FALSE;
    if (!document->textFont) {
        document->textFont = FPDFText_LoadFont(document->document, g_textFont.data(),
                                               static_cast<uint32_t>(g_textFont.size()), FPDF_FONT_TRUETYPE, true);
        if (!document->textFont) return JNI_FALSE;
    }
    std::vector<jfloat> values(static_cast<size_t>(count) * 8);
    env->GetFloatArrayRegion(quads, 0, count * 8, values.data());
    for (jsize i = 0; i < count; ++i) {
        auto word = static_cast<jstring>(env->GetObjectArrayElement(texts, i));
        const jsize length = env->GetStringLength(word);
        std::vector<FPDF_WCHAR> text(static_cast<size_t>(length) + 1, 0);
        env->GetStringRegion(word, 0, length, reinterpret_cast<jchar*>(text.data()));
        env->DeleteLocalRef(word);
        FS_POINTF quad[4];
        for (int corner = 0; corner < 4; ++corner) {
            const size_t at = static_cast<size_t>(i) * 8 + static_cast<size_t>(corner) * 2;
            quad[corner] = ToPage(page, values[at], values[at + 1]);
        }
        if (!AddInvisibleWord(document->document, page, document->textFont, text, quad)) return JNI_FALSE;
    }
    return FPDFPage_GenerateContent(page) ? JNI_TRUE : JNI_FALSE;
}

// Whether page [index] has text TilePDF recognised.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeHasRecognizedText(JNIEnv*, jclass, jlong handle, jint index) {
    FPDF_PAGE page = FromHandle(handle)->Page(index);
    if (!page) return JNI_FALSE;
    const int count = FPDFPage_CountObjects(page);
    for (int i = 0; i < count; ++i) {
        FPDF_PAGEOBJECT object = FPDFPage_GetObject(page, i);
        if (FPDFPageObj_GetType(object) == FPDF_PAGEOBJ_TEXT && HasMark(object, kOcrMark)) return JNI_TRUE;
    }
    return JNI_FALSE;
}

// Lets [host] answer the document's form scripts, or none with null. With
// [openAction], the document's open action runs then, once.
JNIEXPORT void JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeSetScriptHost(
        JNIEnv* env, jclass, jlong handle, jobject host, jboolean openAction) {
    NativeDocument* document = FromHandle(handle);
    if (document->scriptHost) env->DeleteGlobalRef(document->scriptHost);
    document->scriptHost = host ? env->NewGlobalRef(host) : nullptr;
    if (openAction == JNI_TRUE && document->form && !document->openActionDone) {
        document->openActionDone = true;
        FORM_DoDocumentOpenAction(document->form);
    }
}

// Whether the document is an XFA form whose pages PDFium lays out itself.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeIsXfa(JNIEnv*, jclass, jlong handle) {
    NativeDocument* document = FromHandle(handle);
    return document->xfa && FPDF_GetFormType(document->document) == FORMTYPE_XFA_FULL ? JNI_TRUE : JNI_FALSE;
}

// Taps top-left point (x, y) of page [index] as a finger would. Returns the
// text of the field the tap focused if it's a text field, else null.
JNIEXPORT jstring JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeFormTap(
        JNIEnv* env, jclass, jlong handle, jint index, jfloat x, jfloat y) {
    NativeDocument* document = FromHandle(handle);
    FPDF_PAGE page = document->Page(index);
    if (!page || !document->form) return nullptr;
    double pageX = 0;
    double pageY = 0;
    TopLeftToPage(page, x, y, &pageX, &pageY);
    FORM_OnMouseMove(document->form, page, 0, pageX, pageY);
    FORM_OnLButtonDown(document->form, page, 0, pageX, pageY);
    FORM_OnLButtonUp(document->form, page, 0, pageX, pageY);
    if (!IsXfaTextWidget(FORM_TilePdfGetFocusedXfaWidgetType(document->form))) return nullptr;
    const std::vector<jchar> text = Utf16([&](FPDF_WCHAR* buffer, unsigned long length) {
        return FORM_GetFocusedText(document->form, page, buffer, length);
    });
    return env->NewString(text.data(), static_cast<jsize>(text.size()));
}

// Replaces the text of the focused field on page [index] and commits it.
JNIEXPORT jboolean JNICALL
Java_com_mustafakoceerr_tilepdf_core_pdf_PdfiumNative_nativeFormReplaceText(
        JNIEnv* env, jclass, jlong handle, jint index, jstring text) {
    NativeDocument* document = FromHandle(handle);
    FPDF_PAGE page = document->Page(index);
    if (!page || !document->form) return JNI_FALSE;
    std::vector<jchar> chars = Chars(env, text);
    chars.push_back(0);
    FORM_SelectAllText(document->form, page);
    FORM_ReplaceSelection(document->form, page, reinterpret_cast<FPDF_WIDESTRING>(chars.data()));
    return FORM_ForceToKillFocus(document->form) ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
