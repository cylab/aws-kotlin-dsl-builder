/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.target

import net.highteq.awssdk.awskotlindslbuilder.pairOrNull
import net.highteq.awssdk.awskotlindslbuilder.rawClass
import net.highteq.awssdk.awskotlindslbuilder.source.BuilderModel
import net.highteq.awssdk.awskotlindslbuilder.source.MethodModel
import net.highteq.awssdk.awskotlindslbuilder.source.MethodModel.ParamContainer.COLLECTION
import net.highteq.awssdk.awskotlindslbuilder.source.MethodModel.ParamContainer.MAP
import net.highteq.awssdk.awskotlindslbuilder.source.TypeDeclaration
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.function.Consumer

fun transform(builders: Map<Class<*>, BuilderModel>, sourcePackage: String, targetPackage: String): DSLModel {

  val dslMarker = DSLMarkerModel(
    targetPackage,
    "${sourcePackage.substringAfterLast('.').capitalize()}DSL"
  )

  val collectionDSLs = tranformBuildableCollections(builders, sourcePackage, targetPackage, dslMarker)
  val mapDSLs = transformBuildableMaps(builders, sourcePackage, targetPackage, dslMarker)
  val typeDSLs = transformBuildableTypes(builders, sourcePackage, targetPackage, dslMarker)

  return DSLModel(dslMarker, collectionDSLs, mapDSLs, typeDSLs)
}

private fun tranformBuildableCollections(builders: Map<Class<*>, BuilderModel>, sourcePackage: String, targetPackage: String, dslMarker: DSLMarkerModel): List<CollectionDSLModel> {
  return findCollectionDSLTypes(builders)
    .map { builder ->
      CollectionDSLModel(
        packageName = convertPackage(builder.target.type, sourcePackage, targetPackage),
        name = "${builder.target.name}CollectionDSL",
        imports = setOf(
          dslMarker.qualified,
          builder.target.qualified
        ),
        comment = "Builds a collection of type ${builder.target.name}:\n" +
          toMarkdown(builder.target.doc?.comment ?: ""),
        annotations = setOf(dslMarker.name),
        dslEntrypoint = "build${builder.target.name}Collection",
        targetType = builder.target.name,
        targetDSLType = "${builder.target.name}DSL",
        targetDSLEntrypoint = "build${builder.target.name}"
      )
    }
}

private fun transformBuildableMaps(builders: Map<Class<*>, BuilderModel>, sourcePackage: String, targetPackage: String, dslMarker: DSLMarkerModel): List<MapDSLModel> {
  return findMapDSLTypes(builders)
    .map { (key, builder) ->
      MapDSLModel(
        packageName = convertPackage(builder.target.type, sourcePackage, targetPackage),
        name = "${builder.target.name}MapDSL",
        imports = setOf(
          dslMarker.qualified,
          builder.target.qualified
        ),
        comment = "Builds a maps of type ${builder.target.name}:\n" +
          toMarkdown(builder.target.doc?.comment ?: ""),
        annotations = setOf(dslMarker.name),
        dslEntrypoint = "build${builder.target.name}Map",
        keyType = key.simpleTypeName(),
        targetType = builder.target.name,
        targetDSLType = "${builder.target.name}DSL",
        targetDSLEntrypoint = "build${builder.target.name}"
      )
    }
}

private fun transformBuildableTypes(builders: Map<Class<*>, BuilderModel>, sourcePackage: String, targetPackage: String, dslMarker: DSLMarkerModel): List<TypeDSLModel> {
  return builders.values
    .map { transformBuildableType(it, builders, sourcePackage, targetPackage, dslMarker) }
}

