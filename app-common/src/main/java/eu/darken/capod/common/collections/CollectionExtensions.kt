package eu.darken.capod.common.collections


fun Collection<Int>.median(): Int = this.sorted().let {
    if (it.size % 2 == 0) {
        (it[it.size / 2] + it[(it.size - 1) / 2]) / 2
    } else {
        it[it.size / 2]
    }
}