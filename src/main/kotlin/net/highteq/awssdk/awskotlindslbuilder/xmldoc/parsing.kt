package net.highteq.awssdk.awskotlindslbuilder.xmldoc

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

private val kotlinXmlMapper =
  XmlMapper(JacksonXmlModule().apply { setDefaultUseWrapper(false) })
    .registerKotlinModule()
    .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)


internal inline fun <reified T : Any> parseAs(resource: File): T =
  kotlinXmlMapper.readValue(resource, T::class.java)
