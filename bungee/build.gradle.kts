plugins {
    java
}

group = "io.github.miklires"
version = rootProject.version

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://libraries.minecraft.net")
}

dependencies {
    compileOnly("net.md-5:bungeecord-api:1.21-R0.3")
}

tasks {
    jar {
        archiveFileName.set("mAuth-Bungee-${project.version}.jar")
    }
    processResources {
        filesMatching("bungee.yml") {
            expand("version" to project.version)
        }
    }
}
