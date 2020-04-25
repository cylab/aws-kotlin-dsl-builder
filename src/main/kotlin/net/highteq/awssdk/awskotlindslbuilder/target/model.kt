/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.target

class DSLModel(
  val dslMarker: DSLMarkerModel,
  val collectionDSLs: List<CollectionDSLModel>,
  val typeDLSs: List<TypeDSLModel>
)

class DSLMarkerModel(
  val packageName: String,
  val name: String
) {
  val qualified get() = "$packageName.$name"
}

class CollectionDSLModel(
  val packageName: String,
  val imports: Set<String>,
  val comment: String,
  val annotations: Set<String>,
  val name: String,
  val dslEntrypoint: String,
  val targetType: String,
  val targetDSLType: String
) {
  val qualified get() = "$packageName.$name"
}

class TypeDSLModel(
  val packageName: String,
  val imports: Set<String>,
  val comment: String,
  val annotations: Set<String>,
  val name: String,
  val dslEntrypoint: String,
  val targetType: String,
  val dslProperties: List<DSLPropertyModel>,
  val dslFunctions: List<DSLFunctionModel>,
  val subDSLs: List<SubDSLModel>
) {
  val qualified get() = "$packageName.$name"
  fun hasDslBlock() = dslProperties.isNotEmpty() || dslFunctions.isNotEmpty() || subDSLs.isNotEmpty()
}

class DSLPropertyModel(
  val comment: String,
  val name: String,
  val targetType: String
)

class DSLFunctionModel(
  val comment: String,
  val name: String,
  val targetType: String
)

class SubDSLModel(
  val comment: String,
  val name: String,
  val targetType: String,
  val targetDSLType: String,
  val targetDSLEntrypoint: String
)
