/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder

import mu.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import software.amazon.awssdk.core.client.builder.SdkAsyncClientBuilder
import software.amazon.awssdk.utils.builder.SdkBuilder
import java.io.File

val logger = KotlinLogging.logger {}

@SpringBootApplication
class AwsKotlinDslBuilder : CommandLineRunner {

  override fun run(vararg args: String?) {
    val projectDir = findProjectDir()
    generateDSL(
      SdkBuilder::class.java,
      "software.amazon.awssdk.services.dynamodb",
      "net.highteq.cylab.awssdk.dynamodb.kotlin.dsl",
      File(projectDir, "build/sdkDocs/dynamodb/docs.xml"),
      File(projectDir.parent, "dsls/awssdk-dynamodb-kotlin-dsl/src/generated/kotlin")
    )
    if(true) return;
    generateDSL(
      SdkBuilder::class.java,
      "software.amazon.awssdk.services.dynamodb",
//      "net.highteq.cylab.awssdk.dynamodb.kotlin.dsl",
//      File(projectDir.parent, "dsls/awssdk-dynamodb-kotlin-dsl/src/generated/kotlin")
      "net.highteq.cylab.ktdsl.awssdk.dynamodb",
      File(projectDir, "build/sdkDocs/dynamodb/docs.xml"),
      File(projectDir, "dsls/dynamodb-dsl/src/generated/kotlin")
    )
    generateDSL(
      SdkBuilder::class.java,
      "software.amazon.awssdk.http",
      "net.highteq.cylab.ktdsl.awssdk.http",
      File(projectDir, "build/sdkDocs/http-client-spi/docs.xml"),
      File(projectDir, "dsls/http-client-spi-dsl/src/generated/kotlin")
    )
    generateDSL(
      SdkAsyncClientBuilder::class.java,
      "software.amazon.awssdk.services.s3",
      "net.highteq.cylab.ktdsl.awssdk.s3",
      File(projectDir, "build/sdkDocs/s3/docs.xml"),
      File(projectDir, "dsls/s3-dsl/src/generated/kotlin")
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
