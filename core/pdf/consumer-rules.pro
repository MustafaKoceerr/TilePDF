# The JNI bridge: libtilepdf_pdfium exports Java_..._PdfiumNative_* symbols,
# so the class and its native methods keep their names.
-keep class com.mustafakoceerr.tilepdf.core.pdf.PdfiumNative {
    native <methods>;
}

# Built by the C++ code through FindClass and GetMethodID("<init>", ...);
# R8 can't see those uses.
-keep class com.mustafakoceerr.tilepdf.core.pdf.NativePageText {
    <init>(...);
    <fields>;
}
-keep class com.mustafakoceerr.tilepdf.core.pdf.NativeAnnotation {
    <init>(...);
    <fields>;
}
