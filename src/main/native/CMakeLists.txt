cmake_minimum_required(VERSION 3.21)
project(ffi-benchmark C)

set(CMAKE_C_STANDARD 11)

include_directories(include include/linux)

add_library(ffi-benchmark SHARED library.c)
