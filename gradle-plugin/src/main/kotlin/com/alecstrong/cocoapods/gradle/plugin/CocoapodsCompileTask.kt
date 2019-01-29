package com.alecstrong.cocoapods.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.KonanTarget.IOS_ARM32
import org.jetbrains.kotlin.konan.target.KonanTarget.IOS_ARM64
import org.jetbrains.kotlin.konan.target.KonanTarget.IOS_X64
import java.io.File

open class CocoapodsCompileTask : DefaultTask() {
  internal lateinit var buildType: NativeBuildType
  internal lateinit var compilations: Collection<KotlinNativeLink>

  @TaskAction
  fun compileFatFramework() {
    compileFatBinary(
        binaryPath = project.name,
        bundleName = "${project.name}.framework"
    )
  }

  @TaskAction
  fun compileFatDsym() {
    // dsyms are not created for release builds, yet
    // https://github.com/JetBrains/kotlin-native/issues/2422
    if (buildType != NativeBuildType.RELEASE) {
      compileFatBinary(
              binaryPath = "Contents/Resources/DWARF/${project.name}",
              bundleName = "${project.name}.framework.dSYM"
      )
    }
  }

  private fun compileFatBinary(
    binaryPath: String,
    bundleName: String
  ) {
    val finalContainerPath = "${project.buildDir.path}/$bundleName"
    val finalOutputPath =  "$finalContainerPath/$binaryPath"

    var deviceParentDir: String? = null

    project.exec { exec ->
      File(finalOutputPath).parentFile.mkdirs()

      val args = mutableListOf("-create")

      compilations.forEach { compilation ->
        val output = compilation.outputFile.get().parentFile.absolutePath
        val target = compilation.binary.target.konanTarget
        if (target.architecture == Architecture.ARM64) {
          deviceParentDir = output
        }

        args.addAll(listOf(
            "-arch", target.architecture(), "$output/$bundleName/$binaryPath"
        ))
      }

      args.addAll(listOf(
          "-output", finalOutputPath
      ))

      exec.executable = "lipo"
      exec.args = args
    }.rethrowFailure().assertNormalExitValue()

    if (deviceParentDir == null) {
      throw IllegalStateException("You need to have a compilation target for X64")
    }

    val initialContainer = "$deviceParentDir/$bundleName"
    val copyResult = project.copy { copy ->
      copy.from(initialContainer) { from ->
        from.exclude(binaryPath)
      }
      copy.into(finalContainerPath)
    }

    if (!copyResult.didWork) throw IllegalStateException("Failed to copy framework.")

    // clean plist (only works for frameworks)
    val plistPath = "$finalContainerPath/Info.plist"
    if (File(plistPath).exists()) {
      project.exec { exec ->
        exec.executable = "/usr/libexec/PlistBuddy"
        exec.args = listOf("-c", "Delete :UIRequiredDeviceCapabilities", plistPath)
      }.rethrowFailure().assertNormalExitValue().exitValue

      // Clear supported platforms
      project.exec { exec ->
        exec.executable = "/usr/libexec/PlistBuddy"
        exec.args = listOf("-c", "Delete :CFBundleSupportedPlatforms:0", plistPath)
      }.rethrowFailure().assertNormalExitValue()

      compilations.map { it.binary.target.konanTarget.supportedPlatform() }.distinct()
          .forEachIndexed { index, platform ->
            project.exec { exec ->
              exec.executable = "/usr/libexec/PlistBuddy"
              exec.args = listOf("-c", "Add :CFBundleSupportedPlatforms:$index string $platform", plistPath)
            }.rethrowFailure().assertNormalExitValue()
          }
    }
  }

  private fun KonanTarget.supportedPlatform(): String = when (this) {
    IOS_X64 -> "iPhoneOS"
    IOS_ARM64, IOS_ARM32 -> "iPhoneSimulator"
    else -> throw AssertionError()
  }
}