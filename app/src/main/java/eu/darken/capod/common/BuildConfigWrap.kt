package eu.darken.capod.common

import eu.darken.capod.BuildConfig


// Can't be const because that prevents them from being mocked in tests
@Suppress("MayBeConstant")
object BuildConfigWrap {
    val FLAVOR: String = BuildConfig.FLAVOR
    val BUILD_TYPE: String = BuildConfig.BUILD_TYPE
    val DEBUG: Boolean = BuildConfig.DEBUG

    val APPLICATION_ID = BuildConfig.APPLICATION_ID

    val VERSION_CODE: Long = BuildConfig.VERSION_CODE.toLong()
    val VERSION_NAME: String = BuildConfig.VERSION_NAME
    val GIT_SHA: String = BuildConfig.GITSHA

    val VERSION_DESCRIPTION_LONG: String = "v$VERSION_NAME ($VERSION_CODE) [$GIT_SHA] ${FLAVOR}_$BUILD_TYPE"
    val VERSION_DESCRIPTION_SHORT: String = "v$VERSION_NAME [$GIT_SHA] $FLAVOR"
}
