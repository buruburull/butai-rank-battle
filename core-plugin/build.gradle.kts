plugins {
    id("com.github.johnrengelman.shadow")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation(project(":common"))
    implementation(project(":trigger-plugin"))
}

tasks.shadowJar {
    archiveBaseName.set("BorderRankBattle")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
