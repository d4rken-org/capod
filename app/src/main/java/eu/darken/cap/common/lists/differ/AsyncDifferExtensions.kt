package eu.darken.cap.common.lists.differ

import androidx.recyclerview.widget.RecyclerView
import eu.darken.cap.common.lists.modular.ModularAdapter


fun <X, T> X.update(newData: List<T>?)
        where X : HasAsyncDiffer<T>, X : RecyclerView.Adapter<*> {

    asyncDiffer.submitUpdate(newData ?: emptyList())
}

fun <A, T : DifferItem> A.setupDiffer(): AsyncDiffer<A, T>
        where A : HasAsyncDiffer<T>, A : ModularAdapter<*> =
    AsyncDiffer(this)
