buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.10.0")
    }
}

plugins {
    id("com.chaquo.python") version "17.2.0" apply false
}
