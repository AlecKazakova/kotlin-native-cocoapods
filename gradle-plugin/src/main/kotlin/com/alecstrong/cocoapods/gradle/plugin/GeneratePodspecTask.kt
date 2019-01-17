package com.alecstrong.cocoapods.gradle.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.utils.fileUtils.descendantRelativeTo
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
      |  framework_dir = "${'$'}{PODS_TARGET_SRCROOT}/${project.buildDir.name}"
      |
      |  spec.name                     = '${project.name}'
      |  spec.version                  = '$version'
      |  ${if (homepage != null) "spec.homepage                 = '$homepage'" else "# homepage can be provided from gradle"}
      |  spec.source                   = { :git => "Not Published", :tag => "Cocoapods/#{spec.name}/#{spec.version}" }
      |  spec.ios.deployment_target    = '$deploymentTarget'
      |  ${if (authors != null) "spec.authors                  = '$authors'" else "# authors can be provided from gradle"}
      |  ${if (license != null) "spec.license                  = $license" else "# license can be provided from gradle"}
      |  ${if (summary != null) "spec.summary                  = '$summary'" else "# summary can be provided from gradle"}
      |  spec.ios.vendored_frameworks  = "${project.buildDir.name}/#{spec.name}.framework"
      |
      |  preserve_path_patterns = ['*.gradle', 'gradle*', '*.properties', 'src/**/*.*']
      |  spec.preserve_paths = preserve_path_patterns + ['src/**/*'] # also include empty dirs for full hierarchy
      |
      |  framework_loc = '${'$'}{PODS_TARGET_SRCROOT}/${project.buildDir.name}'
      |
      |  spec.prepare_command = <<-SCRIPT
      |    set -ev
      |    $gradlew -P${InitializeFrameworkTask.FRAMEWORK_PROPERTY}=#{spec.name}.framework initializeFramework --stacktrace
      |  SCRIPT
      |
      |  spec.script_phases = [
      |    {
      |      :name => 'Build ${project.name}',
      |      :input_files => Dir.glob(preserve_path_patterns).map {|f| "${'$'}(PODS_TARGET_SRCROOT)/#{f}"},
      |      :output_files => ["#{framework_dir}/#{spec.name}.framework", "#{framework_dir}/#{spec.name}.dSYM"],
      |      :shell_path => '/bin/sh',
      |      :script => <<-SCRIPT
      |        set -ev
      |        REPO_ROOT=`realpath "${'$'}PODS_TARGET_SRCROOT"`
      |        rm -rf "${'$'}{REPO_ROOT}/#{spec.name}.framework"*
      |        ${'$'}REPO_ROOT/$gradlew -P${CocoapodsPlugin.POD_FRAMEWORK_DIR_ENV}=`realpath "#{framework_dir}"` -p "${'$'}REPO_ROOT" "createIos${'$'}{CONFIGURATION}Artifacts"
      |      SCRIPT
      |    }
      |  ]
      |end
    """.trimMargin())
  }
}