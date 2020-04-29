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
import java.lang.reflect.TypeVariable
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
        name = "${target.name}CollectionDSL",
        imports = setOf(
          dslMarker.qualified,
          target.qualified,
          *method.dependencies.map { it.name }.filterNot { isStandardImport(it) }.toTypedArray()
        ),
        comment = "Builds instances of type ${target.name}:\n" +
          toMarkdown(target.doc?.comment ?: ""),
        annotations = setOf(dslMarker.name),
        dslEntrypoint = "build${target.name}Collection",
        targetType = target.name,
        targetDSLType = "${target.name}DSL"
      )
    }

  val mapDSLs = findMapDSLTypes(sourceModel)
    .map { (method, key, target) ->
      MapDSLModel(
        packageName = target.convertPackage(),
        name = "${target.name}MapDSL",
        imports = setOf(
          dslMarker.qualified,
          target.qualified,
          *method.dependencies.map { it.name }.filterNot { isStandardImport(it) }.toTypedArray()
        ),
        comment = "Builds instances of type ${target.name}:\n" +
          toMarkdown(target.doc?.comment ?: ""),
        annotations = setOf(dslMarker.name),
        dslEntrypoint = "build${target.name}Map",
        keyType = key.simpleTypeName(),
        targetType = target.name,
        targetDSLType = "${target.name}DSL"
      )
    }

  val typeDSLs = sourceModel.builders.values
    .map { builder ->
      val groups = builder.methodGroups.values.map { MethodGroupFacade(it) }

      val functions = groups.mapNotNull { it.simpleFunction }
        .filter { sourceModel.superType.isAssignableFrom(it.method.returnType) }
      val dslFunctions = functions.map {
        DSLFunctionModel(
          name = it.name,
          comment = toMarkdown(it.doc?.comment ?: "")
        )
      }

      val properties = groups.mapNotNull { it.primaryProperty }
      val dslProperties = properties.map {
        val type = it.method.genericParameterTypes[0]
        DSLPropertyModel(
          name = it.name,
          comment = toMarkdown(it.doc?.comment ?: ""),
          targetType = type.simpleTypeName() + type.nullableMarker()
        )
      }

      val secondaries = groups.mapNotNull { it.secondaryOverload }
      val dslSecondaries = secondaries.map {
        val type = it.method.genericParameterTypes[0]
        DSLPropertyModel(
          name = it.name,
          comment = toMarkdown(it.doc?.comment ?: ""),
          targetType = type.simpleTypeName() + type.nullableMarker()
        )
      }

      val subBuilders = groups
        .mapNotNull { it.primaryProperty }
        .map { it to sourceModel.builders[it.method.parameterTypes[0]] }
        .filterNot { it.second == null }
        .map { (method, targetBuilder) -> method to targetBuilder!!.target }

      val subDSLs = subBuilders.map { (method, target) ->
        SubDSLModel(
          name = method.name,
          comment = toMarkdown(method.doc?.comment ?: ""),
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
          name = method.name,
          comment = toMarkdown(method.doc?.comment ?: ""),
          targetType = target.name,
          targetDSLType = "${target.name}CollectionDSL",
          targetDSLEntrypoint = "build${target.name}Collection"
        )
      }

      val subMapBuilders = groups
        .mapNotNull { it.findMaps() }
        .map { it to it.method.genericParameterTypes[0] }
        .filter { it.second is ParameterizedType }
        .map { (method, type) -> method to (type as ParameterizedType).actualTypeArguments[1] }
        .filterNot { it.second == null }
        .map { (method, type) -> method to sourceModel.builders[type] }
        .filterNot { it.second == null }
        .map { (method, targetBuilder) -> method to targetBuilder!!.target }

      val subMapDSLs = subMapBuilders.map { (method, target) ->
        SubDSLModel(
          name = method.name,
          comment = toMarkdown(method.doc?.comment ?: ""),
          targetType = target.name,
          targetDSLType = "${target.name}MapDSL",
          targetDSLEntrypoint = "build${target.name}Map"
        )
      }

      val dependencies = listOf(
        properties,
        secondaries,
        subBuilders.map { it.first },
        subMapBuilders.map { it.first },
        subCollectionBuilders.map { it.first })
        .flatten()
        .flatMap { it.dependencies }
        .map { it.name }

      TypeDSLModel(
        builder.target.convertPackage(),
        name = "${builder.target.name}DSL",
        imports = setOf(
          dslMarker.qualified,
          builder.target.qualified,
          *dependencies.filterNot { isStandardImport(it) }.toTypedArray()
        ),
        comment = "Builds instances of type ${builder.target.name}:\n" +
          toMarkdown(builder.target.doc?.comment ?: ""),
        annotations = setOf(dslMarker.name),
        dslEntrypoint = "build${builder.target.name}",
        targetType = builder.target.name,
        dslProperties = dslProperties,
        dslSecondaries = dslSecondaries,
        dslFunctions = dslFunctions,
        subDSLs = listOf(subDSLs, subMapDSLs, subCollectionDSLs).flatten()
      )
    }

  return DSLModel(dslMarker, collectionDSLs, mapDSLs, typeDSLs)
}


