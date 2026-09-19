plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "2.2.21"
}

group = "retrofit2.http.options"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("commons-io:commons-io:2.16.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
}

// Bytecode 1.8 keeps the plugin usable from any modern (or old) Gradle/JDK.
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// Unit Tests:
tasks.named<Test>("test") {
    useJUnit()
    maxHeapSize = "1G"
}

gradlePlugin {
    website = "https://github.com/christopherney/Enigma"
    vcsUrl = "https://github.com/christopherney/Enigma"
    plugins {
        create("enigmaPlugin") {
            id = "retrofit2.http.options"
            displayName = "Enigma Plugin"
            description = "Obfuscator Strings Encryption plugin for Android/Java/Kotlin apps."
            implementationClass = "retrofit2.http.options.EnigmaPlugin"
        }
    }
}
