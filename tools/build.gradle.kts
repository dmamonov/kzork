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

// Maintenance tasks use exactly the same formatter as game document serialization.
val markdownRoot = rootProject.projectDir.absolutePath
tasks.register<JavaExec>("formatMarkdown") {
    group = "formatting"
    description = "Format repository Markdown: 120 characters and one sentence per source line."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("kzork.tools.FormatMarkdownKt")
    args(markdownRoot)
}
tasks.register<JavaExec>("checkMarkdown") {
    group = "verification"
    description = "Check repository Markdown formatting without writing files."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("kzork.tools.FormatMarkdownKt")
    args(markdownRoot, "--check")
}
tasks.named("check") {
    dependsOn("checkMarkdown")
}
