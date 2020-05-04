/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.source

import net.highteq.awssdk.awskotlindslbuilder.Index
import net.highteq.awssdk.awskotlindslbuilder.pairOrNull
import net.highteq.awssdk.awskotlindslbuilder.xmldoc.Docs
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner
import org.springframework.core.ResolvableType
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.util.ReflectionUtils
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder
import java.lang.reflect.*


fun scanSource(superType: Class<*>, sourcePackage: String, docs: Docs?): SourceModel {
  val methodIndex = createMethodIndex(sourcePackage, docs)

  methodIndex.values
    .filter { false }
    .flatMap { it.methods }
    .filter { it.name == "build" }
    .filter { it.owner.type == DynamoDbClientBuilder::class.java }
    .onEach {
      val method = it.method
      println(
        "${method.genericReturnType.typeName} " +
        "${method.declaringClass.name}.${method.name}" +
        "(${method.genericParameterTypes.map { it.typeName }.joinToString(", ")})"
      )
      if(method.genericReturnType is TypeVariable<*>){
        if(method.declaringClass.typeParameters.isNotEmpty()) {
          method.genericReturnType
          println("ParameterizedType")
          val paramIndex = method.declaringClass.typeParameters.indexOf(method.genericReturnType)
          if(paramIndex >= 0) {
            val resolvedType = ResolvableType
              .forClass(it.owner.type.rawClass)
              .`as`(method.declaringClass)

            println("  builder: " + it.owner.type.typeName)
            println("  target:  " + resolvedType.getGeneric(paramIndex))
          }
        }
      }
      println("  builder: " + it.owner.type.typeName)
      println("  target:  " + it.returnType.simpleName)
    }

  val builderTypeMap = findResolvableSubtypesOf(superType, sourcePackage)
    .mapNotNull { pairOrNull(it.getGeneric(0).resolve(), it.getGeneric(1).resolve()) }
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
        methodGroups = findMethodGroups(builderDeclaration, docs)
          .filterNot { it.value.name in listOf( "applyMutation", "copy") }
          .filter {
            // TODO: real check for generic type buidlers or at least accept a list of possible builder names
            it.value.methods.any { method ->
              superType.isAssignableFrom(method.returnType)
                || method.returnType.name.endsWith("Builder")
            }
          }
      )
    }
    .toMap()

  return SourceModel(superType, builderTypeMap, methodIndex)
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
  ReflectionUtils
    .getAllDeclaredMethods(declaration.type.rawClass)
    .filter {
      Modifier.isPublic(it.modifiers) && !it.name.contains('$')
    }
    .filter {
      declaration.type == DynamoDbClientBuilder::class.java && it.name == "build"
    }
    .onEach { println("$it") }
    .groupBy { methodCallSignature(it) }
    .onEach { println("$it") }
    .map { methodWithMostConcreteReturnType(declaration.type.rawClass, it.value) }
    .onEach { (method, returnType) ->
      println("Most Concrete")
      println(
        "${method.genericReturnType.typeName} " +
          "${method.declaringClass.name}.${method.name}" +
          "(${method.genericParameterTypes.map { it.typeName }.joinToString(", ")})"
      )
      println("  builder: " + declaration.type.rawClass.simpleName)
      println("  target:  " + returnType.rawClass.simpleName)
    }
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
    .sortedWith(
      Comparator{
        t1, t2 ->
          if(t1.second.rawClass.isAssignableFrom(t2.second.rawClass))
            1
          else
            -1
      }
    )
    .first()
}

fun methodWithResolvedReturnType(clazz: Class<*>, method: Method): Pair<Method, Type> {
  if(method.genericReturnType is TypeVariable<*> && method.declaringClass.typeParameters.isNotEmpty()) {
    val paramIndex = method.declaringClass.typeParameters.indexOf(method.genericReturnType)
    if(paramIndex >= 0) {
      val resolvedType = ResolvableType
        .forClass(clazz)
        .`as`(method.declaringClass)
      val returnType = resolvedType.getGeneric(paramIndex).resolve()
      if(returnType != null){
        return method to returnType
      }
    }
  }
  return method to method.genericReturnType
}

private fun methodCallSignature(method: Method) =
  "${method.name}" +
  "(${method.genericParameterTypes.map { it.typeName }.joinToString(", ")})"

private fun findResolvableSubtypesOf(type: Class<*>, sourcePackage: String) =
  findBeanDefinitionsOf(type, sourcePackage)
    .map {
      ResolvableType
        .forClass(it.resolveBeanClass(null))
        .`as`(type)
    }

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
        it.annotationClass.simpleName?.toLowerCase()?.contains("internal")?:false
      }
    }
    .filterNot { it.simpleName.startsWith("Default") || it.simpleName.endsWith("Impl") }
}

private fun findBeanDefinitionsOf(type: Class<*>, sourcePackage: String): List<AbstractBeanDefinition> {
  return SimpleBeanDefinitionRegistry()
    .run {
      val scanner = ClassPathBeanDefinitionScanner(this).apply {
        setIncludeAnnotationConfig(false)
        addIncludeFilter(AssignableTypeFilter(type))
      }
      scanner.scan(sourcePackage)
      beanDefinitionNames.map { getBeanDefinition(it) }
    }
    .filterIsInstance<AbstractBeanDefinition>()
}

val Type.rawClass : Class<*> get() = when (this) {
    is Class<*> -> this
    is ParameterizedType -> this.rawType.rawClass
    else -> Object::class.java
}
