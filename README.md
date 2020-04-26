# AWS Kotlin DSL Builder

A DSL generator based on reflection and javadoc converted to xml

## TODOs

- Configure nullability (at least for attributevalue)
- Automatic download of sources and javadoc xml generation 
- Convert javadoc html comments to real markdown
- Switch to generation based solely on javadoc xml, if possible
- Allow overriding of single dsl methods, if the generic implementation is not sufficient
- Finalize generation of gradle projects including upload to maven central
- Automatic build via GitHub CI?
- Automatic dependency update and rebuild on AWS-SDK update
- (Optional) Migration to Kotlin Flow?

## Notes 

- https://github.com/MarkusBernhardt/xml-doclet


    javadoc -doclet com.github.markusbernhardt.xmldoclet.XmlDoclet -docletpath xml-doclet-1.0.5-jar-with-dependencies.jar -filename dynamodb.xml -sourcepath dynamodb-sources/ -subpackages software

## Author

Mathias Henze ( cylab at highteq dot net )
