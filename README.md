# kotlin-native-cocoapods

A Gradle plugin which handles creating a podspec for a local Kotlin/Native project. The generated podspec properly integrates your project with cocoapods, and release/debug fat binaries will be created and linked when you compile the xcode project. Using this plugin means you do not need to manually set up xcode or the `packForXcode` task as described in the [documentation](https://kotlinlang.org/docs/tutorials/native/mpp-ios-android.html#creating-ios-application).

### Setup

```groovy
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath 'com.alecstrong:cocoapods-gradle-plugin:0.2.0'
  }
}

// Cocoapods plugin is only applicable for multiplatform projects with Kotlin/Native
apply plugin: 'org.jetbrains.kotlin.multiplatform'
apply plugin: 'com.alecstrong.cocoapods'

// Optional configuration of plugin.
cocoapods {
  version = "1.0.0-LOCAL" // Defaults to "1.0.0-LOCAL"
  homepage = www.mywebsite.com  // Default to empty
  deploymentTarget = "10.0" // Defaults to "10.0"
  authors = "Ben Asher" // Defaults to empty
  license = "..." // Defaults to empty
  summary = "..." // Defaults to empty
  daemon = true // Defaults to false
}
```

From this the plugin will generate a task `generatePodspec` to create a `.podspec` file in that directory for the kotlin native project.

```
> Code/Kotlin/gradlew -p Code/Kotlin :common:generatePodspec
```

The above command is assuming a module structure where `Code/Kotlin` is the root of your gradle project, and `common` is a Kotlin Multiplatform module with iOS targets.

Then in your `Podfile` you can reference the module:

```ruby
source 'https://git.sqcorp.co/scm/ios/cocoapodspecs.git'

...

target 'MyProject' do
  ...
  pod 'common', :path => 'Code/Kotlin/common'
end
```

And that's it! From your iOS project you will be able to `import common`.

### Custom Preset

The plugin also includes a custom preset which sets up the necessary x64/arm64 source sets:

```groovy
kotlin {
  targetForCocoapods('ios')
  
  sourceSets {
    iosMain { ... }
    iosTest { ... }
  }
}
```

Doing this will also generate a `iosTest` task for running tests against this target.

### Custom architectures

By default this packages a fat binary with x64, arm64, and arm32 architectures inside. To override this behavior pass a list of presets into the `targetForCocoapods` method:

```groovy
kotlin {
  targetForCocoapods([presets.iosArm64, presets.iosX64], 'ios')
}
```

Its also possible to use the full 1.3.20 DSL to customize the targets:


```groovy
kotlin {
  targetForCocoapods([presets.iosArm64, presets.iosX64], 'ios') {
    compilations.main.extraOpts '-module-name', 'CP'
  }
}
```
