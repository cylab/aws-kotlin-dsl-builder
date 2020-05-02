/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder

import java.lang.reflect.Method

class Index<T>(
  val index: Map<String, T>
) {
  operator fun get(key: String) = index[key]
  operator fun get(type: Class<*>) = this[type.name]
  operator fun get(method: Method) = this[methodKey(method)]
}

fun <F, S> pairOrNull(first: F?, second: S?) =
  if (first == null || second == null) null else first to second

fun methodKey(method: Method) =
  "${method.declaringClass.name}.${method.name}" +
    "(${method.genericParameterTypes.map { it.typeName }.joinToString(", ")})"
