plugins {
    java
    id("com.diffplug.spotless") version "8.10.4"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// The text recognition helper uses the macOS Vision framework, so it only builds on macOS.
tasks.register<Exec>("ocrHelper") {
    group = "build"
    description = "Builds the macOS text recognition helper into build/dayflow-ocr."
    val source = layout.projectDirectory.file("native/macos/ocr.swift")
    val binary = layout.buildDirectory.file("dayflow-ocr")
    inputs.file(source)
    outputs.file(binary)
    commandLine("swiftc", "-O", source.asFile.path, "-o", binary.get().asFile.path)
}
