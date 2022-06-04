package entries

import db.Link

interface EntriesAdapterCallback {

    fun onItemClick(item: EntriesAdapterItem)

    fun onDownloadAudioEnclosureClick(link: Link)

    fun onPlayAudioEnclosureClick(link: Link)
}