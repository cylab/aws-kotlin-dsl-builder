/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.source

import net.highteq.awssdk.awskotlindslbuilder.pairOrNull
import net.highteq.awssdk.awskotlindslbuilder.xmldoc.Docs
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner
import org.springframework.core.ResolvableType
import org.springframework.core.type.filter.AssignableTypeFilter
import org.springframework.util.ReflectionUtils
import software.amazon.awssdk.utils.builder.SdkBuilder


fun scanSource(sourcePackage: String, docs: Docs?) = SourceModel(
  findResolvableSubtypesOf(SdkBuilder::class.java, sourcePackage)
    .mapNotNull { pairOrNull(it.getGeneric(0).resolve(), it.getGeneric(1).resolve()) }
    .map { (builderClass, targetClass) ->
      targetClass to BuilderModel(
        builder = TypeDeclaration(
          name = builderClass.simpleName,
          qualified = builderClass.name,
          type = builderClass,
          doc = docs?.types?.get(builderClass)
        ),
        target = TypeDeclaration(
          name = targetClass.simpleName,
          qualified = targetClass.name,
          type = targetClass,
          doc = docs?.types?.get(targetClass)
        ),
        methodGroups = findMethodGroups(builderClass, docs)
      )
    }
    .toMap()
)

private fun findMethodGroups(type: Class<*>, docs: Docs?) =
  ReflectionUtils
    .getAllDeclaredMethods(type)
    .filter { it.parameterCount == 1 }
    .filterNot {
      it.name in listOf(
        "applyMutation",
        "httpClientBuilder"
      )
    }
//    .filter { it.returnType.name == type.name && it.parameterCount == 1 }
    .map {
      MethodModel(
        name = it.name,
        qualified = "${type.name.replace('$','.')}.${it.name}",
        method = it,
        doc = docs?.methods?.get(it)
      )
    }
    .groupBy { it.method.name }
    .mapValues { (name, methods) ->
      MethodGroupModel(
        name = name,
        qualified = methods.first().qualified,
        methods = methods
      )
    }


private fun findResolvableSubtypesOf(type: Class<*>, sourcePackage: String) =
  SimpleBeanDefinitionRegistry()
    .run {
      ClassPathBeanDefinitionScanner(this).apply {
        setIncludeAnnotationConfig(false)
        addIncludeFilter(AssignableTypeFilter(type))
        scan(sourcePackage)
      }
      beanDefinitionNames.map { this.getBeanDefinition(it) }
    }
    .filterIsInstance<AbstractBeanDefinition>()
    .map {
      ResolvableType
        .forClass(it.resolveBeanClass(null))
        .`as`(type)
    }
