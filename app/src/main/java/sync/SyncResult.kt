package sync

sealed class SyncResult {
    class Ok(val newAndUpdatedEntries: Int) : SyncResult()
    class Err(val e: Exception) : SyncResult()
}
