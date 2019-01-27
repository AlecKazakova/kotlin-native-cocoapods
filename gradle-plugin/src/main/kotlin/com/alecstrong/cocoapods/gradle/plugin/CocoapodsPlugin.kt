package com.alecstrong.cocoapods.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind

open class CocoapodsPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("cocoapods", CocoapodsExtension::class.java)

    project.extensions.add("cocoapodsPreset", CocoapodsTargetPreset(project))

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

      val mppExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
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
