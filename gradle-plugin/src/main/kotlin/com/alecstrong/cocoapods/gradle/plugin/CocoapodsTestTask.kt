package com.alecstrong.cocoapods.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind.EXECUTABLE

open class CocoapodsTestTask : DefaultTask() {
  internal lateinit var target: KotlinNativeTarget

  @Input var device: String = "iPhone 8"

  @TaskAction
  fun performTest() {
    val binary = target.compilations.getByName("test").getBinary(EXECUTABLE, DEBUG)
    project.exec { exec ->
      exec.commandLine("xcrun", "simctl", "spawn", device, binary.absolutePath)
    }
  }
}