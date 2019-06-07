Change Log
==========

Version 0.3.3 *(2019-06-07)*
----------------------------

* Fix: Compatibility with Kotlin 1.3.31

Version 0.3.2 *(2019-03-13)*
----------------------------

* Fix: Bundle dSYM's for release builds that supply the "-g" compiler arg
* Fix: Make the lipo task incremental

Version 0.3.1 *(2019-02-13)*
----------------------------

* Fix: Remove IphoneSimulator from generated plist

Version 0.3.0 *(2019-01-29)*
----------------------------

* New: Add custom method `targetForCocoapods` which matches Kotlin 1.3.20 multiplatform DSL
* Fix: Support arm32 ios architecture
* Fix: License is correctly string-escaped

Version 0.2.0 *(2019-01-26)*
----------------------------

* New: Add custom preset 'cocoapodsPreset' for settings up ios source set
* Fix: Compatibility with kotlin 1.3.20
