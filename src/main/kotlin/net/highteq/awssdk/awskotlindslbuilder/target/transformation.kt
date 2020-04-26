/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.target

import net.highteq.awssdk.awskotlindslbuilder.source.MethodGroupModel
import net.highteq.awssdk.awskotlindslbuilder.source.MethodModel
import net.highteq.awssdk.awskotlindslbuilder.source.SourceModel
import net.highteq.awssdk.awskotlindslbuilder.source.TypeDeclaration
import java.lang.reflect.Type
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import java.util.function.Consumer

fun transform(sourceModel: SourceModel, sourcePackage: String, targetPackage: String): DSLModel {
  fun TypeDeclaration.convertPackage() = targetPackage + this.type.`package`.name.substringAfter(sourcePackage)
  fun String.startLowerCase() =
    toCharArray()
      .foldIndexed(0) { index, last, char ->
        if (last == index && char.isUpperCase()) index + 1 else last
      }
      .let {
        when (it) {
          0 -> this
          1 -> substring(0, 1).toLowerCase() + substring(1)
          else -> substring(0, it - 1).toLowerCase() + substring(it - 1)
        }
      }

  fun Type.simpleTypeName(): String = when (this) {
    is Class<*> -> if (this.isPrimitive) this.simpleName.capitalize() else this.simpleName
    is ParameterizedType -> this.rawType.typeName.substringAfterLast('.') +
      "<" + this.actualTypeArguments.map { it.simpleTypeName() }.joinToString(", ") + ">"
    // TODO: find out, what really needs to be done for a WildcardType
    is WildcardType -> when {
      this.lowerBounds.isNotEmpty() -> this.lowerBounds[0].simpleTypeName()
      this.upperBounds.isNotEmpty() -> this.upperBounds[0].simpleTypeName()
      else -> "Any"
    }
    else -> "Should not happen: " + this.javaClass
  }.replace(Regex("^Integer$"), "Int")

  fun Type.nullableMarker(): String = when (this) {
    is Class<*> -> if (this.isPrimitive) "" else "?"
    is ParameterizedType -> this.rawType.nullableMarker()
    is WildcardType -> when {
      this.lowerBounds.isNotEmpty() -> this.lowerBounds[0].nullableMarker()
      this.upperBounds.isNotEmpty() -> this.upperBounds[0].nullableMarker()
      else -> ""
    }
    else -> ""
  }

  val dslMarker = DSLMarkerModel(
    targetPackage,
    "${sourcePackage.substringAfterLast('.').capitalize()}DSL"
  )

  val collectionDSLs = findCollectionDSLTypes(sourceModel)
    .map { (method, target) ->
      CollectionDSLModel(
        packageName = target.convertPackage(),
        imports = setOf(
          dslMarker.qualified,
          target.qualified,
          *method.dependencies.map { it.name }.filterNot { isStandardImport(it) }.toTypedArray()
        ),
        comment = "Builds instances of type ${target.name}:\n" +
          toMarkdown(target.doc?.comment ?: ""),
        annotations = setOf(dslMarker.name),
        name = "${target.name}CollectionDSL",
        dslEntrypoint = "build${target.name}Collection",
        targetType = target.name,
        targetDSLType = "${target.name}DSL"
      )
    }

  val typeDSLs = sourceModel.builders.values
    .map { builder ->
      val groups = builder.methodGroups.values.map { MethodGroupFacade(it) }

      val properties = groups.map { it.primaryProperty }
      val dslProperties = properties.map {
        val type = it.method.genericParameterTypes[0]
        DSLPropertyModel(
          comment = toMarkdown(it.doc?.comment ?: ""),
          name = it.name,
          targetType = type.simpleTypeName() + type.nullableMarker()
        )
      }

      val functions = groups.mapNotNull { it.secondaryFunction }
      val dslFunctions = functions.map {
        val type = it.method.genericParameterTypes[0]
        DSLFunctionModel(
          comment = toMarkdown(it.doc?.comment ?: ""),
          name = it.name,
          targetType = type.simpleTypeName() + type.nullableMarker()
        )
      }

      val subBuilders = groups
        .map { it.primaryProperty }
        .map { it to sourceModel.builders[it.method.parameterTypes[0]] }
        .filterNot { it.second == null }
        .map { (method, targetBuilder) -> method to targetBuilder!!.target }

      val subDSLs = subBuilders.map { (method, target) ->
        SubDSLModel(
          comment = toMarkdown(method.doc?.comment ?: ""),
          name = method.name,
          targetType = target.name,
          targetDSLType = "${target.name}DSL",
          targetDSLEntrypoint = "build${target.name}"
        )
      }

      val subCollectionBuilders = groups
        .mapNotNull { it.findCollection() }
        .map { it to it.method.genericParameterTypes[0] }
        .filter { it.second is ParameterizedType }
        .map { (method, type) -> method to (type as ParameterizedType).actualTypeArguments[0] }
        .filterNot { it.second == null }
        .map { (method, type) -> method to sourceModel.builders[type] }
        .filterNot { it.second == null }
        .map { (method, targetBuilder) -> method to targetBuilder!!.target }

      val subCollectionDSLs = subCollectionBuilders.map { (method, target) ->
        SubDSLModel(
          comment = toMarkdown(method.doc?.comment ?: ""),
          name = method.name,
          targetType = target.name,
          targetDSLType = "${target.name}CollectionDSL",
          targetDSLEntrypoint = "build${target.name}Collection"
        )
      }

      val dependencies = listOf(properties, functions, subBuilders.map { it.first }, subCollectionBuilders.map { it.first })
        .flatten()
        .flatMap { it.dependencies }
        .map { it.name }

      TypeDSLModel(
        builder.target.convertPackage(),
        imports = setOf(
          dslMarker.qualified,
          builder.target.qualified,
          *dependencies.filterNot { isStandardImport(it) }.toTypedArray()
        ),
        comment = "Builds instances of type ${builder.target.name}:\n" +
          toMarkdown(builder.target.doc?.comment ?: ""),
        annotations = setOf(dslMarker.name),
        name = "${builder.target.name}DSL",
        dslEntrypoint = "build${builder.target.name}",
        targetType = builder.target.name,
        dslProperties = dslProperties,
        dslFunctions = dslFunctions,
        subDSLs = listOf(subDSLs, subCollectionDSLs).flatten()
      )
    }

  return DSLModel(dslMarker, collectionDSLs, typeDSLs)
}


