package org.vestifeed.parser

import org.w3c.dom.Node
import org.w3c.dom.NodeList

fun NodeList.list(): List<Node> {
    val list = mutableListOf<Node>()

    repeat(length) {
        list += item(it)
    }

    return list
}