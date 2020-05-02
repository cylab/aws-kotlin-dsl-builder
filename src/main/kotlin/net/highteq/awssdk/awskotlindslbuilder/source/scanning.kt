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


fun scanSource(superType: Class<*>, sourcePackage: String, docs: Docs?): SourceModel {
  val subTypeMap = findResolvableSubtypesOf(superType, sourcePackage)
    .mapNotNull { pairOrNull(it.getGeneric(0).resolve(), it.getGeneric(1).resolve()) }
    .map { (builderClass, targetClass) ->
      targetClass to BuilderModel(
        builder = TypeDeclaration(
          name = builderClass.name.substringAfterLast('.').replace('$','.'),
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
        methodGroups = findMethodGroups(superType, builderClass, docs)
      )
    }
    .toMap()
  return SourceModel(superType, subTypeMap)
}

private fun findMethodGroups(superType: Class<*>, type: Class<*>, docs: Docs?) =
  ReflectionUtils
    .getAllDeclaredMethods(type)
    .filterNot {
      it.name in listOf(
        "applyMutation",
        "copy" // todo -> better fix for that!
      )
    }
    .filter {
      // TODO: real check for generic type buidlers or at least accept a list of possible builder names
      superType.isAssignableFrom(it.returnType)
        || it.returnType.name.endsWith("Builder")
    }
    .map {
      MethodModel(
        name = it.name,
        qualified = "${type.name}.${it.name}",
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
      val scanner = ClassPathBeanDefinitionScanner(this).apply {
        setIncludeAnnotationConfig(false)
        addIncludeFilter(AssignableTypeFilter(type))
      }
      scanner.scan(sourcePackage)
      beanDefinitionNames.map { getBeanDefinition(it) }
    }
    .filterIsInstance<AbstractBeanDefinition>()
    .map {
      ResolvableType
        .forClass(it.resolveBeanClass(null))
        .`as`(type)
    }
