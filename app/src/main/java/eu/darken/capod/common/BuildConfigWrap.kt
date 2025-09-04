package eu.darken.capod.common

import eu.darken.capod.BuildConfig


// Can't be const because that prevents them from being mocked in tests
@Suppress("MayBeConstant")
object BuildConfigWrap {
    val DEBUG: Boolean = BuildConfig.DEBUG

    val BUILD_TYPE: BuildType = when (val typ = BuildConfig.BUILD_TYPE) {
        "debug" -> BuildType.DEV
        "beta" -> BuildType.BETA
        "release" -> BuildType.RELEASE
        else -> throw IllegalArgumentException("Unknown buildtype: $typ")
    }

    enum class BuildType {
        DEV,
        BETA,
        RELEASE,
        ;
    }

    val FLAVOR: Flavor = when (val flav = BuildConfig.FLAVOR) {
        "gplay" -> Flavor.GPLAY
        "foss" -> Flavor.FOSS
        else -> throw IllegalStateException("Unknown flavor: $flav")
    }

    enum class Flavor {
        GPLAY,
        FOSS,
        ;
    }

    val APPLICATION_ID: String = BuildConfig.APPLICATION_ID

    val VERSION_CODE: Long = BuildConfig.VERSION_CODE.toLong()
    val VERSION_NAME: String = BuildConfig.VERSION_NAME
    val GIT_SHA: String = "unknown" // TODO: Re-add GITSHA to build configuration

    val VERSION_DESCRIPTION_LONG: String = "v$VERSION_NAME ($VERSION_CODE) [$GIT_SHA] ${FLAVOR}_$BUILD_TYPE"
    val VERSION_DESCRIPTION_SHORT: String = "v$VERSION_NAME [$GIT_SHA] $FLAVOR"
    val VERSION_DESCRIPTION_TINY: String = "v$VERSION_NAME"
}
