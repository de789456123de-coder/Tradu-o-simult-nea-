cmake_minimum_required(VERSION 3.10.2)
project(whisper_jni)

set(CMAKE_CXX_STANDARD 14)

include_directories(${CMAKE_SOURCE_DIR})

add_library(whisper_jni SHARED whisper_jni.cpp)

add_library(whisper SHARED IMPORTED)
set_target_properties(whisper PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libwhisper.so)

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml.so)

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-base.so)

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-cpu.so)

find_library(log-lib log)

target_link_libraries(whisper_jni
    whisper
    ggml
    ggml-base
    ggml-cpu
    ${log-lib}
)
