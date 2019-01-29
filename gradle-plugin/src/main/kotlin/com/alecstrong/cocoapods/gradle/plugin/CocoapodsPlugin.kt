package com.alecstrong.cocoapods.gradle.plugin

import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind

open class CocoapodsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("cocoapods", CocoapodsExtension::class.java)
    val mppExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    var architectures: List<String>? = null

    project.extensions.add("targetForCocoapods", object : Closure<Unit>(extension) {
      override fun call(vararg args: Any) {
        architectures = extension.architectures
        val name = args[0] as? String
            ?: throw IllegalArgumentException("Expected string for first argument to targetForCocoapods")
        val closure: Closure<*>? = when {
          args.size == 2 -> args[1] as? Closure<*>
              ?: throw IllegalArgumentException("Expected closure for second argument to targetForCocoapods")
          args.size > 2 -> throw IllegalArgumentException("Expected two arguments to targetForCocoapods")
          else -> null
        }
        val preset = CocoapodsTargetPreset(project, closure)
        mppExtension.targetFromPreset(preset, name)
      }
    })

    project.afterEvaluate {
      if (architectures != null && (extension.architectures != architectures)) {
        throw GradleException("""
          |If specifying architectures in the cocoapods configuration, is must be above the kotlin
          |configuration:
          |
          |cocoapods {
          |  architectures = ${extension.architectures}
          |}
          |
          |kotlin {
          |  ...
          |}
        """.trimMargin())
      }

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

      mppExtension.targets
          .filterIsInstance<KotlinNativeTarget>()
          .flatMap { it.binaries }
          .filter { it.outputKind == NativeOutputKind.FRAMEWORK }
          .map { it.buildType to it.linkTask }
          .groupBy({ it.first }, { it.second })
          .forEach { (buildType, compilations) ->
            project.tasks.register("createIos${buildType.name()}Artifacts",
                CocoapodsCompileTask::class.java) { task ->
              task.dependsOn(compilations)
              task.buildType = buildType
              task.compilations = compilations
              task.group = GROUP
            }
          }
    }
  }

  companion object {
    internal const val GROUP = "cocoapods"
 }
}
