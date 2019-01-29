package com.alecstrong.cocoapods.gradle.plugin

import groovy.lang.Closure
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetPreset
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind.EXECUTABLE
import org.jetbrains.kotlin.konan.target.KonanTarget.IOS_ARM32
import org.jetbrains.kotlin.konan.target.KonanTarget.IOS_ARM64
import org.jetbrains.kotlin.konan.target.KonanTarget.IOS_X64

class CocoapodsTargetPreset(
  private val project: Project,
  private val configure: Closure<*>?
) : KotlinTargetPreset<KotlinNativeTarget> {
  override fun createTarget(name: String): KotlinNativeTarget {
    val extension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    val cocoapodsExtension = project.extensions.getByType(CocoapodsExtension::class.java)

    val targets = cocoapodsExtension.architectures.mapIndexed { index, architecture ->
      val target = extension.targetFromPreset(
              extension.presets.getByName(architecture), if (index == 0) name else architecture
      ).configureTarget()
      if (index > 0) configureSources(name, target.compilations)
      return@mapIndexed target
    }

    val simulator = targets.find { it.konanTarget == IOS_X64 }

    if (simulator != null) {
      project.tasks.register("${name}Test", CocoapodsTestTask::class.java) { task ->
        task.dependsOn(simulator.compilations.getByName("test").getLinkTask(EXECUTABLE, DEBUG))
        task.group = CocoapodsPlugin.GROUP
        task.description = "Run tests for target '$name' on an iOS Simulator"
        task.target = simulator
      }
    } else {
      project.logger.warn("No architecture provided for framework to be run on a simulator." +
          " This means no test task was added.")
    }

    return targets.first()
  }

  override fun getName() = "Cocoapods"

  private fun KotlinTarget.configureTarget(): KotlinNativeTarget {
    if (this !is KotlinNativeTarget) throw kotlin.IllegalStateException()
    ConfigureUtil.configure(configure, this)
    if (binaries.findFramework(NativeBuildType.DEBUG) == null ||
        binaries.findFramework(NativeBuildType.RELEASE) == null) {
      when (konanTarget) {
        IOS_ARM32, IOS_ARM64 -> {
          binaries {
            framework()
          }
        }
        IOS_X64 -> {
          binaries {
            framework {
              embedBitcode("disable")
            }
          }
        }
        else -> throw IllegalStateException("Unsupported target $konanTarget")
      }
    }
    return this
  }

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
