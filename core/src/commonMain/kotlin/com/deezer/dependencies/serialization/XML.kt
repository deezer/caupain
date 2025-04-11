package com.deezer.dependencies.serialization

import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML

val DefaultXml = XML {
    defaultPolicy {
        ignoreUnknownChildren()
    }
    repairNamespaces = true
    xmlDeclMode = XmlDeclMode.None
    autoPolymorphic = false
}