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
    // A universal binary for macOS 13 or later. Vision text recognition with Korean needs request revision 3,
    // which is macOS 13, and ocr.swift uses nothing newer. Each architecture is built apart, then joined.
    val out = binary.get().asFile.path
    commandLine(
        "sh", "-c",
        "set -e && swiftc -O -target arm64-apple-macos13 \"\$1\" -o \"\$2.arm64\" && " +
            "swiftc -O -target x86_64-apple-macos13 \"\$1\" -o \"\$2.x86_64\" && " +
            "lipo -create -output \"\$2\" \"\$2.arm64\" \"\$2.x86_64\" && rm -f \"\$2.arm64\" \"\$2.x86_64\"",
        "sh", source.asFile.path, out,
    )
}

tasks.jar {
    archiveFileName = "DayFlow.jar"
    manifest {
        attributes("Main-Class" to "dayflow.ui.DayFlowApp")
    }
}

tasks.javadoc {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        charSet = "UTF-8"
        docEncoding = "UTF-8"
        windowTitle = "DayFlow API"
        docTitle = "DayFlow API"
        addStringOption("Xmaxwarns", "1000")
    }
}

// Puts the text recognition helper next to DayFlow.jar so that running from build/libs finds it.
// Off macOS there is no swiftc, so the copy is skipped and the build still passes.
val copyHelper = tasks.register<Copy>("copyHelper") {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    dependsOn("ocrHelper")
    from(layout.buildDirectory.file("dayflow-ocr"))
    into(layout.buildDirectory.dir("libs"))
}

tasks.assemble {
    if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
        dependsOn(copyHelper)
    }
}
