plugins {
    java
}

group = "dev.disklens"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Folia bridge: compiled against the Folia/Paper API (Java 17), loaded only when Folia is detected.
val folia: SourceSet by sourceSets.creating

dependencies {
    // Core: lowest common API so one jar runs on Spigot/Paper/Purpur/Folia 1.16.5+
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")

    "foliaCompileOnly"("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    "foliaCompileOnly"(sourceSets.main.get().output)

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.named<JavaCompile>("compileFoliaJava") {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.compileTestJava {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    from(folia.output)
    archiveFileName.set("DiskLens-${project.version}.jar")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
