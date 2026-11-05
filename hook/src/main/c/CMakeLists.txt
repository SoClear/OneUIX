cmake_minimum_required(VERSION 3.22)
project(oneuix_network C)

add_library(oneuix_network SHARED network_speed.c traffic.c)
target_compile_features(oneuix_network PRIVATE c_std_17)
target_compile_options(oneuix_network PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
target_link_libraries(oneuix_network m)
