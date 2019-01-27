package com.alecstrong.cocoapods.gradle.plugin

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetPreset
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind.EXECUTABLE

class CocoapodsTargetPreset(
  private val project: Project
) : KotlinTargetPreset<KotlinNativeTarget> {
  override fun createTarget(name: String): KotlinNativeTarget {
    val extension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    val simulator = (extension.targetFromPreset(
        extension.presets.getByName("iosX64"), name
    ) as KotlinNativeTarget).apply {
      binaries {
        framework()
      }
    }

    val device = (extension.targetFromPreset(
        extension.presets.getByName("iosArm64"), "${name}Device"
    ) as KotlinNativeTarget).apply {
      binaries {
        framework {
          embedBitcode("disable")
        }
      }
    }

    configureSources(name, device.compilations)

    project.tasks.register("${name}Test", CocoapodsTestTask::class.java) { task ->
      task.dependsOn(simulator.compilations.getByName("test").getLinkTask(EXECUTABLE, DEBUG))
      task.group = CocoapodsPlugin.GROUP
      task.description = "Run tests for target '$name' on an iOS Simulator"
      task.target = simulator
    }

    return simulator
  }

  override fun getName() = "Cocoapods"

  private fun configureSources(name: String, compilations: NamedDomainObjectContainer<KotlinNativeCompilation>) {
    val extension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

    project.afterEvaluate {
      compilations.named("main").configure { compilation ->
        extension.sourceSets.findByName("${name}Main")?.let(compilation::source)
      }
      compilations.named("test").configure { compilation ->
        extension.sourceSets.findByName("${name}Test")?.let(compilation::source)
      }
    }
  }
}
