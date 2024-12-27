plugins {
    kotlin("jvm") version "2.0.20"
}

group = "ru.transaero21"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:4.28.3")
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(8)
}