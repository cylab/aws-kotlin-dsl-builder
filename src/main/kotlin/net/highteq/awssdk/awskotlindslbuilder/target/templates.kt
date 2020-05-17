/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.target

fun header() = """
  /*
    This file was generated from https://github.com/aws/aws-sdk-java-v2 by https://github.com/cylab/aws-kotlin-dsl-builder
    Like the original code, this file and project is licensed under
    Apache License Version 2.0
    See LICENSE.txt for more info
  */
  
"""

fun dslScope(model: DSLScopeModel) = """
  package ${model.packageName}

  @DslMarker
  annotation class ${model.marker}
  
  class ${model.name} {
    companion object
  }

"""


fun collectionDSL(model: CollectionDSLModel) = """
  @file:Suppress("DEPRECATION", "NOTHING_TO_INLINE")
  package ${model.packageName}
  
  import kotlin.DeprecationLevel.WARNING
  ${imports(model.imports)}
  
  /**
    * ${comment(model.comment)}
    */
  ${annotations(model.annotations)}
  inline class ${model.name}(
    @PublishedApi
    @Deprecated("Don't use internal fields!", level = WARNING)
    internal val list : MutableList<${model.targetType}>
  ){
    @PublishedApi
    internal fun build() = list

    /**
      * Builds an object of type ${model.targetType} from 
      * the given DSL in 'dslBlock' and adds it to the collection
      */
    inline fun o(dslBlock: ${model.targetDSLType}.() -> Unit) {
      list.add(${model.targetDSLEntrypoint}(dslBlock))
    }

    /**
      * Adds a ${model.targetType} to the collection built by this DSL
      */
    inline operator fun ${model.targetType}.unaryPlus() {
      list.add(this)
    }

    /**
      * Adds all given ${model.targetType} instances to the collection built by this DSL
      */
    inline operator fun Collection<${model.targetType}>.unaryPlus() {
      list.addAll(this)
    }

    /**
      * Adds all given ${model.targetType} instances to the collection built by this DSL
      */
    inline operator fun Array<${model.targetType}>.unaryPlus() {
      list.addAll(this)
    }
  }

  /**
    * ${comment(model.comment)}
    */
  inline fun ${model.dslEntrypoint}(dslBlock: ${model.name}.() -> Unit) =
    ${model.name}(mutableListOf<${model.targetType}>()).apply(dslBlock).build()

  /**
    * ${comment(model.comment)}
    */
  inline fun ${model.scope.name}.Companion.${model.dslEntrypoint}(dslBlock: ${model.name}.() -> Unit) =
    ${model.name}(mutableListOf<${model.targetType}>()).apply(dslBlock).build()

"""

fun mapDSL(model: MapDSLModel) = """
  @file:Suppress("DEPRECATION", "NOTHING_TO_INLINE")
  package ${model.packageName}
  
  import kotlin.DeprecationLevel.WARNING
  ${imports(model.imports)}
  
  /**
    * ${comment(model.comment)}
    */
  ${annotations(model.annotations)}
  inline class ${model.name}(
    @PublishedApi
    @Deprecated("Don't use internal fields!", level = WARNING)
    internal val map : MutableMap<${model.keyType}, ${model.targetType}>
  ) {
    @PublishedApi
    internal fun build() : Map<${model.keyType}, ${model.targetType}> = map

    /**
      * Builds an object of type ${model.targetType} from 
      * the given DSL in 'dslBlock' and adds it to the map at ['key']
      */
    inline fun o(key: ${model.keyType}, dslBlock: ${model.targetDSLType}.() -> Unit) {
      map[key] = ${model.targetDSLEntrypoint}(dslBlock)
    }

    /**
      * Adds a pair of ${model.keyType} -> ${model.targetType} to the map
      */
    inline operator fun Pair<${model.keyType}, ${model.targetType}>.unaryPlus() {
      map[this.first] = this.second
    }

    /**
      * Adds all given Pair<${model.keyType}, ${model.targetType}> instances to the map
      */
    inline operator fun Collection<Pair<${model.keyType}, ${model.targetType}>>.unaryPlus() {
      this.forEach { map[it.first] = it.second }
    }

    /**
      * Adds all given Pair<${model.keyType}, ${model.targetType}> instances to the map
      */
    inline operator fun Array<Pair<${model.keyType}, ${model.targetType}>>.unaryPlus() {
      this.forEach { map[it.first] = it.second }
    }

    /**
      * Adds all entries in the given map
      */
    inline operator fun Map<${model.keyType}, ${model.targetType}>.unaryPlus() {
      map.putAll(this)
    }
  }

  /**
    * ${comment(model.comment)}
    */
  inline fun ${model.dslEntrypoint}(dslBlock: ${model.name}.() -> Unit) =
    ${model.name}(mutableMapOf<${model.keyType}, ${model.targetType}>()).apply(dslBlock).build()

  /**
    * ${comment(model.comment)}
    */
  inline fun ${model.scope.name}.Companion.${model.dslEntrypoint}(dslBlock: ${model.name}.() -> Unit) =
    ${model.name}(mutableMapOf<${model.keyType}, ${model.targetType}>()).apply(dslBlock).build()

"""


