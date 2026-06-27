cmake_minimum_required(VERSION 3.22.1)

project(reader_core_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(reader_core_static STATIC IMPORTED GLOBAL)
set_target_properties(reader_core_static PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/../../../libs/${ANDROID_ABI}/libreader_core.a
)

add_library(reader_core_jni SHARED
    reader_jni.cpp
)

target_include_directories(reader_core_jni PRIVATE
    ${CMAKE_SOURCE_DIR}/include
)

find_library(log-lib log)

target_link_libraries(reader_core_jni
    reader_core_static
    ${log-lib}
    android
    dl
    m
)
