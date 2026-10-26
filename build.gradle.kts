plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)

    // Apply when the application entry point is implemented.
    alias(libs.plugins.spring.boot) apply false
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.kotlin.bom))
    implementation(libs.bundles.backend)
    implementation(libs.bundles.data.parsers)

    runtimeOnly(libs.postgresql)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events("failed", "skipped")
    }
}
