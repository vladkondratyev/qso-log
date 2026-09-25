# Only strip unused Material icons (the icon library is ~38 MB); keep every other class exactly as is.
-keep class !androidx.compose.material.icons.**,** { *; }
-dontobfuscate
-dontoptimize
-dontwarn **
-ignorewarnings
