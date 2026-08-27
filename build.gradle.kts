buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 provides built-in Kotlin. This explicitly aligns its KGP runtime
        // with the Compose compiler plugin version used by the app module.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
