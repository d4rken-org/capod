package eu.darken.cap.common.lists.differ

import eu.darken.cap.common.lists.ListItem

interface DifferItem : ListItem {
    val stableId: Long

    val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)?
        get() = null
}