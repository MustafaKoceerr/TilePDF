# ML Kit finds its components through manifest meta-data and creates each
# registrar with its no-argument constructor. firebase-components' own rule
# keeps the classes but, in R8's full mode, not that constructor: the text
# registrar was dropped and TextRecognition.getClient failed in release.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
