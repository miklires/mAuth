plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
}

group = "io.github.miklires"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    implementation("net.dv8tion:JDA:5.6.1") {
        exclude(module = "opus-java")
    }
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.1.0")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("mAuth-${project.version}.jar")

        exclude("META-INF/LICENSE")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/LICENSE.md")
        exclude("META-INF/NOTICE")
        exclude("META-INF/NOTICE.txt")
        exclude("META-INF/NOTICE.md")
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/versions/**/module-info.class")
        exclude("module-info.class")

        mergeServiceFiles()

        relocate("net.dv8tion.jda", "io.github.miklires.mauth.libs.jda")
        relocate("at.favre.lib", "io.github.miklires.mauth.libs.bcrypt")
        relocate("com.zaxxer.hikari", "io.github.miklires.mauth.libs.hikari")
        relocate("com.mysql", "io.github.miklires.mauth.libs.mysql")
        relocate("okhttp3", "io.github.miklires.mauth.libs.okhttp3")
        relocate("okio", "io.github.miklires.mauth.libs.okio")

        minimize {
            exclude(dependency("net.dv8tion:JDA:.*"))
            exclude(dependency("com.mysql:mysql-connector-j:.*"))
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
