plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

allprojects {
    group = "com.borderrank.battle"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
