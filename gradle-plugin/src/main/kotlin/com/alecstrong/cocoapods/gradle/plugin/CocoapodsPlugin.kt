package com.alecstrong.cocoapods.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

open class CocoapodsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("cocoapods", CocoapodsExtension::class.java)

    project.afterEvaluate {
      project.tasks.register("generatePodspec", GeneratePodspecTask::class.java) { task ->
        task.group = GROUP
        task.description = "Generate a podspec file for this Kotlin Native project"

        task.version = extension.version
        task.deploymentTarget = extension.deploymentTarget
        task.homepage = extension.homepage
        task.authors = extension.authors
        task.license = extension.license
        task.summary = extension.summary
        task.daemon = extension.daemon
      }

      project.tasks.register("initializeFramework", InitializeFrameworkTask::class.java) { task ->
        task.group = GROUP
        task.description = "Create a dummy dynamic framework to be used during Cocoapods installation"
      }

      createFatFrameworkTasks(project)
    }
  }

  private fun createFatFrameworkTasks(project: Project) {
    val mppExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    mppExtension.targets
        .flatMap { target ->
          target.compilations
              .filterIsInstance<KotlinNativeCompilation>()
              .filter { !it.isTestCompilation }
              .flatMap { compilation ->
                target.components.flatMap { component ->
                  (component.target as KotlinNativeTarget).binaries.map { binary ->
                    compilation to binary
                  }
                }
              }
        }
        .filter { (_, binary) ->
          binary.target.konanTarget.family == Family.IOS
        }
        .flatMap { (compilation, binary) ->
          compilation.buildTypes.map { buildType ->
            Configuration(
                buildType = buildType,
                linkTask = binary.linkTask
            )
          }
        }
        .groupBy { it.buildType }
        .forEach { (buildType, configurations) ->
          val dsym = project.registerTaskFor(
              buildType = buildType,
              configurations = configurations,
              binaryPath = "Contents/Resources/DWARF/${project.name}",
              bundleName = "${project.name}.framework.dSYM",
              taskSuffix = "DSYM"
          )
          val framework = project.registerTaskFor(
              buildType = buildType,
              configurations = configurations,
              binaryPath = project.name,
              bundleName = "${project.name}.framework",
              taskSuffix = "Framework"
          )
          project.tasks.register("createIos${buildType.name()}Artifacts") { task ->
            task.dependsOn(dsym, framework)
          }
        }
  }

  private fun Project.registerTaskFor(
    buildType: NativeBuildType,
    configurations: Collection<Configuration>,
    binaryPath: String,
    bundleName: String,
    taskSuffix: String
  ): TaskProvider<Task> {
    return tasks.register("createIos${buildType.name()}Fat$taskSuffix") { task ->
      task.dependsOn(configurations.map { it.linkTask })

      task.doLast {
        // put together final path
        val finalContainerParentDir = property(POD_FRAMEWORK_DIR_ENV) as String
        val finalContainerPath = "$finalContainerParentDir/$bundleName"
        val finalOutputPath =  "$finalContainerPath/$binaryPath"

        var deviceParentDir: String? = null

        exec { exec ->
          File(finalOutputPath).parentFile.mkdirs()

          val args = mutableListOf("-create")

          configurations.forEach { (_, linkTask) ->
            val output = linkTask.outputFile.get().parentFile.absolutePath
            val target = linkTask.compilation.target.konanTarget
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
        copy { copy ->
          copy.from(initialContainer) { from ->
            from.exclude(binaryPath)
          }
          copy.into(finalContainerPath)
        }

        // clean plist (only works for frameworks)
        val plistPath = "$finalContainerPath/Info.plist"
        if (File(plistPath).exists()) {
          exec { exec ->
            exec.executable = "/usr/libexec/PlistBuddy"
            exec.args = listOf("-c", "Delete :UIRequiredDeviceCapabilities", plistPath)
            exec.isIgnoreExitValue = true
          }

          exec { exec ->
            exec.executable = "/usr/libexec/PlistBuddy"
            exec.args = listOf("-c", "Add :CFBundleSupportedPlatforms:1 string iPhoneSimulator", plistPath)
            exec.isIgnoreExitValue = true
          }
        }
      }
    }
  }

  private data class Configuration(
    val buildType: NativeBuildType,
    val linkTask: KotlinNativeLink
  )

  private fun KonanTarget.architecture() = when (this) {
    is KonanTarget.IOS_X64 -> "x86_64"
    is KonanTarget.IOS_ARM64 -> "arm64"
    is KonanTarget.IOS_ARM32 -> "arm32"
    else -> throw IllegalStateException("Cannot collapse non-ios target $this into descriptor.")
  }

  private fun NativeBuildType.name() = when (this) {
    NativeBuildType.RELEASE -> "Release"
    NativeBuildType.DEBUG -> "Debug"
  }

  companion object {
    private const val GROUP = "cocoapods"

    internal const val POD_FRAMEWORK_DIR_ENV = "POD_FRAMEWORK_DIR"
 }
}
