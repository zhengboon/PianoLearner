import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.10"
}

group = "com.synthesia.desktop"
version = "0.1.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnit()
}

// Offline pitch-detection test driver. Usage:
//   gradlew testPlayer -PmidiPath="C:\path\to\file.mid"
//   gradlew testPlayer -PmidiPath="C:\path\to\file.mid" -PmaxSlots=20
tasks.register<JavaExec>("testPlayer") {
    group = "verification"
    description = "Synthesize audio for each MIDI slot and run it through PitchDetector + PitchMatcher."
    mainClass.set("com.synthesia.desktop.tools.TestPlayerKt")
    classpath = sourceSets["main"].runtimeClasspath
    val midiPath = (project.findProperty("midiPath") as? String) ?: "src/main/resources/samples/scale.mid"
    val maxSlots = (project.findProperty("maxSlots") as? String) ?: "-"
    val sampleSize = (project.findProperty("sampleSize") as? String) ?: "-"
    val seed = (project.findProperty("seed") as? String) ?: "-"
    // Always pass 4 positional args; TestPlayer treats "-" as unset.
    args = listOf(midiPath, maxSlots, sampleSize, seed)
}

// One-shot "is the code healthy?" gate: runs JUnit + the offline TestPlayer back-to-back.
tasks.register("verify") {
    group = "verification"
    description = "Runs `test` then `testPlayer`."
    dependsOn("test", "testPlayer")
}
tasks.named("testPlayer") {
    mustRunAfter("test")
}

compose.desktop {
    application {
        mainClass = "com.synthesia.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.AppImage)
            packageName = "SynthesiaDesktop"
            packageVersion = "0.1.0"
            description = "Synthesia-style step-by-step piano practice (Stage 1)"
        }
    }
}
