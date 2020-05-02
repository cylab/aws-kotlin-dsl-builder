package net.highteq.awssdk.awskotlindslbuilder.target

val targetTypeMapping : Map<Class<*>, String> = mapOf(
  software.amazon.awssdk.http.SdkHttpClient.Builder::class.java to "SdkHttpClient.Builder<*>",
  software.amazon.awssdk.http.async.SdkAsyncHttpClient.Builder::class.java to "SdkAsyncHttpClient.Builder<*>"
)

val extraTargetTypeDSLMapping : Map<Any, String> = mapOf(
  "java.util.Map<java.lang.String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>"
    to "net.highteq.cylab.awssdk.dynamodb.kotlin.dsl.ext.model.AttributeMapDSL"
)