private fun transformBuildableType(builder: BuilderModel, builders: Map<Class<*>, BuilderModel>, sourcePackage: String, targetPackage: String, dslMarker: DSLMarkerModel): TypeDSLModel {
  val methodGroups = builder.attributes
    .groupBy { it.method.name }
    .map { (name, methods) ->
      MethodGroup(
        owner = builder.builder,
        name = name,
        qualified = methods.first().qualified,
        methods = methods
      )
    }

  val dslFunctions = transformSimpleMethods(methodGroups)
  val dslProperties = transformPropertyMethods(methodGroups)
  val dslSecondaries = transformPropertyMethodOverloads(methodGroups)
  val subDSLs = transformMethodsWithBuildableType(builder.attributes, builders)
  val extDSLs =  transformExtMethodsWithBuildableType(builder.targetUsages, builders)

  val dependencies = listOf(
    dslMarker.qualified,
    builder.builder.qualified,
    builder.target.qualified,
    *methodGroups
      .flatMap { listOf(it.primaryProperty, it.secondaryOverload) }
      .filterNotNull()
      .flatMap { it.dependencies }
      .map { it.name }
      .toTypedArray(),
    *subDSLs
      .flatMap { it.imports }
      .toTypedArray(),
    *extDSLs
      .flatMap { it.imports }
      .toTypedArray()
  )
  return TypeDSLModel(
    packageName = convertPackage(builder.target.type, sourcePackage, targetPackage),
    name = "${builder.target.name}DSL",
    imports = dependencies
      .filterNot { it.contains('$') }
      .filterNot { isStandardImport(it) }
      .toSet(),
    comment = "Builds instances of type ${builder.target.name}:\n" +
      toMarkdown(builder.target.doc?.comment ?: ""),
    annotations = setOf(dslMarker.name),
    dslEntrypoint = "build${builder.target.name}",
    builderType = builder.builder.name,
    targetType = builder.target.name,
    dslProperties = dslProperties,
    dslSecondaries = dslSecondaries,
    dslFunctions = dslFunctions,
    subDSLs = subDSLs,
    extDSLs = extDSLs
  )
}

private fun transformSimpleMethods(methodGroups: List<MethodGroup>): List<DSLFunctionModel> {
  return methodGroups
    .mapNotNull { it.simpleFunction }
    .map {
      DSLFunctionModel(
        name = it.name,
        comment = toMarkdown(it.doc?.comment ?: "")
      )
    }
}

