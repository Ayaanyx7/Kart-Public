cmake_minimum_required(VERSION 3.0)
# DO NOT CHANGE THIS SRB2 STRING! Some variable names depend on this string.
# Version change is fine.
project(SRB2
	VERSION 1.6
	LANGUAGES C)

if(${PROJECT_SOURCE_DIR} MATCHES ${PROJECT_BINARY_DIR})
	message(FATAL_ERROR "In-source builds will bring you a world of pain. Please make a separate directory to invoke CMake from.")
endif()

# Set up CMAKE path
set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} "${CMAKE_SOURCE_DIR}/cmake/Modules/")

### Useful functions

# Prepend sources with current source directory
function(prepend_sources SOURCE_FILES)
	foreach(SOURCE_FILE ${${SOURCE_FILES}})
		set(MODIFIED ${MODIFIED} ${CMAKE_CURRENT_SOURCE_DIR}/${SOURCE_FILE})
	endforeach()
	set(${SOURCE_FILES} ${MODIFIED} PARENT_SCOPE)
endfunction()

# Macro to add OSX framework
macro(add_framework fwname appname)
	find_library(FRAMEWORK_${fwname}
	NAMES ${fwname}
	PATHS ${CMAKE_OSX_SYSROOT}/System/Library
		${CMAKE_OSX_SYSROOT}/Library
		/System/Library
		/Library
	ATH_SUFFIXES Frameworks
	NO_DEFAULT_PATH)
    if( ${FRAMEWORK_${fwname}} STREQUAL FRAMEWORK_${fwname}-NOTFOUND)
        MESSAGE(ERROR ": Framework ${fwname} not found")
    else()
        TARGET_LINK_LIBRARIES(${appname} PRIVATE "${FRAMEWORK_${fwname}}/${fwname}")
        MESSAGE(STATUS "Framework ${fwname} found at ${FRAMEWORK_${fwname}}")
    endif()
endmacro()

# Macro to copy Windows DLLs to Debug/Release folder for easy debugging
# Note: this is general purpose, we could copy anything. Just using for DLLs on MSVC though
macro(copy_files_to_build_dir target dlllist_var)
	if(MSVC)
		# http://stackoverflow.com/a/26983405/3064195
		foreach(dlllist_item ${${dlllist_var}})
			get_filename_component(dllname ${dlllist_item} NAME)
			add_custom_command(TARGET ${target} POST_BUILD
				COMMAND ${CMAKE_COMMAND} -E copy_if_different
				${dlllist_item}
				$<TARGET_FILE_DIR:${target}>/${dllname}
			)
		endforeach()
	endif()
endmacro()

# bitness check
set(SRB2_SYSTEM_BITS 0)
if(CMAKE_SIZEOF_VOID_P EQUAL 8)
	message(STATUS "Target is 64-bit")
	set(SRB2_SYSTEM_BITS 64)
endif()
if(CMAKE_SIZEOF_VOID_P EQUAL 4)
	message(STATUS "Target is 32-bit")
	set(SRB2_SYSTEM_BITS 32)
endif()
if(${SRB2_SYSTEM_BITS} EQUAL 0)
	message(STATUS "Target bitness is unknown")
endif()

# OS macros
if (UNIX)
	add_definitions(-DUNIXCOMMON)
endif()

if(CMAKE_COMPILER_IS_GNUCC)
	find_program(OBJCOPY objcopy)
endif()

if(${CMAKE_SYSTEM} MATCHES "Linux")
	add_definitions(-DLINUX)
	if(${SRB2_SYSTEM_BITS} EQUAL 64)
		add_definitions(-DLINUX64)
	endif()
endif()

if(${CMAKE_SYSTEM} MATCHES "Darwin")
	add_definitions(-DMACOSX)
endif()

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin")
set(CMAKE_PDB_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin")

# Set EXE names so the assets CMakeLists can refer to its target
set(SRB2_SDL2_EXE_NAME main STRING "Executable binary output name")
set(SRB2_WIN_EXE_NAME srb2kartdd CACHE STRING "Executable binary output name for DirectDraw build")

include_directories(${CMAKE_CURRENT_BINARY_DIR}/src)

add_subdirectory(src)
#add_subdirectory(assets)


## config.h generation
set(GIT_EXECUTABLE "git" CACHE FILEPATH "Path to git binary")
include(GitUtilities)
git_latest_commit(SRB2_COMP_COMMIT "${CMAKE_SOURCE_DIR}")
git_current_branch(SRB2_GIT_BRANCH "${CMAKE_SOURCE_DIR}")
set(SRB2_COMP_BRANCH "${SRB2_GIT_BRANCH}")
set(SRB2_COMP_REVISION "${SRB2_COMP_COMMIT}")
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/src/config.h.in ${CMAKE_CURRENT_BINARY_DIR}/src/config.h)

##### PACKAGE CONFIGURATION #####

##### PACKAGE CONFIGURATION #####

set(SRB2_CPACK_GENERATOR "" CACHE STRING "Generator to use for making a package. E.g., ZIP, TGZ, DragNDrop (OSX only). Leave blank for default generator.")

if("${SRB2_CPACK_GENERATOR}" STREQUAL "")
	if("${CMAKE_SYSTEM_NAME}" MATCHES "Windows")
		set(SRB2_CPACK_GENERATOR "ZIP")
	elseif("${CMAKE_SYSTEM_NAME}" MATCHES "Linux")
		set(SRB2_CPACK_GENERATOR "TGZ")
	elseif("${CMAKE_SYSTEM_NAME}" MATCHES "Darwin")
		set(SRB2_CPACK_GENERATOR "TGZ")
	elseif("${CMAKE_SYSTEM_NAME}" MATCHES "FreeBSD")
		set(SRB2_CPACK_GENERATOR "TGZ")
	endif()
endif()

set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "Sonic Robo Blast 2 Kart" CACHE STRING "Program name for display purposes")
set(CPACK_PACKAGE_VENDOR "Kart Krew" CACHE STRING "Vendor name for display purposes")
#set(CPACK_PACKAGE_DESCRIPTION_FILE )
set(CPACK_RESOURCE_FILE_LICENSE "${CMAKE_CURRENT_SOURCE_DIR}/LICENSE")
set(CPACK_PACKAGE_VERSION_MAJOR ${SRB2_VERSION_MAJOR})
set(CPACK_PACKAGE_VERSION_MINOR ${SRB2_VERSION_MINOR})
set(CPACK_PACKAGE_VERSION_PATCH ${SRB2_VERSION_PATCH})
set(CPACK_PACKAGE_INSTALL_DIRECTORY "CMake ${CMAKE_VERSION_MAJOR}.${CMAKE_VERSION_MINOR}")
include(CPack)

add_subdirectory(thirdparty)

# ====================================================================
# Android GPU Translation Layer Setup (gl4es Bridge Configuration)
# ====================================================================
if(ANDROID)
    # Force gl4es compile profile settings for mobile translation layers
    set(NOX11 ON CACHE BOOL "" FORCE)
    set(NOEGL OFF CACHE BOOL "" FORCE)
    set(DEFAULT_ES 2 CACHE STRING "" FORCE)

    # Instruct CMake to load and compile gl4es quietly as a submodule
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/thirdparty/gl4es/CMakeLists.txt")
        add_subdirectory(thirdparty/gl4es)
    else()
        message(FATAL_ERROR "gl4es submodule missing! Run: git submodule update --init --recursive")
    endif()
endif()
