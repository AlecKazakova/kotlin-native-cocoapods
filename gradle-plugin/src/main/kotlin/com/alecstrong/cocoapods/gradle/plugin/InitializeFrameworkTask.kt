package com.alecstrong.cocoapods.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.InputStream
import java.io.OutputStream

open class InitializeFrameworkTask : DefaultTask() {
  @TaskAction
  fun initializeFramework() {
    val buildDir = File(project.projectDir, project.buildDir.name).apply { mkdir() }

    val frameworkName = project.findProperty(FRAMEWORK_PROPERTY) as? String ?: "nice.framework"
    val framework = File(buildDir, frameworkName).apply {
      // Reset the directory
      deleteRecursively()
      mkdir()
    }

    File(buildDir, "$frameworkName.dSYM").mkdir()

    run {
      // Copy CodeResources to _CodeSignature
      val codeSignature = File(framework, "_CodeSignature").apply { mkdir() }
      val codeResources = File(codeSignature, "CodeResources").apply { createNewFile() }
      javaClass.getResourceAsStream("/Dummy.framework/_CodeSignature/CodeResources")
          .transfer(codeResources.outputStream())
    }

    run {
      // Copy Dummy.h to {framework_name}.h
      val headers = File(framework, "Headers").apply { mkdir() }
      val header = File(headers, "Dummy.h")
      javaClass.getResourceAsStream("/Dummy.framework/Headers/Dummy.h")
          .transfer(header.outputStream())
    }

    run {
      // Copy module.modulemap to Modules
      val modules = File(framework, "Modules").apply { mkdir() }
      val modulemap = File(modules, "module.modulemap")
      javaClass.getResourceAsStream("/Dummy.framework/Modules/module.modulemap")
          .transfer(modulemap.outputStream())
    }

    run {
      // Copy Dummy executable to Framework
      val executable = File(framework, project.name).apply { createNewFile() }
      javaClass.getResourceAsStream("/Dummy.framework/Dummy")
          .transfer(executable.outputStream())
    }

    run {
      // Copy plist to Framework
      val plist = File(framework, "Info.plist").apply { createNewFile() }
      javaClass.getResourceAsStream("/Dummy.framework/Info.plist")
          .transfer(plist.outputStream())
    }
  }

  private fun InputStream.transfer(out: OutputStream): Long {
    var transferred = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var read: Int
    read = read(buffer, 0, DEFAULT_BUFFER_SIZE)
    while (read >= 0) {
      out.write(buffer, 0, read)
      transferred += read
      read = read(buffer, 0, DEFAULT_BUFFER_SIZE)
    }
    return transferred
  }

  companion object {
    internal const val FRAMEWORK_PROPERTY = "framework"
  }
}