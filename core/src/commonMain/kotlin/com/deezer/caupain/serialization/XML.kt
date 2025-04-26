package com.deezer.caupain.serialization

import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy

internal val DefaultXml = XML {
    defaultPolicy {
        ignoreUnknownChildren()
        encodeDefault = XmlSerializationPolicy.XmlEncodeDefault.NEVER
    }
    repairNamespaces = true
    xmlDeclMode = XmlDeclMode.None
    autoPolymorphic = false
}