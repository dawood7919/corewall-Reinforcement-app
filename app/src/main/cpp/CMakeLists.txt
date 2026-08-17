cmake_minimum_required(VERSION 3.22.1)
project(corewall_dwg LANGUAGES C CXX)

# قارئ فقط: لا نسخ JSON ولا كتابة/تحويل ملفات، ما يقلل سطح الهجوم وحجم المكتبة.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LIBREDWG_LIBONLY ON CACHE BOOL "" FORCE)
set(LIBREDWG_DISABLE_WRITE ON CACHE BOOL "" FORCE)
set(LIBREDWG_DISABLE_JSON ON CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(ENABLE_LTO OFF CACHE BOOL "" FORCE)
set(DISABLE_WERROR ON CACHE BOOL "" FORCE)

set(LIBREDWG_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/libredwg")
if (NOT EXISTS "${LIBREDWG_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "LibreDWG is missing. Initialize the Git submodule: git submodule update --init --recursive")
endif()

add_subdirectory("${LIBREDWG_DIR}" "${CMAKE_BINARY_DIR}/libredwg")

add_library(corewall_dwg SHARED dwg_jni.cpp)
target_compile_features(corewall_dwg PRIVATE cxx_std_20)
target_compile_definitions(corewall_dwg PRIVATE ANDROID)
target_link_libraries(corewall_dwg PRIVATE redwg android log m)
