plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.luistureo.ttsbackend.TtsBackendServerKt")
}

dependencies {
    implementation("com.google.cloud:google-cloud-texttospeech:2.94.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
