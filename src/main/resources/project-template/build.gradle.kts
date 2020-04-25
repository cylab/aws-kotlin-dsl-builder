plugins {
    kotlin("jvm") version "1.3.71"
}

val awssdkVersion = "2.5.54"
val awssdkService = "project-template"

group = "net.highteq.cylab"
version = "${awssdkVersion}-ALPHA"
java.sourceCompatibility = JavaVersion.VERSION_8

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("software.amazon.awssdk:utils")
    implementation("software.amazon.awssdk:$awssdkService")
}

dependencyManagement {
    imports {
        mavenBom("software.amazon.awssdk:bom:$awssdkVersion")
    }
}
