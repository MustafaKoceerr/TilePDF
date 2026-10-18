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
-keep class com.mustafakoceerr.tilepdf.core.pdf.NativeFormField {
    <init>(...);
    <fields>;
}

# Called back by the C++ code through GetMethodID on the interface.
-keep interface com.mustafakoceerr.tilepdf.core.pdf.FormScriptHost { *; }
-keepclassmembers class * implements com.mustafakoceerr.tilepdf.core.pdf.FormScriptHost {
    public int alert(java.lang.String, java.lang.String, int, int);
    public java.lang.String response(java.lang.String, java.lang.String, java.lang.String, java.lang.String, boolean);
    public void print();
    public void goToPage(int);
}
-keep class com.mustafakoceerr.tilepdf.core.pdf.NativeSignature {
    <init>(...);
    <fields>;
}
