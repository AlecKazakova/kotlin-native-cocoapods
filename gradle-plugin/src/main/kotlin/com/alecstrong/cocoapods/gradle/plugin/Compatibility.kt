package com.alecstrong.cocoapods.gradle.plugin

import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.konan.target.KonanTarget

internal fun NativeBuildType.name() = when (this) {
  NativeBuildType.RELEASE -> "Release"
  NativeBuildType.DEBUG -> "Debug"
}

internal fun KonanTarget.architecture() = when (this) {
  is KonanTarget.IOS_X64 -> "x86_64"
  is KonanTarget.IOS_ARM64 -> "arm64"
  is KonanTarget.IOS_ARM32 -> "armv7"
  else -> throw IllegalStateException("Cannot collapse non-ios target $this into descriptor.")
}
