plugins {
    id("buildsrc.convention.kotlin-jvm")
}

group = "org.example"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":model"))
    implementation(libs.kotlinxSerialization)
    implementation("org.yaml:snakeyaml:2.4")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("fixture.directory", rootProject.file("games/ice_dungeon").absolutePath)
}
