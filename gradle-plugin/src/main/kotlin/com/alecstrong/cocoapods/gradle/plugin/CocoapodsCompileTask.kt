package com.alecstrong.cocoapods.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.Architecture
import java.io.File

open class CocoapodsCompileTask : DefaultTask() {
  @InputFiles lateinit var inputs: FileCollection

  @Input internal lateinit var buildDeviceArchTarget: Architecture
  @Input internal var buildType: NativeBuildType? = null
    set(value) {
      val outputs = mutableListOf("${project.buildDir.path}/${project.name}.framework")
      if (hasDsyms()) outputs.add("${project.buildDir.path}/${project.name}.framework.dSYM")
      outputDirectories = project.files(*outputs.toTypedArray())
      field = value
    }

  @OutputDirectories lateinit var outputDirectories: FileCollection

  internal var compilations: Collection<KotlinNativeLink> = emptyList()
    set(value) {
      inputs = project.files(*value.map { it.binary.outputFile }.toTypedArray())
      field = value
    }

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
    if (hasDsyms()) {
      compileFatBinary(
          binaryPath = "Contents/Resources/DWARF/${project.name}",
          bundleName = "${project.name}.framework.dSYM"
      )
    }
  }

  private fun hasDsyms(): Boolean {
    return compilations.all { it.binary.debuggable || it.binary.freeCompilerArgs.contains("-g") }
  }

  private fun compileFatBinary(
    binaryPath: String,
    bundleName: String
  ) {
    logger.info("Creating fat binary for $binaryPath $bundleName")
    val finalContainerPath = "${project.buildDir.path}/$bundleName"
    val finalOutputPath =  "$finalContainerPath/$binaryPath"

    var deviceParentDir: String? = null

    project.exec { exec ->
      File(finalOutputPath).parentFile.mkdirs()

      val args = mutableListOf("-create")

      compilations.forEach { compilation ->
        val output = compilation.outputFile.get().parentFile.absolutePath
        val target = compilation.binary.target.konanTarget
        if (target.architecture == buildDeviceArchTarget) {
          logger.info("Selected device arch target: ${target.architecture}")
          deviceParentDir = output
        }

        logger.info("Lipo'ing for arch ${target.architecture} with path $output/$bundleName/$binaryPath")
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

      // only add iPhoneOS as supported platform
      project.exec { exec ->
        exec.executable = "/usr/libexec/PlistBuddy"
        exec.args = listOf("-c", "Add :CFBundleSupportedPlatforms:0 string iPhoneOS", plistPath)
      }.rethrowFailure().assertNormalExitValue()
    }
  }
}