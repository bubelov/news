package entries

import db.EntryWithoutContent
import db.Link

interface EntriesAdapterCallback {

    fun onItemClick(item: EntriesAdapterItem)

    fun onDownloadAudioEnclosureClick(entry: EntryWithoutContent, link: Link)

    fun onPlayAudioEnclosureClick(entry: EntryWithoutContent, link: Link)
}