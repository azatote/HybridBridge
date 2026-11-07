import com.google.protobuf.gradle.*
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
}

val schemaDirectory = layout.buildDirectory.dir("meshtastic-protos")
val licenseDirectory = layout.buildDirectory.dir("license-assets")
val prepareMeshtasticProtos by tasks.registering {
    val revision = "6b1ded439633cd03d4af85b44231b91d1d106278"
    inputs.property("revision", revision)
    outputs.dir(schemaDirectory)
    outputs.dir(licenseDirectory)
    inputs.file(rootProject.file("LICENSE"))
    doLast {
        val schemas = listOf(
            "admin", "apponly", "atak", "cannedmessages", "channel", "clientonly",
            "config", "connection_status", "device_ui", "deviceonly", "interdevice",
            "localonly", "mesh", "module_config", "mqtt", "paxcount", "portnums",
            "powermon", "remote_hardware", "rtttl", "serial_hal", "storeforward",
            "telemetry", "xmodem"
        ).map { "meshtastic/$it.proto" } + "nanopb.proto"
        for (path in schemas) {
            val target = schemaDirectory.get().file(path).asFile
            target.parentFile.mkdirs()
            URI("https://raw.githubusercontent.com/meshtastic/protobufs/$revision/$path")
                .toURL().openStream().use { source -> target.outputStream().use { source.copyTo(it) } }
        }
        val gpl = licenseDirectory.get().file("licenses/GPL-3.0.txt").asFile
        gpl.parentFile.mkdirs()
        rootProject.file("LICENSE").copyTo(gpl, overwrite = true)
        val protobufLicense = licenseDirectory.get().file("licenses/Protobuf-License.txt").asFile
        URI("https://raw.githubusercontent.com/protocolbuffers/protobuf/v25.5/LICENSE")
            .toURL().openStream().use { source -> protobufLicense.outputStream().use { source.copyTo(it) } }
    }
}

android {
    namespace = "fr.hybridbridge.app"
    compileSdk = 35
    defaultConfig { applicationId = "fr.hybridbridge.app"; minSdk = 26; targetSdk = 35; versionCode = 4; versionName = "0.4" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").proto {
        srcDir(schemaDirectory)
        exclude("nanopb.proto")
    }
    sourceSets.getByName("main").assets.srcDir(licenseDirectory)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(prepareMeshtasticProtos)
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.5" }
    generateProtoTasks {
        all().configureEach {
            dependsOn(prepareMeshtasticProtos)
            builtins { maybeCreate("java").option("lite") }
        }
    }
}

dependencies {
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")
    testImplementation("junit:junit:4.13.2")
}

