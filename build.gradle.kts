plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

// Build output lives outside OneDrive: syncing locks files mid-build.
allprojects {
    layout.buildDirectory.set(file("${System.getProperty("user.home")}/.devi-build/${project.name}"))
}
