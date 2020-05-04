/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.source

import net.highteq.awssdk.awskotlindslbuilder.Index
import net.highteq.awssdk.awskotlindslbuilder.rawClass
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
import java.lang.reflect.Type


fun scanSource(superType: Class<*>, sourcePackage: String, docs: Docs?): SourceModel {
  val methodIndex = createMethodIndex(sourcePackage, docs)

  val builderTypeMap = findBuildersWithTargets(methodIndex)
    .map { (builderClass, targetClass) ->
      val builderDeclaration = TypeDeclaration(
        name = builderClass.name.substringAfterLast('.').replace('$', '.'),
        qualified = builderClass.name,
        type = builderClass,
        doc = docs?.types?.get(builderClass)
      )
      val targetDeclaration = TypeDeclaration(
        name = targetClass.simpleName,
        qualified = targetClass.name,
        type = targetClass,
        doc = docs?.types?.get(targetClass)
      )

      targetClass to BuilderModel(
        builder = builderDeclaration,
        target = targetDeclaration,
        methodGroups = findBuilderMethods(methodIndex, builderDeclaration)
      )
    }
    .toMap()

  return SourceModel(superType, builderTypeMap, methodIndex)
}

private fun findBuildersWithTargets(methodIndex: Index<MethodGroupModel>): List<Pair<Class<*>, Class<*>>> {
  return methodIndex.values
    .flatMap { it.methods }
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

private fun findBuilderMethods(methodIndex: Index<MethodGroupModel>, builderDeclaration: TypeDeclaration): Map<String, MethodGroupModel> {
  return methodIndex.values
    .filter { it.owner.type == builderDeclaration.type }
    .filterNot { it.name in listOf("applyMutation", "copy") }
    .map { it.name to it }
    .toMap()
}

private fun createMethodIndex(sourcePackage: String, docs: Docs?): Index<MethodGroupModel> {
  return Index(
    findPublicNonInternalTypes(sourcePackage)
      .map { type ->
        val typeDeclaration = TypeDeclaration(
          name = type.rawClass.name.substringAfterLast('.').replace('$', '.'),
          qualified = type.rawClass.name,
          type = type,
          doc = docs?.types?.get(type.rawClass)
        )
        findMethodGroups(typeDeclaration, docs)
      }
      .flatMap { it.values }
      .map { it.qualified to it }
      .toMap()
  )
}

private fun findMethodGroups(declaration: TypeDeclaration, docs: Docs?) =
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
    .groupBy { it.method.name }
    .mapValues { (name, methods) ->
      MethodGroupModel(
        owner = declaration,
        name = name,
        qualified = methods.first().qualified,
        methods = methods
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
