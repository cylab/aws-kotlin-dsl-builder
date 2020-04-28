package net.highteq.awssdk.awskotlindslbuilder.target

import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.lang.reflect.Type

val extraTargetTypeDSLMapping : Map<Any, String> = mapOf(
  "java.util.Map<java.lang.String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>"
    to "net.highteq.cylab.awssdk.dynamodb.kotlin.dsl.ext.model.AttributeMapDSL"
)
