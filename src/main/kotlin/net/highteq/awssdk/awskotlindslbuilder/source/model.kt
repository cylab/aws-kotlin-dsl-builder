/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.source

import net.highteq.awssdk.awskotlindslbuilder.xmldoc.MethodElement
import net.highteq.awssdk.awskotlindslbuilder.xmldoc.TypeDeclarationElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

data class TypeDeclaration(
  val name: String,
  val qualified: String,
  val type: Type,
  val doc: TypeDeclarationElement?
)

data class BuilderModel(
  val builder: TypeDeclaration,
  val target: TypeDeclaration,
  val attributes: Collection<MethodModel>,
  val targetUsages: Collection<MethodModel>
)

data class MethodModel(
  val owner: TypeDeclaration,
  val name: String,
  val returnType: Class<*>,
  val qualified: String,
  val method: Method,
  val doc: MethodElement?,
  val isBuilderMethod: Boolean = false,
  val buildableParamContainer: ParamContainer = ParamContainer.NONE,
  val buildableParamValueClass: Class<*>? = null
) {
  enum class ParamContainer { NONE, SCALAR, COLLECTION, MAP }
  val dependencies = mutableSetOf<Class<*>>().apply {
    method.genericParameterTypes.forEach { add(it) }
  }
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
