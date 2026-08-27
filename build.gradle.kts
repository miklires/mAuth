plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
    id("com.modrinth.minotaur") version "2.9.0"
}

group = "io.github.miklires"
version = "1.0.1"

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN") ?: "")
    projectId.set(System.getenv("MODRINTH_PROJECT_ID") ?: "")
    versionNumber.set(project.version.toString())
    versionName.set("mAuth ${project.version}")
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    additionalFiles {
        other(layout.projectDirectory.file("velocity/build/libs/mAuth-Velocity-${project.version}.jar"))
    }
    gameVersions.addAll("26.2")
    loaders.addAll("paper", "purpur", "folia", "velocity")
    changelog.set(provider { file("CHANGELOG.md").readText() })
    syncBodyFrom.set(file("README.md").readText())
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    implementation(project(":api"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    compileOnly("me.clip:placeholderapi:2.11.7")

    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.bstats:bstats-bukkit:3.1.0")
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("com.h2database:h2:2.3.232")
    compileOnly("org.xerial:sqlite-jdbc:3.47.1.0")
    compileOnly("com.mysql:mysql-connector-j:9.1.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    compileOnly("org.postgresql:postgresql:42.7.12")
    compileOnly("com.maxmind.geoip2:geoip2:5.2.0")
}

tasks {
    jar {
        archiveClassifier.set("plain")
    }

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

        relocate("at.favre.lib", "io.github.miklires.mauth.libs.bcrypt")
        relocate("org.bouncycastle", "io.github.miklires.mauth.libs.bouncycastle")
        relocate("org.bstats", "io.github.miklires.mauth.libs.bstats")
        relocate("okhttp3", "io.github.miklires.mauth.libs.okhttp3")
        relocate("okio", "io.github.miklires.mauth.libs.okio")

        minimize {
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

    test {
        useJUnitPlatform()
    }
}

sourceSets {
    main {
        java.exclude("io/github/miklires/mauth/discord/DiscordBot.java")
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testCompileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    testRuntimeOnly("com.maxmind.geoip2:geoip2:5.2.0")
}
