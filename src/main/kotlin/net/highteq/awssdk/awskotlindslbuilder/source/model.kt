/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.source

import net.highteq.awssdk.awskotlindslbuilder.Index
import net.highteq.awssdk.awskotlindslbuilder.methodKey
import net.highteq.awssdk.awskotlindslbuilder.xmldoc.MethodElement
import net.highteq.awssdk.awskotlindslbuilder.xmldoc.TypeDeclarationElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

typealias BuilderMap = Map<Class<*>, BuilderModel>

data class SourceModel(
  val superType: Class<*>,
  val builders: BuilderMap
) {
  val methods: Index<MethodModel> = Index(
    builders.values
      .flatMap { it.methodGroups.values }
      .flatMap { it.methods }
      .map { methodKey(it.method) to it }
      .toMap()
  )
}

data class TypeDeclaration(
  val name: String,
  val qualified: String,
  val type: Class<*>,
  val doc: TypeDeclarationElement?
)

typealias MethodGroupMap = Map<String, MethodGroupModel>

data class BuilderModel(
  val builder: TypeDeclaration,
  val target: TypeDeclaration,
  val methodGroups: MethodGroupMap
)

typealias MethodList = List<MethodModel>

data class MethodGroupModel(
  val name: String,
  val qualified: String,
  val methods: MethodList
)

data class MethodModel(
  val name: String,
  val qualified: String,
  val method: Method,
  val doc: MethodElement?
) {
  val dependencies = dependenciesOfMethod(method)
}

private fun dependenciesOfMethod(method: Method): Set<Class<*>> =
  mutableSetOf<Class<*>>().apply {
    method.genericParameterTypes.forEach { add(it) }
  }


private fun MutableSet<Class<*>>.add(type: Type) {
  when (type) {
    is Class<*> -> if (!type.isPrimitive) add(type)
    is ParameterizedType -> {
      type.rawType?.let { add(it) }
      type.actualTypeArguments.forEach { add(it) }
    }
    is WildcardType -> {
      type.lowerBounds?.forEach { add(it) }
      type.upperBounds?.forEach { add(it) }
    }
  }
}
