// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.4.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:1.8.10-1.0.9")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.48")
    }
}