plugins {
    id("com.android.application")
}

android {
    namespace = "io.helpium.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.helpium.example"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Set these in ~/.gradle/gradle.properties or with -P to point at your workspace.
        buildConfigField("String", "HELPIUM_WIDGET_KEY", "\"${project.findProperty("helpium.widgetKey") ?: ""}\"")
        buildConfigField("String", "HELPIUM_BASE_URL", "\"${project.findProperty("helpium.baseUrl") ?: "https://helpium.io"}\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(project(":helpium"))
}
