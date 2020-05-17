/*
  This file as well as the whole project is licensed under
  Apache License Version 2.0
  See LICENSE.txt for more info
 */
package net.highteq.awssdk.awskotlindslbuilder.target

class DSLModel(
  val dslScope: DSLScopeModel,
  val collectionDSLs: List<CollectionDSLModel>,
  val mapDSLs: List<MapDSLModel>,
  val typeDLSs: List<TypeDSLModel>
)

sealed class DSLFileModel(
  val packageName: String,
  val name: String
) {
  val qualified = "$packageName.$name"
}

class DSLScopeModel(packageName: String, name: String)
  : DSLFileModel(packageName, name)
{
  val marker = "${name}Marker"
  val declarations = setOf("$packageName.$name", "$packageName.$marker")
}

class CollectionDSLModel(
  packageName: String,
  name: String,
  val scope: DSLScopeModel,
  val imports: Set<String>,
  val comment: String,
  val annotations: Set<String>,
  val dslEntrypoint: String,
  val targetType: String,
  val targetDSLType: String,
  val targetDSLEntrypoint: String
) : DSLFileModel(packageName, name)

class MapDSLModel(
  packageName: String,
  name: String,
  val scope: DSLScopeModel,
  val imports: Set<String>,
  val comment: String,
  val annotations: Set<String>,
  val dslEntrypoint: String,
  val keyType: String,
  val targetType: String,
  val targetDSLType: String,
  val targetDSLEntrypoint: String
) : DSLFileModel(packageName, name)

class TypeDSLModel(
  packageName: String,
  name: String,
  val scope: DSLScopeModel,
  val imports: Set<String>,
  val comment: String,
  val annotations: Set<String>,
  val dslEntrypoint: String,
  val builderType: String,
  val targetType: String,
  val dslProperties: List<DSLPropertyModel>,
  val dslSecondaries: List<DSLPropertyModel>,
  val dslFunctions: List<DSLFunctionModel>,
  val subDSLs: List<SubDSLModel>,
  val extDSLs: List<ExtDSLModel>
) : DSLFileModel(packageName, name) {
  fun hasDslBlock() = dslProperties.isNotEmpty() || dslSecondaries.isNotEmpty() || subDSLs.isNotEmpty()
}

class DSLPropertyModel(
  val name: String,
  val comment: String,
  val targetType: String
)

class DSLFunctionModel(
  val name: String,
  val comment: String
)

class SubDSLModel(
  val name: String,
  val comment: String,
  val imports: Set<String>,
  val targetType: String,
  val targetDSLType: String,
  val targetDSLEntrypoint: String
)

class ExtDSLModel(
  val name: String,
  val comment: String,
  val imports: Set<String>,
  val receiverType: String,
  val targetType: String,
  val targetDSLType: String,
  val targetDSLEntrypoint: String
)
