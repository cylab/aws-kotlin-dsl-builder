/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.source

import net.highteq.awssdk.awskotlindslbuilder.rawClass
import net.highteq.awssdk.awskotlindslbuilder.source.MethodModel.ParamContainer
import net.highteq.awssdk.awskotlindslbuilder.source.MethodModel.ParamContainer.*
import net.highteq.awssdk.awskotlindslbuilder.xmldoc.Docs
import org.apache.commons.lang3.reflect.TypeUtils.getTypeArguments
import org.reflections.ReflectionUtils.*
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type


fun scanSource(sourcePackage: String, docs: Docs?) : Map<Class<*>, BuilderModel> {
  // TODO: shift method groups to transformation
  val rawMethods = findPublicNonInternalMethods(sourcePackage, docs)
  val builderMap = findBuildersWithTargets(rawMethods).toMap()
  val targets = builderMap.values

  // add some more metadata to the methods
  val methods = rawMethods.map {
    val (paramContainer, valueClass) = resolveBuildableParam(it.method, targets)
    it.copy(
      isBuilderMethod = it.returnType in builderMap,
      buildableParamContainer = paramContainer,
      buildableParamValueClass = valueClass
    )
  }

  return builderMap.entries
    .map { (builderClass, targetClass) ->
      val builder = TypeDeclaration(
        name = builderClass.name.substringAfterLast('.').replace('$', '.'),
        qualified = builderClass.name,
        type = builderClass,
        doc = docs?.types?.get(builderClass)
      )
      val target = TypeDeclaration(
        name = targetClass.simpleName,
        qualified = targetClass.name,
        type = targetClass,
        doc = docs?.types?.get(targetClass)
      )
      target.type.rawClass to BuilderModel(
        builder = builder,
        target = target,
        attributes = findBuilderAttributes(methods, builder),
        targetUsages = findTargetUsages(methods, target)
      )
    }
    .toMap()
}

private fun resolveBuildableParam(method: Method, targets: Collection<Class<*>>): Pair<ParamContainer, Class<*>?> {
  if (method.parameterCount != 1)
    return NONE to null

  val paramType = method.genericParameterTypes[0]
  val isCollection = Collection::class.java.isAssignableFrom(paramType.rawClass)
  val isMap = Map::class.java.isAssignableFrom(paramType.rawClass)

  val possibleValueClass = when {
    paramType is ParameterizedType && isCollection -> paramType.actualTypeArguments[0]
    paramType is ParameterizedType && isMap -> paramType.actualTypeArguments[1]
    else -> paramType.rawClass
  }
  val valueClass = targets.firstOrNull { it == possibleValueClass }
  val containerType = when {
    valueClass == null -> NONE
    isCollection -> COLLECTION
    isMap -> MAP
    else -> SCALAR
  }
  return containerType to valueClass
}

private fun findBuildersWithTargets(methods: List<MethodModel>): List<Pair<Class<*>, Class<*>>> {
  return methods
    .filter { it.name == "build" && it.method.parameterCount == 0 }
    .map { it.owner.type.rawClass to it.returnType }
    .filter { (_, targetClass) ->
      getAllMethods(targetClass,
        withModifier(Modifier.STATIC),
        withName("builder"),
        withParametersCount(0)
      ).isNotEmpty()
    }
}

private fun findBuilderAttributes(methods: Collection<MethodModel>, builderDeclaration: TypeDeclaration): List<MethodModel> {
  return methods
    // TODO: resolve method return type against builder type (resolved, if it is a parameterized type)
    // TODO: owner-type hierarchy resolven
    .filter { it.owner.type == builderDeclaration.type && it.returnType.isAssignableFrom(builderDeclaration.type.rawClass)}
    .filterNot { it.name in listOf("applyMutation", "copy", "build", "sdkFields") }
}

private fun findTargetUsages(methods: Collection<MethodModel>, target: TypeDeclaration): List<MethodModel> {
  return methods
    .filter { it.buildableParamValueClass == target.type }
}

private fun findPublicNonInternalMethods(sourcePackage: String, docs: Docs?): List<MethodModel> {
  return findPublicNonInternalTypes(sourcePackage)
    .flatMap { type ->
      val typeDeclaration = TypeDeclaration(
        name = type.rawClass.name.substringAfterLast('.').replace('$', '.'),
        qualified = type.rawClass.name,
        type = type,
        doc = docs?.types?.get(type.rawClass)
      )
      findMethodsOfType(typeDeclaration, docs)
    }
}

private fun findMethodsOfType(declaration: TypeDeclaration, docs: Docs?) =
  getAllMethods(declaration.type.rawClass, withModifier(Modifier.PUBLIC))
    .filterNot { it.name.contains('$') }
    .groupBy { methodCallSignature(it) }
    .map { methodWithMostConcreteReturnType(declaration.type.rawClass, it.value) }
    .map { (method, returnType) ->
      MethodModel(
        owner = declaration,
        name = method.name,
        returnType = returnType.rawClass,
        qualified = "${declaration.name}.${method.name}",
        method = method,
        doc = docs?.methods?.get(method)
      )
    }

fun methodWithMostConcreteReturnType(clazz: Class<*>, methods: List<Method>): Pair<Method, Type> {
  return methods
    .map { methodWithResolvedReturnType(clazz, it) }
    .sortedWith(typeHierarchyComparator { it.second })
    .first()
}

private fun methodWithResolvedReturnType(clazz: Class<*>, method: Method): Pair<Method, Type> {
  val returnType = getTypeArguments(clazz, method.declaringClass)[method.genericReturnType]
  return method to (returnType ?: method.genericReturnType)
}

private fun <T> typeHierarchyComparator(selector: (T) -> Type) = Comparator<T> { t1, t2 ->
  if (selector(t1).rawClass.isAssignableFrom(selector(t2).rawClass)) 1 else -1
}

private fun methodCallSignature(method: Method) =
  method.name + "(${method.genericParameterTypes.map { it.typeName }.joinToString(", ")})"

private fun findPublicNonInternalTypes(sourcePackage: String): List<Class<*>> {
  val reflections = Reflections(ConfigurationBuilder()
    .setScanners(SubTypesScanner(false), ResourcesScanner())
    .setUrls(ClasspathHelper.forJavaClassPath())
    .filterInputsBy(FilterBuilder().include(FilterBuilder.prefix(sourcePackage)))
  )
  return reflections.getSubTypesOf(java.lang.Object::class.java)
    .filter { Modifier.isPublic(it.modifiers) }
    .filterNot { type ->
      type.annotations.any {
        it.annotationClass.simpleName?.toLowerCase()?.contains("internal") ?: false
      }
    }
    .filterNot { it.simpleName.startsWith("Default") || it.simpleName.endsWith("Impl") }
}
