/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder

import net.highteq.awssdk.awskotlindslbuilder.source.scanSource
import net.highteq.awssdk.awskotlindslbuilder.target.*
import net.highteq.awssdk.awskotlindslbuilder.xmldoc.Docs
import net.highteq.awssdk.awskotlindslbuilder.xmldoc.parseAs
import java.io.File

fun generateDSL(xmlDoc: File, sourcePackage: String, targetPackage: String, outputDir: File) {
  outputDir.mkdirs()
  val docs = parseAs<Docs>(xmlDoc)
  val sourceModel = scanSource(sourcePackage, docs)
  val targetModel = transform(sourceModel, sourcePackage, targetPackage)
  generate(targetModel, outputDir)
}

private fun generate(model: DSLModel, outputDir: File){
  logger.info("Generating to ${outputDir.absolutePath}")

  ktFile(outputDir, model.dslMarker.packageName, model.dslMarker.name).apply {
    parentFile.mkdirs()
    logger.info("Generating ${name}")
    writeText(header().trimIndent()+"\n")
    appendText(dslMarker(model.dslMarker).trimIndent())
  }
  model.typeDLSs.forEach { typeDSLModel ->
    ktFile(outputDir, typeDSLModel.packageName, typeDSLModel.name).apply {
      parentFile.mkdirs()
      logger.info("Generating ${name}")
      writeText(header().trimIndent()+"\n")
      appendText(typeDSL(typeDSLModel).trimIndent())
    }
  }
  model.collectionDSLs.forEach { collectionDSL ->
    ktFile(outputDir, collectionDSL.packageName, collectionDSL.name).apply {
      parentFile.mkdirs()
      logger.info("Generating ${name}")
      writeText(header().trimIndent()+"\n")
      appendText(collectionDSL(collectionDSL).trimIndent())
    }
  }
}

private fun ktFile(parentDir: File, packageName: String, name: String) = File(File(parentDir, packageName.replace('.', '/')), "$name.kt")

internal fun dslProperties(dslProperties: List<DSLPropertyModel>) = dslProperties
  .map { dslProperty(it).prependIndent("  ") }.joinToString("\n")

internal fun subDSLs(subDSLs: List<SubDSLModel>) = subDSLs
  .map { subDSL(it).prependIndent("  ") }.joinToString("\n")

internal fun dslFunctions(dslFunctions: List<DSLFunctionModel>) = dslFunctions
  .map { dslFunction(it).prependIndent("  ") }.joinToString("\n")

internal fun imports(set: Set<String>) = "import " +
  set
    .map {it.replace('$', '.')}
    .sorted()
    .joinToString("\n  import ")

internal fun comment(text: String) = text.lines().joinToString("\n    * ")
internal fun annotations(set: Set<String>) = "@" + set.sorted().joinToString("\n  @")

