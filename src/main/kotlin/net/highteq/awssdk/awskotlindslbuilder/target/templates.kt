/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.target

import net.highteq.awssdk.awskotlindslbuilder.*

fun header() =
  """
  /*
    This file was generated from https://github.com/aws/aws-sdk-java-v2 by https://github.com/cylab/aws-kotlin-dsl-builder
    Like the original code, this file and project is licensed under
    Apache License Version 2.0
    See LICENSE.txt for more info
  */
  """

fun dslMarker(model: DSLMarkerModel) =
  """
  package ${model.packageName}

  @DslMarker
  annotation class ${model.name}
  """


fun collectionDSL(model: CollectionDSLModel) =
  """
  package ${model.packageName}
  
  ${imports(model.imports)}
  
  /**
    * ${comment(model.comment)}
    */
  ${annotations(model.annotations)}
  class ${model.name} {
    private val list = ArrayList<${model.targetType}>()
    internal fun build() : List<${model.targetType}> = list

    fun item(init: ${model.targetDSLType}.() -> Unit) {
      list.add(${model.targetDSLType}().apply(init).build())
    }

    operator fun ${model.targetType}.unaryPlus() {
      list.add(this)
    }
  
    operator fun Collection<${model.targetType}>.unaryPlus() {
      list.addAll(this)
    }
  
    operator fun Array<${model.targetType}>.unaryPlus() {
      list.addAll(this)
    }
  }

  /**
    * ${comment(model.comment)}
    */
  fun ${model.dslEntrypoint}(dslBlock: ${model.name}.() -> Unit) =
    ${model.name}().apply(dslBlock).build()
  """


fun typeDSL(model: TypeDSLModel) =
  """
  @file:Suppress("DEPRECATION")
  package ${model.packageName}
  
  import kotlin.DeprecationLevel.HIDDEN
  import kotlin.DeprecationLevel.WARNING
  ${imports(model.imports)}

  /**
    * ${comment(model.comment)}
    */
  ${annotations(model.annotations)}
  class ${model.name} {
    @Deprecated("Usage of the builder field is not recommended. It might vanish in any new release!", level = WARNING)
    internal val builder = ${model.targetType}.builder()
    internal fun build(): ${model.targetType} = builder.build()
    ${dslProperties(model.dslProperties)}
    ${dslFunctions(model.dslFunctions)}
    ${subDSLs(model.subDSLs)}
  }

  /**
    * ${comment(model.comment)}
    */
  fun ${model.dslEntrypoint}(dslBlock: ${model.name}.() -> Unit) =
    ${model.name}().apply(dslBlock).build()
  """


fun dslProperty(model: DSLPropertyModel) =
  """
  /**
    * ${comment(model.comment)}
    */
  var ${model.name}: ${model.targetType}
    @Deprecated("", level = HIDDEN) // Hide from Kotlin callers
    get() = throw UnsupportedOperationException()
    set(value) {
      builder.${model.name}(value)
    }
  """


fun dslFunction(model: DSLFunctionModel) =
  """
  /**
    * ${comment(model.comment)}
    */
  fun ${model.name}(value: ${model.targetType}) {
    builder.${model.name}(value)
  }
  """


fun subDSL(model: SubDSLModel) =
  """
  /**
    * ${comment(model.comment)}
    */
  fun ${model.name}(dslBlock: ${model.targetDSLType}.() -> Unit) {
    builder.${model.name}(${model.targetDSLEntrypoint}(dslBlock))
  }
  """
