@file:Suppress("UnusedImports") // Detekt bug

package com.deezer.caupain.serialization.xml

import nl.adaptivity.xmlutil.XmlDelegatingReader
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.localPart
import nl.adaptivity.xmlutil.namespaceURI
import nl.adaptivity.xmlutil.prefix
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor

internal class DynamicTagReader(
    private val idPropertyName: String,
    reader: XmlReader,
    descriptor: XmlDescriptor
) : XmlDelegatingReader(reader) {

    private val filterDepth = delegate.depth - reader.depth

    private val elementName = descriptor.tagName

    private val idAttrName = (0 until descriptor.elementsCount)
        .first { descriptor.serialDescriptor.getElementName(it) == idPropertyName }
        .let { descriptor.getElementDescriptor(it) }
        .tagName

    private val idValue = delegate.localName

    override val attributeCount: Int
        get() = if (filterDepth == 0) super.attributeCount + 1 else super.attributeCount

    override fun getAttributeNamespace(index: Int): String = if (filterDepth == 0) {
        if (index == 0) idAttrName.namespaceURI else super.getAttributeNamespace(index - 1)
    } else {
        super.getAttributeNamespace(index)
    }

    override fun getAttributePrefix(index: Int): String = if (filterDepth == 0) {
        if (index == 0) idAttrName.prefix else super.getAttributePrefix(index - 1)
    } else {
        super.getAttributePrefix(index)
    }

    override fun getAttributeLocalName(index: Int): String = if (filterDepth == 0) {
        if (index == 0) idAttrName.localPart else super.getAttributeLocalName(index - 1)
    } else {
        super.getAttributeLocalName(index)
    }

    override fun getAttributeValue(index: Int): String = if (filterDepth == 0) {
        if (index == 0) idValue else super.getAttributeValue(index - 1)
    } else {
        super.getAttributeValue(index)
    }

    @Suppress("UnnecessaryParentheses") // More understandable this way
    override fun getAttributeValue(nsUri: String?, localName: String): String? =
        if (
            filterDepth == 0 &&
            nsUri.orEmpty() == idAttrName.namespaceURI &&
            localName == idAttrName.localPart
        ) {
            idValue
        } else {
            super.getAttributeValue(nsUri, localName)
        }

    override val namespaceURI: String
        get() = if (filterDepth == 0) elementName.namespaceURI else super.namespaceURI

    override val localName: String
        get() = if (filterDepth == 0) elementName.localPart else super.localName

    override val prefix: String
        get() = if (filterDepth == 0) elementName.prefix else super.prefix
}
