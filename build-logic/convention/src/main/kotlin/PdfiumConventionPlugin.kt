import com.android.build.api.dsl.LibraryExtension
import com.mustafakoceerr.tilepdf.DownloadPdfiumTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * Links the prebuilt PDFium from bblanchon/pdfium-binaries into a module's
 * CMake build. To update PDFium, change [RELEASE] and every hash in [ARCHIVES].
 */
class PdfiumConventionPlugin : Plugin<Project> {

    private companion object {
        const val RELEASE = "chromium/8076"

        /** Android ABI to the release archive and its SHA-256. */
        val ARCHIVES = mapOf(
            "arm64-v8a" to ("pdfium-android-arm64.tgz" to "b7a6919dc130dfdf7e409bd1762ed2a306408c4faee1173668837fc446217bf7"),
            "armeabi-v7a" to ("pdfium-android-arm.tgz" to "4cabe64372ec9a8f9a7e023ae1c2802068abdb5ed79d4ef5cd42f2bbde80927a"),
            "x86_64" to ("pdfium-android-x64.tgz" to "9d043689096c7cdd4ca98fff95599be115b2a38feba0c68d2a79ba5eedbda415"),
            "x86" to ("pdfium-android-x86.tgz" to "eb0aa52696094f66c86994220d45b00b63cd90b13b8e76bd9b201d7f89825214"),
        )
    }

    override fun apply(target: Project) {
        with(target) {
            // Outside build/ so `clean` doesn't download again.
            val pdfiumDir = rootProject.layout.projectDirectory.dir(".gradle/pdfium/${RELEASE.replace('/', '-')}")
            val downloads = ARCHIVES.map { (abi, archive) ->
                val (fileName, hash) = archive
                tasks.register<DownloadPdfiumTask>("downloadPdfium" + abi.split('-', '_').joinToString("") { it.replaceFirstChar(Char::uppercase) }) {
                    url.set("https://github.com/bblanchon/pdfium-binaries/releases/download/${RELEASE.replace("/", "%2F")}/$fileName")
                    sha256.set(hash)
                    outputDir.set(pdfiumDir.dir(abi))
                }
            }

            extensions.configure<LibraryExtension> {
                defaultConfig {
                    ndk.abiFilters += ARCHIVES.keys
                    externalNativeBuild.cmake.arguments += "-DPDFIUM_DIR=${pdfiumDir.asFile.invariantSeparatorsPath}"
                }
                externalNativeBuild.cmake.path = file("src/main/cpp/CMakeLists.txt")
            }

            tasks.matching { it.name.startsWith("configureCMake") }.configureEach {
                dependsOn(downloads)
            }
        }
    }
}
