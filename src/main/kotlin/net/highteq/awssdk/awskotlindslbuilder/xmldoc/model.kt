package net.highteq.awssdk.awskotlindslbuilder.xmldoc

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.util.StdConverter
import net.highteq.awssdk.awskotlindslbuilder.Index


@JsonDeserialize(converter=Indexer::class)
data class Docs(
  @set:JsonProperty("package")
  var packages: List<PackageElement> = ArrayList(),
  var types: Index<TypeDeclarationElement> = Index(mapOf()),
  var methods: Index<MethodElement> = Index(mapOf())
)


sealed class NamedElement {
  @set:JsonProperty
  var name: String = ""
}


sealed class CommentedElement : NamedElement() {

  @set:JsonProperty
  var comment: String = ""
}


class PackageElement : CommentedElement() {

  @set:JsonProperty("class")
  var classes: List<ClassElement> = ArrayList()

  @set:JsonProperty("interface")
  var interfaces: List<InterfaceElement> = ArrayList()

  @set:JsonProperty("enum")
  var enums: List<EnumElement> = ArrayList()
}


sealed class QualifiedElement {
  @set:JsonProperty
  var qualified = ""
}


sealed class ScopedElement : CommentedElement() {
  @set:JsonProperty
  var qualified = ""

  @set:JsonProperty
  var scope = "public"
}


sealed class TypeDeclarationElement : ScopedElement() {
  @set:JsonProperty("method")
  var methods: List<MethodElement> = ArrayList()
}


class ClassElement : TypeDeclarationElement()

class InterfaceElement : TypeDeclarationElement()

class EnumElement : TypeDeclarationElement()


class MethodElement : ScopedElement() {
  @set:JsonProperty("signature")
  var signature = ""

  @set:JsonProperty("tag")
  var tags: List<TagElement> = ArrayList()

  @set:JsonProperty("parameter")
  var parameters: List<ParameterElement> = ArrayList()

  @set:JsonProperty("return")
  var returnType = ReturnElement()

  val key: String get() = "${qualified+signature}"
}


class TagElement : NamedElement() {

  @set:JsonProperty
  var text: String = ""
}


class ParameterElement : CommentedElement() {
  @set:JsonProperty
  var type = TypeElement()
}


class TypeElement : QualifiedElement()

class ReturnElement : QualifiedElement()


internal class Indexer : StdConverter<Docs, Docs>() {
  override fun convert(docs: Docs): Docs {

    val declarations = docs.packages
      .flatMap { listOf(it.interfaces, it.classes, it.enums) }
      .flatten()

    docs.types = Index(
      declarations
        .map { it.qualified to it }
        .toMap()
    )

    docs.methods = Index(
      declarations
        .flatMap { it.methods }
        .map { "${it.qualified}${it.signature}" to it }
        .toMap()
    )

    return docs
  }
}
