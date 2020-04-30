/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder

import mu.KotlinLogging
import net.highteq.awssdk.awskotlindslbuilder.source.scanSource
import net.highteq.awssdk.awskotlindslbuilder.target.transform
import net.highteq.awssdk.awskotlindslbuilder.xmldoc.Docs
import net.highteq.awssdk.awskotlindslbuilder.xmldoc.parseAs
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.io.File

val logger = KotlinLogging.logger {}

@SpringBootApplication
class AwsKotlinDslBuilder : CommandLineRunner {

  override fun run(vararg args: String?) {
    val projectDir = findProjectDir()
    generateDSL(
      File(projectDir, "build/sdkDocs/dynamodb.xml"),
      "software.amazon.awssdk.services.dynamodb",
      "net.highteq.cylab.awssdk.dynamodb.kotlin.dsl",
      File(projectDir.parent, "dsls/awssdk-dynamodb-kotlin-dsl/src/generated/kotlin")
    )
  }
}

fun findProjectDir() = File(
  AwsKotlinDslBuilder::class.java.protectionDomain.codeSource.location
    .toExternalForm()
    .substringBeforeLast("!")
    .substringBeforeLast("/")
    .substringBeforeLast("/build/classes")
    .substringAfter("file:")
)

fun main(args: Array<String>) {
  runApplication<AwsKotlinDslBuilder>(*args)
}