fun typeDSL(model: TypeDSLModel) = """
  @file:Suppress("DEPRECATION", "NOTHING_TO_INLINE")
  package ${model.packageName}
  
  import kotlin.DeprecationLevel.HIDDEN
  import kotlin.DeprecationLevel.WARNING
  ${imports(model.imports)}

  /**
    * ${comment(model.comment)}
    */
  ${annotations(model.annotations)}
  inline class ${model.name}(
    @Deprecated("Usage of the builder field is not recommended. It might vanish in any new release!", level = WARNING)
    val builder: ${model.builderType}
  ){
    @PublishedApi
    internal fun build(): ${model.targetType} = builder.build()
    ${dslProperties(model.dslProperties)}
    ${dslSecondaries(model.dslSecondaries)}
    ${dslFunctions(model.dslFunctions)}
    ${subDSLs(model.subDSLs)}
  }

  /**
    * ${comment(model.comment)}
    */
  inline fun ${model.dslEntrypoint}(dslBlock: ${model.name}.() -> Unit) =
    ${model.name}(${model.targetType}.builder()).apply(dslBlock).build()

  /**
    * ${comment(model.comment)}
    */
  inline fun ${model.scope.name}.Companion.${model.dslEntrypoint}(dslBlock: ${model.name}.() -> Unit) =
    ${model.name}(${model.targetType}.builder()).apply(dslBlock).build()

  ${extDSLs(model.extDSLs)}

"""


fun dslProperties(dslProperties: List<DSLPropertyModel>) = dslProperties.sortedBy{ it.name }
  .map { dslProperty(it).prependIndent("  ") }.joinToString("\n")

fun dslProperty(model: DSLPropertyModel) = """
  /**
    * ${comment(model.comment)}
    */
  inline var ${model.name}: ${model.targetType}
    @Deprecated("", level = HIDDEN) // Hide from Kotlin callers
    get() = throw UnsupportedOperationException()
    set(value) {
      builder.${model.name}(value)
    }

"""


fun dslSecondaries(dslSecondaries: List<DSLPropertyModel>) = dslSecondaries.sortedBy{ it.name }
  .map { dslSecondary(it).prependIndent("  ") }.joinToString("\n")

fun dslSecondary(model: DSLPropertyModel) = """
  /**
    * ${comment(model.comment)}
    */
  inline fun ${model.name}(value: ${model.targetType}) {
    builder.${model.name}(value)
  }
  
"""


fun dslFunctions(dslFunctions: List<DSLFunctionModel>) = dslFunctions.sortedBy{ it.name }
  .map { dslFunction(it).prependIndent("  ") }.joinToString("\n")

fun dslFunction(model: DSLFunctionModel) = """
  /**
    * ${comment(model.comment)}
    */
  inline fun ${model.name}() {
    builder.${model.name}()
  }

"""


fun subDSLs(subDSLs: List<SubDSLModel>) = subDSLs.sortedBy{ it.name }
  .map { subDSL(it).prependIndent("  ") }.joinToString("\n")

fun subDSL(model: SubDSLModel) = """
  /**
    * ${comment(model.comment)}
    */
  inline fun ${model.name}(dslBlock: ${model.targetDSLType}.() -> Unit) {
    builder.${model.name}(${model.targetDSLEntrypoint}(dslBlock))
  }

"""


fun extDSLs(extDSLs: List<ExtDSLModel>) = extDSLs.sortedBy{ "${it.receiverType}ZZZZZZ${it.name}" }
    .map { extDSL(it).prependIndent("") }.joinToString("\n")

fun extDSL(model: ExtDSLModel) = """
  /**
    * ${comment(model.comment)}
    */
  inline fun ${model.receiverType}.${model.name}By(dslBlock: ${model.targetDSLType}.() -> Unit) =
    this.${model.name}(${model.targetDSLEntrypoint}(dslBlock))

"""


fun imports(set: Set<String>) = "import " + set.sorted().joinToString("\n  import ")

fun comment(text: String) = text.lines().joinToString("\n    * ")

fun annotations(set: Set<String>) = "@" + set.sorted().joinToString("\n  @")
