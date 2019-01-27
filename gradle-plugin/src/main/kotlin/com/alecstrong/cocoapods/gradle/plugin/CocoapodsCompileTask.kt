package com.alecstrong.cocoapods.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.Architecture
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
    compileFatBinary(
        binaryPath = "Contents/Resources/DWARF/${project.name}",
        bundleName = "${project.name}.framework.dSYM"
    )
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
      exec.isIgnoreExitValue = true
    }

    if (deviceParentDir == null) {
      throw IllegalStateException("You need to have a compilation target for X64")
    }

    val initialContainer = "$deviceParentDir/$bundleName"
    project.copy { copy ->
      copy.from(initialContainer) { from ->
        from.exclude(binaryPath)
      }
      copy.into(finalContainerPath)
    }

    // clean plist (only works for frameworks)
    val plistPath = "$finalContainerPath/Info.plist"
    if (File(plistPath).exists()) {
      project.exec { exec ->
        exec.executable = "/usr/libexec/PlistBuddy"
        exec.args = listOf("-c", "Delete :UIRequiredDeviceCapabilities", plistPath)
        exec.isIgnoreExitValue = true
      }

      project.exec { exec ->
        exec.executable = "/usr/libexec/PlistBuddy"
        exec.args = listOf("-c", "Add :CFBundleSupportedPlatforms:1 string iPhoneSimulator", plistPath)
        exec.isIgnoreExitValue = true
      }
    }
  }
}