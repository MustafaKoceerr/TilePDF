cmake_minimum_required(VERSION 3.22.1)
project(tilepdf_pdfium CXX)

# PDFIUM_DIR is passed by the tilepdf.pdfium Gradle plugin: one extracted
# bblanchon/pdfium-binaries archive per ABI.
set(PDFIUM_ABI_DIR ${PDFIUM_DIR}/${ANDROID_ABI})

add_library(pdfium SHARED IMPORTED)
set_target_properties(pdfium PROPERTIES
        IMPORTED_LOCATION ${PDFIUM_ABI_DIR}/lib/libpdfium.so
        INTERFACE_INCLUDE_DIRECTORIES ${PDFIUM_ABI_DIR}/include)

add_library(tilepdf_pdfium SHARED pdfium_jni.cpp)
target_compile_features(tilepdf_pdfium PRIVATE cxx_std_17)
target_compile_options(tilepdf_pdfium PRIVATE -Wall -Wextra -Werror)
target_link_libraries(tilepdf_pdfium PRIVATE pdfium jnigraphics log)
