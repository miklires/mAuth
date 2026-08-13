plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
}

group = "io.github.miklires"
version = rootProject.version

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(project(":"))
    compileOnly(project(":api"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    implementation("net.dv8tion:JDA:5.6.1") {
        exclude(module = "opus-java")
    }
}

sourceSets {
    main {
        java.srcDir("../src/main/java")
        java.include("io/github/miklires/mauth/discord/Discord*.java")
    }
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("mAuth-Discord-${project.version}.jar")
        mergeServiceFiles()
        relocate("net.dv8tion.jda", "io.github.miklires.mauth.discord.libs.jda")
        relocate("okhttp3", "io.github.miklires.mauth.discord.libs.okhttp3")
        relocate("okio", "io.github.miklires.mauth.discord.libs.okio")
        minimize {
            exclude(dependency("net.dv8tion:JDA:.*"))
            exclude(dependency("com.squareup.okhttp3:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
