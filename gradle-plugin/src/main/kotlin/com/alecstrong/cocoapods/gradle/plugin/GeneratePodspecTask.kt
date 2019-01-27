package com.alecstrong.cocoapods.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

open class GeneratePodspecTask : DefaultTask() {
  // Extension parameters
  @Input lateinit var version: String
  @Input lateinit var deploymentTarget: String

  // Optional parameters
  var homepage: String? = null
  var authors: String? = null
  var license: String? = null
  var summary: String? = null
  var daemon: Boolean = false

  @TaskAction
  fun generatePodspec() {
    var current = project.projectDir
    var gradlew: String
    if (current == project.rootDir) {
      gradlew = "./gradlew"
    } else {
      gradlew = "gradlew"
      while (current != project.rootDir) {
        gradlew = "../$gradlew"
        current = current.parentFile
      }
    }

    File(project.projectDir, "${project.name}.podspec").writeText("""
      |Pod::Spec.new do |spec|
      |  spec.name                     = '${project.name}'
      |  spec.version                  = '$version'
      |  ${if (homepage != null) "spec.homepage                 = '$homepage'" else "# homepage can be provided from gradle"}
      |  spec.source                   = { :git => "Not Published", :tag => "Cocoapods/#{spec.name}/#{spec.version}" }
      |  spec.ios.deployment_target    = '$deploymentTarget'
      |  ${if (authors != null) "spec.authors                  = '$authors'" else "# authors can be provided from gradle"}
      |  ${if (license != null) "spec.license                  = '$license'" else "# license can be provided from gradle"}
      |  ${if (summary != null) "spec.summary                  = '$summary'" else "# summary can be provided from gradle"}
      |  spec.ios.vendored_frameworks  = "${project.buildDir.name}/#{spec.name}.framework"
      |
      |  spec.prepare_command = <<-SCRIPT
      |    set -ev
      |    $gradlew ${if (daemon) "" else "--no-daemon" } -P${InitializeFrameworkTask.FRAMEWORK_PROPERTY}=#{spec.name}.framework initializeFramework --stacktrace
      |  SCRIPT
      |
      |  spec.script_phases = [
      |    {
      |      :name => 'Build ${project.name}',
      |      :shell_path => '/bin/sh',
      |      :script => <<-SCRIPT
      |        set -ev
      |        REPO_ROOT=`realpath "${'$'}PODS_TARGET_SRCROOT"`
      |        rm -rf "${'$'}{REPO_ROOT}/#{spec.name}.framework"*
      |        ${'$'}REPO_ROOT/$gradlew ${if (daemon) "" else "--no-daemon" } -p "${'$'}REPO_ROOT" "createIos${'$'}{CONFIGURATION}Artifacts"
      |      SCRIPT
      |    }
      |  ]
      |end
    """.trimMargin())
  }
}