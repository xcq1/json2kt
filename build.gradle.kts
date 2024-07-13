plugins {
    kotlin("jvm") version "2.0.0"
}

group = "com.xcq1.yaml2kt"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("reflect"))
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.squareup:kotlinpoet:1.17.0")

    testImplementation(kotlin("test"))
    val kotestVersion = "5.9.1"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-framework-datatest:$kotestVersion")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}