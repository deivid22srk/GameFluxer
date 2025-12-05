cmake_minimum_required(VERSION 3.18.1)

project("gamefluxer")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -march=native -mtune=native")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Werror")

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

add_library(
    gamefluxer
    SHARED
    native_database.cpp
    native_github_importer.cpp
    native_zip_importer.cpp
    native_json_parser.cpp
    native_network.cpp
)

find_library(log-lib log)
find_library(z-lib z)
find_library(android-lib android)

target_link_libraries(
    gamefluxer
    ${log-lib}
    ${z-lib}
    ${android-lib}
)