private fun transformPropertyMethods(methodGroups: List<MethodGroup>): List<DSLPropertyModel> {
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

private fun transformPropertyMethodOverloads(methodGroups: List<MethodGroup>): List<DSLPropertyModel> {
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

private fun transformMethodsWithBuildableType(attributes: Collection<MethodModel>, builders: Map<Class<*>, BuilderModel>): List<SubDSLModel> {
  return attributes
    .filterNot { it.buildableParamValueClass == null }
    .mapNotNull { pairOrNull(it, builders[it.buildableParamValueClass!!]) }
    .map { (method, targetBuilder) -> method to targetBuilder!!.target }
    .map { (method, target) ->
      val name = when(method.buildableParamContainer) {
        COLLECTION -> "${target.name}Collection"
        MAP -> "${target.name}Map"
        else -> target.name
      }
      SubDSLModel(
        name = method.name,
        comment = toMarkdown(method.doc?.comment ?: ""),
        imports = setOf(target.qualified),
        targetType = target.name,
        targetDSLType = "${name}DSL",
        targetDSLEntrypoint = "build${name}"
      )
    }
}

private fun transformExtMethodsWithBuildableType(methods: Collection<MethodModel>, builders: Map<Class<*>, BuilderModel>): List<ExtDSLModel> {
  return methods
    .filterNot { it.isBuilderMethod || it.buildableParamValueClass == null }
    .mapNotNull { pairOrNull(it, builders[it.buildableParamValueClass!!]) }
    .map { (method, targetBuilder) -> method to targetBuilder!!.target }
    .map { (method, target) ->
      val name = when(method.buildableParamContainer) {
        COLLECTION -> "${target.name}Collection"
        MAP -> "${target.name}Map"
        else -> target.name
      }
      ExtDSLModel(
        name = method.name,
        comment = toMarkdown(method.doc?.comment ?: ""),
        imports = setOf(method.owner.qualified, target.qualified),
        receiverType = method.owner.name,
        targetType = target.name,
        targetDSLType = "${name}DSL",
        targetDSLEntrypoint = "build${name}"
      )
    }
}


private fun findCollectionDSLTypes(builders: Map<Class<*>, BuilderModel>) =
  builders.values
    .flatMap { it.targetUsages }
    .filter { it.buildableParamContainer == COLLECTION }
    .mapNotNull { builders[it.buildableParamValueClass!!] }


private fun findMapDSLTypes(builders: Map<Class<*>, BuilderModel>) =
  builders.values
    .flatMap { it.targetUsages }
    .filter { it.buildableParamContainer == MAP }
    .map { it.method.genericParameterTypes[0] as ParameterizedType to it }
    .mapNotNull { (param, method) ->
      pairOrNull(param.actualTypeArguments[0].rawClass, builders[method.buildableParamValueClass!!])
    }


private fun isStandardImport(import: String) = listOf("java.util", "java.lang")
  .map { import.startsWith(it) }
  .contains(true)

private fun toMarkdown(text: String) = text
  .replace(Regex("<[^>]+>"), "")
  .replace(Regex("^(\\s*[\r\n])+",RegexOption.MULTILINE), "\n")
  .trim()

private fun convertPackage(type: Type, sourcePackage: String, targetPackage: String) =
  targetPackage + (type as Class<*>).`package`.name.substringAfter(sourcePackage)

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

private class MethodGroup(
  val owner: TypeDeclaration,
  val name: String,
  val qualified: String,
  val methods: List<MethodModel>
) {

  val simpleFunction: MethodModel?
    get() {
      findMethodWithNoParam()?.let { return it }
      return null
    }

  val primaryProperty: MethodModel?
    get() {
      findMethodWithTypedParam()?.let { return it }
      findMethodWithCollectionParam()?.let { return it }
      findMethodWithArrayParam()?.let { return it }
      findMethodWithPrimitiveParam()?.let { return it }
      findMethodWithLambdaParam()?.let { return it }
      return null
    }

  val secondaryOverload: MethodModel?
    get() {
      findMethodWithPrimitiveParam()?.let {
        if(hasOverloads) return it
      }
      return null
    }

  val hasOverloads: Boolean
    get() = methods.size > 1

  fun findMethodWithParamType(type: Class<*>) =
    methods.firstOrNull {
      it.method.parameterCount == 1 && it.method.parameterTypes[0] == (type)
    }

  fun findMethodWithPrimitiveParam() =
    methods.firstOrNull {
      it.method.parameterCount == 1 && countsAsPrimitive(it.method.parameterTypes[0])
    }

  fun findMethodWithTypedParam() =
    methods.firstOrNull {
      it.method.parameterCount == 1 && it.method.parameterTypes[0].run {
        !countsAsPrimitive(this) && !isArray
          && !Collection::class.java.isAssignableFrom(this)
          && !Consumer::class.java.isAssignableFrom(this)
      }
    }

  fun findMethodWithCollectionParam() =
    methods.firstOrNull {
      it.method.parameterCount == 1
        && Collection::class.java.isAssignableFrom(it.method.parameterTypes[0])
    }

  fun findMethodWithMapParam() =
    methods.firstOrNull {
      it.method.parameterCount == 1
        && java.util.Map::class.java.isAssignableFrom(it.method.parameterTypes[0])
    }

  fun findMethodWithArrayParam() =
    methods.firstOrNull {
      it.method.parameterCount == 1 && it.method.parameterTypes[0].isArray
    }

  fun findMethodWithLambdaParam() =
    methods.firstOrNull {
      it.method.parameterCount == 1
        && Consumer::class.java.isAssignableFrom(it.method.parameterTypes[0])
    }

  fun findMethodWithNoParam() = methods.firstOrNull { it.method.parameterCount == 0 }
  fun countsAsPrimitive( type: Class<*>) = type.isPrimitive || type.`package`?.name == "java.lang"
}

