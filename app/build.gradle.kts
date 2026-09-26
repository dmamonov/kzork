plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

val ktorVersion = "3.2.3"
dependencies {
    implementation(project(":model"))
    implementation(project(":tools"))
    implementation(libs.kotlinxSerialization)
    implementation(libs.kotlinxCoroutines)
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
application { mainClass = "kzork.app.AppKt" }
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
tasks.test { systemProperty("fixture.directory", rootProject.file("games/ice_dungeon").absolutePath) }
