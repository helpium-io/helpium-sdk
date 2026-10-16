import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
}

// Point the example at a workspace in android/local.properties (not committed):
//   helpium.widgetKey=wk_...
//   helpium.baseUrl=https://helpium.test
//   helpium.localHost=helpium.test        # emulator: resolve this host to the Mac (10.0.2.2)
//   helpium.localCa=/path/to/ca.pem       # trust a local HTTPS certificate authority (Herd/Valet)
val local = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun setting(key: String, default: String = "") = (project.findProperty(key) as String?) ?: local.getProperty(key) ?: default
val localCa = setting("helpium.localCa").takeIf { it.isNotEmpty() }?.let { file(it) }?.takeIf { it.exists() }
    ?.let { Base64.getEncoder().encodeToString(it.readBytes()) } ?: ""

android {
    namespace = "io.helpium.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.helpium.example"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "HELPIUM_WIDGET_KEY", "\"${setting("helpium.widgetKey")}\"")
        buildConfigField("String", "HELPIUM_BASE_URL", "\"${setting("helpium.baseUrl", "https://helpium.io")}\"")
        buildConfigField("String", "LOCAL_HOST", "\"${setting("helpium.localHost")}\"")
        buildConfigField("String", "LOCAL_CA_BASE64", "\"$localCa\"")
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
