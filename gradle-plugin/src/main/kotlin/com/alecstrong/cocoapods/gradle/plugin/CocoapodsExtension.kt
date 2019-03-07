package com.alecstrong.cocoapods.gradle.plugin

open class CocoapodsExtension(
  var version: String = "1.0.0-LOCAL",
  var homepage: String? = null,
  var deploymentTarget: String = "10.0",
  var authors: String? = null,
  var license: String? = null,
  var summary: String? = null,
  var daemon: Boolean = false,
  var wrapperExecutableName: String? = null,
  var wrapperAdditionalArgs: String? = null
)