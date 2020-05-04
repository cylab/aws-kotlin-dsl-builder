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
import kotlin.text.RegexOption.MULTILINE

fun generateDSL(superType: Class<*>, sourcePackage: String, targetPackage: String, xmlDoc: File, outputDir: File) {
  val docs = parseAs<Docs>(xmlDoc)
  val sourceModel = scanSource(superType, sourcePackage, docs)
  val targetModel = transform(sourceModel, sourcePackage, targetPackage)

  logger.info("Generating to ${outputDir.absolutePath}")
  outputDir.deleteRecursively()
  outputDir.mkdirs()
  generateKotlin(::dslMarker, targetModel.dslMarker, outputDir )
  generateKotlin(::collectionDSL, targetModel.collectionDSLs, outputDir)
  generateKotlin(::mapDSL, targetModel.mapDSLs, outputDir)
  generateKotlin(::typeDSL, targetModel.typeDLSs, outputDir)
}

private fun <T : DSLFileModel> generateKotlin(generator: (T) -> String, dsls: Collection<T>, parentDir: File) {
  dsls.sortedBy{ it.name }.forEach { generateKotlin(generator, it, parentDir) }
}

private fun <T : DSLFileModel> generateKotlin(generator: (T) -> String, dsl: T, parentDir: File) {
  File(File(parentDir, dsl.packageName.replace('.', '/')), "${dsl.name}.kt").apply {
    parentFile.mkdirs()
    logger.info("Generating $name")
    writeText(header().trimIndent().trimLines() + "\n")
    appendText(generator(dsl).trimIndent().consolidateLines().trimLines())
  }
}

internal fun dslProperties(dslProperties: List<DSLPropertyModel>) =
  dslProperties
    .sortedBy{ it.name }
    .map { dslProperty(it).prependIndent("  ") }.joinToString("\n")

internal fun subDSLs(subDSLs: List<SubDSLModel>) =
  subDSLs
    .sortedBy{ it.name }
    .map { subDSL(it).prependIndent("  ") }.joinToString("\n")

internal fun dslSecondaries(dslSecondaries: List<DSLPropertyModel>) =
  dslSecondaries
    .sortedBy{ it.name }
    .map { dslSecondary(it).prependIndent("  ") }.joinToString("\n")

internal fun dslFunctions(dslFunctions: List<DSLFunctionModel>) =
  dslFunctions
    .sortedBy{ it.name }
    .map { dslFunction(it).prependIndent("  ") }.joinToString("\n")

internal fun imports(set: Set<String>) = "import " +
  set
    .sorted()
    .joinToString("\n  import ")

internal fun extDSLs(extDSLs: List<ExtDSLModel>) =
  extDSLs
    .sortedBy{ "${it.receiverType}ZZZZZZ${it.name}" }
    .map { extDSL(it).prependIndent("  ") }.joinToString("\n")

internal fun comment(text: String) = text.lines().joinToString("\n    * ")
internal fun annotations(set: Set<String>) = "@" + set.sorted().joinToString("\n  @")

private fun String.trimLines() = this.trim(' ', '\t', '\r', '\n')
private fun String.consolidateLines() = this.replace(Regex("^(\\s*\\r?\\n)+", MULTILINE), "\n")
