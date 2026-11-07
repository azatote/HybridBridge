import com.google.protobuf.gradle.*
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("com.google.protobuf")
}

val bitchatProtocolDirectory = layout.buildDirectory.dir("bitchat-protocol-source")
val prepareBitchatProtocol by tasks.registering {
    val revision = "20501fdfe0d56c7705c377d4a263b01d8f38eeeb"
    inputs.property("revision", revision)
    outputs.dir(bitchatProtocolDirectory)
    doLast {
        val paths = listOf(
            "protocol/BinaryProtocol.kt", "protocol/CompressionUtil.kt",
            "protocol/DecompressionResourcePool.kt", "protocol/MessagePadding.kt",
            "protocol/PacketPayloadLimits.kt", "util/AppConstants.kt"
        )
        for (path in paths) {
            val target = bitchatProtocolDirectory.get().file("com/bitchat/android/$path").asFile
            target.parentFile.mkdirs()
            URI("https://raw.githubusercontent.com/permissionlesstech/bitchat-android/$revision/app/src/main/java/com/bitchat/android/$path")
                .toURL().openStream().use { source -> target.outputStream().use { source.copyTo(it) } }
        }
        val license = bitchatProtocolDirectory.get().file("Bitchat-LICENSE.md").asFile
        URI("https://raw.githubusercontent.com/permissionlesstech/bitchat-android/$revision/LICENSE.md")
            .toURL().openStream().use { source -> license.outputStream().use { source.copyTo(it) } }
    }
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
        val descriptor = schemaDirectory.get().file("google/protobuf/descriptor.proto").asFile
        descriptor.parentFile.mkdirs()
        URI("https://raw.githubusercontent.com/protocolbuffers/protobuf/v25.5/src/google/protobuf/descriptor.proto")
            .toURL().openStream().use { source -> descriptor.outputStream().use { source.copyTo(it) } }
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
    defaultConfig { applicationId = "fr.hybridbridge.app"; minSdk = 26; targetSdk = 35; versionCode = 5; versionName = "0.4.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("main").proto {
        srcDir(schemaDirectory)
        exclude("google/protobuf/descriptor.proto")
    }
    sourceSets.getByName("main").assets.srcDir(licenseDirectory)
    sourceSets.getByName("main").java.srcDir(bitchatProtocolDirectory)
}

tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    dependsOn(prepareBitchatProtocol)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(prepareMeshtasticProtos)
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.5" }
    generateProtoTasks {
        all().configureEach {
            dependsOn(prepareMeshtasticProtos)
            builtins { maybeCreate("java") }
        }
    }
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:3.25.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

