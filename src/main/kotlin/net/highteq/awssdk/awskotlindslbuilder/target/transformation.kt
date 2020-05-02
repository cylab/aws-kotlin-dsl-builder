/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.target

import net.highteq.awssdk.awskotlindslbuilder.source.*
import java.lang.reflect.Type
import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.function.Consumer

fun transform(sourceModel: SourceModel, sourcePackage: String, targetPackage: String): DSLModel {

  val dslMarker = DSLMarkerModel(
    targetPackage,
    "${sourcePackage.substringAfterLast('.').capitalize()}DSL"
  )

  val collectionDSLs = tranformBuildableCollections(sourceModel, sourcePackage, targetPackage, dslMarker)
  val mapDSLs = transformBuildableMaps(sourceModel, sourcePackage, targetPackage, dslMarker)
  val typeDSLs = transformBuildableTypes(sourceModel, sourcePackage, targetPackage, dslMarker)

  return DSLModel(dslMarker, collectionDSLs, mapDSLs, typeDSLs)
}

private fun tranformBuildableCollections(sourceModel: SourceModel, sourcePackage: String, targetPackage: String, dslMarker: DSLMarkerModel): List<CollectionDSLModel> {
  return findCollectionDSLTypes(sourceModel)
    .map { (method, target) ->
      CollectionDSLModel(
        packageName = convertPackage(target.type, sourcePackage, targetPackage),
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
        targetDSLType = "${target.name}DSL",
        targetDSLEntrypoint = "build${target.name}"
      )
    }
}

private fun transformBuildableMaps(sourceModel: SourceModel, sourcePackage: String, targetPackage: String, dslMarker: DSLMarkerModel): List<MapDSLModel> {
  return findMapDSLTypes(sourceModel)
    .map { (method, key, target) ->
      MapDSLModel(
        packageName = convertPackage(target.type, sourcePackage, targetPackage),
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
        targetDSLType = "${target.name}DSL",
        targetDSLEntrypoint = "build${target.name}"
      )
    }
}

private fun transformBuildableTypes(sourceModel: SourceModel, sourcePackage: String, targetPackage: String, dslMarker: DSLMarkerModel): List<TypeDSLModel> {
  return sourceModel.builders.values
    .map { transformBuildableType(it, sourceModel, sourcePackage, targetPackage, dslMarker) }
}

private fun transformBuildableType(builder: BuilderModel, sourceModel: SourceModel, sourcePackage: String, targetPackage: String, dslMarker: DSLMarkerModel): TypeDSLModel {
  val methodGroups = builder.methodGroups.values.map { MethodGroupFacade(it) }

  val dslFunctions = transformSimpleMethods(methodGroups, sourceModel)
  val dslProperties = transformPropertyMethods(methodGroups)
  val dslSecondaries = transformPropertyMethodOverloads(methodGroups)
  val subDSLs = transformMethodsWithBuildableType(methodGroups, sourceModel)
  val subCollectionDSLs = tranformMethodsWithBuildableCollection(methodGroups, sourceModel)
  val subMapDSLs = transformMethodsWithBuildableMap(methodGroups, sourceModel)
  val dependencies = listOf(
    dslMarker.qualified,
    builder.builder.qualified,
    builder.target.qualified,
    *methodGroups
      .flatMap { listOf(it.primaryProperty, it.secondaryOverload) }
      .filterNotNull()
      .flatMap { it.dependencies }
      .map { it.name }
      .filterNot { isStandardImport(it) }
      .toTypedArray()
  )
  return TypeDSLModel(
    packageName = convertPackage(builder.target.type, sourcePackage, targetPackage),
    name = "${builder.target.name}DSL",
    imports = dependencies.filterNot { it.contains('$') }.toSet(),
    comment = "Builds instances of type ${builder.target.name}:\n" +
      toMarkdown(builder.target.doc?.comment ?: ""),
    annotations = setOf(dslMarker.name),
    dslEntrypoint = "build${builder.target.name}",
    builderType = builder.builder.name,
    targetType = builder.target.name,
    dslProperties = dslProperties,
    dslSecondaries = dslSecondaries,
    dslFunctions = dslFunctions,
    subDSLs = listOf(subDSLs, subMapDSLs, subCollectionDSLs).flatten()
  )
}