fun findCollectionDSLTypes(sourceModel: SourceModel) =
  sourceModel.methods.index.values.asSequence()
    .filter { Collection::class.java.isAssignableFrom(it.method.parameterTypes[0]) }
    .map { model -> model to model.method.genericParameterTypes[0] }
    .filter { (_, param) -> param is ParameterizedType }
    .map { (model, param) -> model to (param as ParameterizedType).actualTypeArguments[0] }
    .filter { (_, type) -> type is Class<*> }
    .mapNotNull { (model, type) -> sourceModel.builders[type]?.let { model to it.target } }
    .toList()


class MethodGroupFacade(
  val model: MethodGroupModel
) {

  val primaryProperty: MethodModel
    get() {
      val primitive = findPrimitive()
      if (primitive != null && !hasOverloads) {
        return primitive
      }
      val typed = findTyped()
      if (typed != null) {
        return typed
      }
      val collection = findCollection()
      if (collection != null) {
        return collection
      }
      val array = findArray()
      if (array != null) {
        return array
      }
      val lambda = findLambda()
      if (lambda != null) {
        return lambda
      }
      return model.methods[0]
    }

  val secondaryFunction: MethodModel?
    get() {
      val primitive = findPrimitive()
      if (primitive != null && hasOverloads) {
        return primitive
      }
      return null
    }

  val hasOverloads: Boolean
    get() = model.methods.size > 1

  fun findPrimitive() = model.methods.firstOrNull {
    val type = it.method.parameterTypes[0]
    type.isPrimitive || type.getPackage()?.name == "java.lang"
  }

  fun findTyped() = model.methods.firstOrNull {
    val type = it.method.parameterTypes[0]
    !(type.isPrimitive || type.getPackage()?.name == "java.lang" ||
      type.isArray ||
      Collection::class.java.isAssignableFrom(type) ||
      Consumer::class.java.isAssignableFrom(type))
  }

  fun findCollection() = model.methods.firstOrNull {
    Collection::class.java.isAssignableFrom(it.method.parameterTypes[0])
  }

  fun findArray() = model.methods.firstOrNull {
    it.method.parameterTypes[0].isArray
  }

  fun findLambda() = model.methods.firstOrNull {
    Consumer::class.java.isAssignableFrom(it.method.parameterTypes[0])
  }
}

fun isStandardImport(import: String) = listOf("java.util", "java.lang")
  .map { import.startsWith(it) }
  .contains(true)

fun toMarkdown(text: String) = text
  .replace(Regex("<[^>]+>"), "")
  .replace(Regex("^(\\s*[\r\n])+",RegexOption.MULTILINE), "\n")
  .trim()
