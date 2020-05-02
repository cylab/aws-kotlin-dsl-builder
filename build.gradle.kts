/*
	This file as well as the whole project is licensed under
	Apache License Version 2.0
	See LICENSE.txt for more info
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.3.0.M4"
	id("io.spring.dependency-management") version "1.0.9.RELEASE"
	kotlin("jvm") version "1.3.71"
	kotlin("plugin.spring") version "1.3.71"
}

group = "net.highteq.awssdk"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

val awssdkVersion = "2.5.54"

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "1.8"
	}
}

val sdkSources by configurations.creating { isTransitive = false }
val xmldoclet by configurations.creating

val downloadSDKSources = tasks.register<Copy>("downloadSDKSources") {
	sdkSources.resolvedConfiguration.resolvedArtifacts.forEach {
		into(File(buildDir, "sdkSources"))
		from(zipTree(it.file.absolutePath)) {
			into(it.name)
		}
	}
}

val generateSDKDocs = tasks.create("generateSDKDocs") {
	dependsOn(downloadSDKSources)
}
afterEvaluate {
	sdkSources.resolvedConfiguration.resolvedArtifacts
		.map {
			tasks.register<Javadoc>("sdkDoc-${it.name}") {
				title = "" // to prevent "invalid flag: -doctitle" with the doclet
				source = fileTree(File(buildDir, "sdkSources/${it.name}")) {
					include("**/*.java")
				}
				classpath = sourceSets["main"].compileClasspath
				setDestinationDir(File("${buildDir}/sdkDocs/${it.name}"))
				with(options as StandardJavadocDocletOptions) {
					doclet = "com.github.markusbernhardt.xmldoclet.XmlDoclet"
					docletpath = xmldoclet.files.toList()
					addStringOption("filename", "docs.xml")
					addStringOption("subpackages", "software")
					noTimestamp(false) // to prevent "invalid flag: -notimestamp" with the doclet
				}
			}
		}
		.forEach { generateSDKDocs.dependsOn(it) }
}

tasks.named<Task>("processResources") {
	dependsOn(generateSDKDocs)
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
	imports {
		mavenBom("software.amazon.awssdk:bom:$awssdkVersion")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
	implementation("io.github.microutils:kotlin-logging:1.7.9")
	implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.10.3")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.10.+")

	implementation("software.amazon.awssdk:utils")
	implementation("software.amazon.awssdk:dynamodb")
	implementation("software.amazon.awssdk:s3")

	sdkSources("software.amazon.awssdk", "dynamodb", classifier = "sources")
	sdkSources("software.amazon.awssdk", "http-client-spi", classifier = "sources")
	sdkSources("software.amazon.awssdk", "s3", classifier = "sources")

	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}

	xmldoclet("com.github.markusbernhardt:xml-doclet:1.0.5")
	xmldoclet("org.slf4j:slf4j-simple:1.7.30")
}

