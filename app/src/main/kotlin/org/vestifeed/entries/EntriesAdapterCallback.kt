package org.vestifeed.entries

fun interface EntriesAdapterCallback {
    fun onItemClick(item: EntriesAdapter.Item)
}