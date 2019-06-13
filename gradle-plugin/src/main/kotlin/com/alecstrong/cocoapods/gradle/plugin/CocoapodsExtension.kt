package com.alecstrong.cocoapods.gradle.plugin

import org.jetbrains.kotlin.konan.target.Architecture

open class CocoapodsExtension(
  var version: String = "1.0.0-LOCAL",
  var deploymentTarget: String = "10.0",
  var buildDeviceArchTarget: Architecture = Architecture.ARM64,
  var homepage: String? = null,
  var authors: String? = null,
  var license: String? = null,
  var summary: String? = null,
  var daemon: Boolean = false,
  var wrapperExecutableName: String? = null,
  var wrapperAdditionalArgs: String? = null
)