fun findCollectionDSLTypes(sourceModel: SourceModel) =
  findMethodWithParameterizedTypeParameter(sourceModel, Collection::class.java)
    .map { (model, param) -> model to param.actualTypeArguments[0] }
    .filter { (_, type) -> type is Class<*> }
    .mapNotNull { (model, type) -> sourceModel.builders[type]?.let { model to it.target } }
    .toList()


fun findMapDSLTypes(sourceModel: SourceModel) =
  findMethodWithParameterizedTypeParameter(sourceModel, Map::class.java)
    .map { (model, param) -> Triple(model, param.actualTypeArguments[0], param.actualTypeArguments[1]) }
    .filter { (_, keyType, targetType) -> keyType is Class<*> && targetType is Class<*> }
    .mapNotNull { (model, keyType, targetType) -> sourceModel.builders[targetType]?.let { Triple(model, keyType, it.target) } }
    .toList()


private fun findMethodWithParameterizedTypeParameter(sourceModel: SourceModel, paramType: Class<*>) =
  sourceModel.methods.index.values.asSequence()
    .filter { it. method.parameterCount == 1 && paramType.isAssignableFrom(it.method.parameterTypes[0]) }
    .map { model -> model to model.method.genericParameterTypes[0] }
    .filter { (_, param) -> param is ParameterizedType }
    .map { (model, param) -> model to (param as ParameterizedType) }


class MethodGroupFacade(
  val model: MethodGroupModel
) {

  val simpleFunction: MethodModel?
    get() {
      findNoParam()?.let { return it }
      return null
    }

  val primaryProperty: MethodModel?
    get() {
      findTyped()?.let { return it }
      findCollection()?.let { return it }
      findArray()?.let { return it }
      findPrimitive()?.let { return it }
      findLambda()?.let { return it }
      return null
    }

  val secondaryOverload: MethodModel?
    get() {
      findPrimitive()?.let {
        if(hasOverloads) return it
      }
      return null
    }

  val hasOverloads: Boolean
    get() = model.methods.size > 1

  fun findPrimitive() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1 && countsAsPrimitive(it.method.parameterTypes[0])
    }

  fun findTyped() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1 && !it.method.parameterTypes[0].run {
        countsAsPrimitive(this) || isArray
          || Collection::class.java.isAssignableFrom(this)
          || Consumer::class.java.isAssignableFrom(this)
      }
    }

  fun findCollection() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1
        && Collection::class.java.isAssignableFrom(it.method.parameterTypes[0])
    }

  fun findMaps() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1
        && java.util.Map::class.java.isAssignableFrom(it.method.parameterTypes[0])
    }

  fun findArray() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1 && it.method.parameterTypes[0].isArray
    }

  fun findLambda() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1
        && Consumer::class.java.isAssignableFrom(it.method.parameterTypes[0])
    }

  fun findNoParam() = model.methods.firstOrNull { it.method.parameterCount == 0 }
  fun countsAsPrimitive( type: Class<*>) = type.isPrimitive || type.`package`?.name == "java.lang"
}

fun isStandardImport(import: String) = listOf("java.util", "java.lang")
  .map { import.startsWith(it) }
  .contains(true)

fun toMarkdown(text: String) = text
  .replace(Regex("<[^>]+>"), "")
  .replace(Regex("^(\\s*[\r\n])+",RegexOption.MULTILINE), "\n")
  .trim()

fun Type.simpleTypeName(): String = when (this) {
  is Class<*> -> if (this.isPrimitive) this.simpleName.capitalize() else targetTypeMapping[this]?:this.simpleName
  is ParameterizedType -> this.rawType.typeName.substringAfterLast('.') +
    "<" + this.actualTypeArguments.map { it.simpleTypeName() }.joinToString(", ") + ">"
  // TODO: find out, what really needs to be done for a WildcardType
  is WildcardType -> when {
    this.lowerBounds.isNotEmpty() -> this.lowerBounds[0].simpleTypeName()
    this.upperBounds.isNotEmpty() -> this.upperBounds[0].simpleTypeName()
    else -> "Any"
  }
  // TODO: find out, how to really handle TypeVariables...
  is TypeVariable<*> -> this.genericDeclaration.toString().substringAfter(" ").substringAfterLast(".")+"<*>"
  else -> "Should not happen: " + this.javaClass
}.replace(Regex("^Integer$"), "Int")

