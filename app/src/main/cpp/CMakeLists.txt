cmake_minimum_required(VERSION 3.22.1)

project(reader_native_runtime_evidence LANGUAGES CXX)

add_library(
    reader_native_runtime_evidence
    SHARED
    reader_native_runtime_evidence.cpp
)

target_compile_features(reader_native_runtime_evidence PRIVATE cxx_std_17)

find_library(log_lib log)

target_link_libraries(reader_native_runtime_evidence PRIVATE ${log_lib})