private fun transformSimpleMethods(methodGroups: List<MethodGroupFacade>, sourceModel: SourceModel): List<DSLFunctionModel> {
  return methodGroups
    .mapNotNull { it.simpleFunction }
    .filter { sourceModel.superType.isAssignableFrom(it.method.returnType) }
    .map {
      DSLFunctionModel(
        name = it.name,
        comment = toMarkdown(it.doc?.comment ?: "")
      )
    }
}

private fun transformPropertyMethods(methodGroups: List<MethodGroupFacade>): List<DSLPropertyModel> {
  return methodGroups
    .mapNotNull { it.primaryProperty }
    .map {
      val type = it.method.genericParameterTypes[0]
      DSLPropertyModel(
        name = it.name,
        comment = toMarkdown(it.doc?.comment ?: ""),
        targetType = type.simpleTypeName() + type.nullableMarker()
      )
    }
}

private fun transformPropertyMethodOverloads(methodGroups: List<MethodGroupFacade>): List<DSLPropertyModel> {
  return methodGroups
    .mapNotNull { it.secondaryOverload }
    .map {
      val type = it.method.genericParameterTypes[0]
      DSLPropertyModel(
        name = it.name,
        comment = toMarkdown(it.doc?.comment ?: ""),
        targetType = type.simpleTypeName() + type.nullableMarker()
      )
    }
}

private fun transformMethodsWithBuildableType(methodGroups: List<MethodGroupFacade>, sourceModel: SourceModel): List<SubDSLModel> {
  return methodGroups
    .mapNotNull { it.primaryProperty }
    .map { it to sourceModel.builders[it.method.parameterTypes[0]] }
    .filterNot { it.second == null }
    .map { (method, targetBuilder) -> method to targetBuilder!!.target }
    .map { (method, target) ->
      SubDSLModel(
        name = method.name,
        comment = toMarkdown(method.doc?.comment ?: ""),
        targetType = target.name,
        targetDSLType = "${target.name}DSL",
        targetDSLEntrypoint = "build${target.name}"
      )
    }
}

private fun tranformMethodsWithBuildableCollection(methodGroups: List<MethodGroupFacade>, sourceModel: SourceModel): List<SubDSLModel> {
  return methodGroups
    .mapNotNull { it.findMethodsWithCollectionParam() }
    .map { it to it.method.genericParameterTypes[0] }
    .filter { it.second is ParameterizedType }
    .map { (method, type) -> method to (type as ParameterizedType).actualTypeArguments[0] }
    .filterNot { it.second == null }
    .map { (method, type) -> method to sourceModel.builders[type] }
    .filterNot { it.second == null }
    .map { (method, targetBuilder) -> method to targetBuilder!!.target }
    .map { (method, target) ->
      SubDSLModel(
        name = method.name,
        comment = toMarkdown(method.doc?.comment ?: ""),
        targetType = target.name,
        targetDSLType = "${target.name}CollectionDSL",
        targetDSLEntrypoint = "build${target.name}Collection"
      )
    }
}

private fun transformMethodsWithBuildableMap(methodGroups: List<MethodGroupFacade>, sourceModel: SourceModel): List<SubDSLModel> {
  return methodGroups
    .mapNotNull { it.findMethodsWithMapParam() }
    .map { it to it.method.genericParameterTypes[0] }
    .filter { it.second is ParameterizedType }
    .map { (method, type) -> method to (type as ParameterizedType).actualTypeArguments[1] }
    .filterNot { it.second == null }
    .map { (method, type) -> method to sourceModel.builders[type] }
    .filterNot { it.second == null }
    .map { (method, targetBuilder) -> method to targetBuilder!!.target }
    .map { (method, target) ->
      SubDSLModel(
        name = method.name,
        comment = toMarkdown(method.doc?.comment ?: ""),
        targetType = target.name,
        targetDSLType = "${target.name}MapDSL",
        targetDSLEntrypoint = "build${target.name}Map"
      )
    }
}

private fun findCollectionDSLTypes(sourceModel: SourceModel) =
  findMethodWithParameterizedTypeParameter(sourceModel, Collection::class.java)
    .map { (model, param) -> model to param.actualTypeArguments[0] }
    .filter { (_, type) -> type is Class<*> }
    .mapNotNull { (model, type) -> sourceModel.builders[type]?.let { model to it.target } }
    .toList()

