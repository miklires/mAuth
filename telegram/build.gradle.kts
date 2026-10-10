plugins { java }

group = "io.github.miklires"
version = rootProject.version

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

repositories { maven("https://repo.papermc.io/repository/maven-public/") }

dependencies {
    compileOnly(project(":"))
    compileOnly(project(":api"))
    compileOnly("io.papermc.paper:paper-api:26.3.build.169-beta")
}

tasks.jar { archiveFileName.set("mAuth-Telegram-${project.version}.jar") }

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
