package eu.darken.capod.common.lists.differ

interface HasAsyncDiffer<T : DifferItem> {

    val data: List<T>
        get() = asyncDiffer.currentList

    val asyncDiffer: AsyncDiffer<*, T>

}