private fun findMapDSLTypes(sourceModel: SourceModel) =
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

private fun isStandardImport(import: String) = listOf("java.util", "java.lang")
  .map { import.startsWith(it) }
  .contains(true)

private fun toMarkdown(text: String) = text
  .replace(Regex("<[^>]+>"), "")
  .replace(Regex("^(\\s*[\r\n])+",RegexOption.MULTILINE), "\n")
  .trim()

private fun convertPackage(type: Class<*>, sourcePackage: String, targetPackage: String) = targetPackage + type.`package`.name.substringAfter(sourcePackage)

private fun Type.nullableMarker(): String = when (this) {
  is Class<*> -> if (this.isPrimitive) "" else "?"
  is ParameterizedType -> this.rawType.nullableMarker()
  is WildcardType -> when {
    this.lowerBounds.isNotEmpty() -> this.lowerBounds[0].nullableMarker()
    this.upperBounds.isNotEmpty() -> this.upperBounds[0].nullableMarker()
    else -> ""
  }
  else -> ""
}

private fun Type.simpleTypeName(): String {
  val result = when (this) {
    is Class<*> -> when {
      this.isPrimitive -> this.simpleName.capitalize()
      else -> targetTypeMapping[this] ?: this.name.substringAfterLast(".").replace('$', '.')
    }
    is ParameterizedType -> {
      this.rawType.typeName.substringAfterLast('.') +
        "<" + this.actualTypeArguments.map { it.simpleTypeName() }.joinToString(", ") + ">"
    }
    // TODO: find out, what really needs to be done for a WildcardType
    is WildcardType -> when {
      this.lowerBounds.isNotEmpty() -> this.lowerBounds[0].simpleTypeName()
      this.upperBounds.isNotEmpty() -> this.upperBounds[0].simpleTypeName()
      else -> "Any"
    }
    // TODO: find out, how to really handle TypeVariables...
    is TypeVariable<*> -> {
      this.genericDeclaration.toString().substringAfter(" ").substringAfterLast(".") + "<*>"
    }
    else -> "Should not happen: " + this.javaClass
  }
  return result.replace(Regex("^Integer$"), "Int")
}

private fun String.startLowerCase() = toCharArray()
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

private class MethodGroupFacade(
  val model: MethodGroupModel
) {

  val simpleFunction: MethodModel?
    get() {
      findMethodsWithNoParam()?.let { return it }
      return null
    }

  val primaryProperty: MethodModel?
    get() {
      findMethodsWithTypedParam()?.let { return it }
      findMethodsWithCollectionParam()?.let { return it }
      findMethodsWithArrayParam()?.let { return it }
      findMethodsWithPrimitiveParam()?.let { return it }
      findMethodsWithLambdaParam()?.let { return it }
      return null
    }

  val secondaryOverload: MethodModel?
    get() {
      findMethodsWithPrimitiveParam()?.let {
        if(hasOverloads) return it
      }
      return null
    }

  val hasOverloads: Boolean
    get() = model.methods.size > 1

  fun findMethodsWithPrimitiveParam() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1 && countsAsPrimitive(it.method.parameterTypes[0])
    }

  fun findMethodsWithTypedParam() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1 && it.method.parameterTypes[0].run {
        !countsAsPrimitive(this) && !isArray
          && !Collection::class.java.isAssignableFrom(this)
          && !Consumer::class.java.isAssignableFrom(this)
      }
    }

  fun findMethodsWithCollectionParam() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1
        && Collection::class.java.isAssignableFrom(it.method.parameterTypes[0])
    }

  fun findMethodsWithMapParam() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1
        && java.util.Map::class.java.isAssignableFrom(it.method.parameterTypes[0])
    }

  fun findMethodsWithArrayParam() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1 && it.method.parameterTypes[0].isArray
    }

  fun findMethodsWithLambdaParam() =
    model.methods.firstOrNull {
      it.method.parameterCount == 1
        && Consumer::class.java.isAssignableFrom(it.method.parameterTypes[0])
    }

  fun findMethodsWithNoParam() = model.methods.firstOrNull { it.method.parameterCount == 0 }
  fun countsAsPrimitive( type: Class<*>) = type.isPrimitive || type.`package`?.name == "java.lang"
}

