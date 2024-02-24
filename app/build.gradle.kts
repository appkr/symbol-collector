plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("de.m3y.kformat:kformat:0.10")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("dev.appkr.symbolcollector.AppKt")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "dev.appkr.symbolcollector.AppKt")
    }
}

tasks.shadowJar {
    archiveBaseName.set("app")
    manifest {
        attributes("Main-Class" to "dev.appkr.symbolcollector.AppKt")
    }
    mergeServiceFiles {
        include("META-INF/*.kotlin_module")
    }
}
