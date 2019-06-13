package com.alecstrong.cocoapods.gradle.plugin

import groovy.lang.Closure
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetPreset
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind

open class CocoapodsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("cocoapods", CocoapodsExtension::class.java)
    val mppExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    configureTarget(project)

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
        task.wrapperExecutableName = extension.wrapperExecutableName
        task.wrapperAdditionalArgs = extension.wrapperAdditionalArgs
      }

      project.tasks.register("initializeFramework", InitializeFrameworkTask::class.java) { task ->
        task.group = GROUP
        task.description =
          "Create a dummy dynamic framework to be used during Cocoapods installation"
      }

      mppExtension.targets
          .filterIsInstance<KotlinNativeTarget>()
          .flatMap { it.binaries }
          .filter { it.outputKind == NativeOutputKind.FRAMEWORK }
          .map { it.buildType to it.linkTask }
          .groupBy({ it.first }, { it.second })
          .forEach { (buildType, compilations) ->
            project.tasks.register(
                "createIos${buildType.name()}Artifacts",
                CocoapodsCompileTask::class.java
            ) { task ->
              task.dependsOn(compilations)
              task.buildDeviceArchTarget = extension.buildDeviceArchTarget
              task.buildType = buildType
              task.compilations = compilations
              task.group = GROUP
            }
          }
    }
  }

  private fun configureTarget(project: Project) {
    val extension = project.extensions.getByType(CocoapodsExtension::class.java)
    val mppExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    project.extensions.add("targetForCocoapods", object : Closure<Unit>(extension) {
      override fun call(vararg args: Any) {
        var presets = listOf(
            mppExtension.presets.getByName("iosArm64"),
            mppExtension.presets.getByName("iosArm32"),
            mppExtension.presets.getByName("iosX64")
        )

        val name: String
        val closure: Closure<*>?

        fun closureForArg(index: Int): Closure<*>? {
          return when {
            args.size == index + 1 -> args[index] as? Closure<*>
              ?: throw IllegalArgumentException(
                  "Expected closure for argument at index $index to targetForCocoapods"
              )

            args.size > index + 1 -> throw IllegalArgumentException(
                "Expected ${index + 1} arguments to targetForCocoapods"
            )

            else -> null
          }
        }

        when (val argument = args[0]) {
          is String -> {
            name = argument
            closure = closureForArg(1)
          }

          is List<*> -> {
            presets = argument
                .map {
                  if (it !is KotlinTargetPreset<*>) {
                    throw IllegalStateException(
                        "Expected list of presets (example: [presets.iosArm64, presets.iosX64])"
                    )
                  }
                  return@map (it as KotlinTargetPreset<*>)
                }
                .ifEmpty {
                  throw IllegalArgumentException(
                      "Expected list of presets for first argument to targetForCocoapods"
                  )
                }
            name = args[1] as? String
                ?: throw IllegalArgumentException("Expected string for first argument to targetForCocoapods")
            closure = closureForArg(2)
          }
          else -> throw IllegalStateException("Expected a list of presets or a string name for the first argument to targetForCocoapods")
        }

        val preset = CocoapodsTargetPreset(project, closure, presets)
        mppExtension.targetFromPreset(preset, name)
      }
    })
  }

  companion object {
    internal const val GROUP = "cocoapods"
  }
}
