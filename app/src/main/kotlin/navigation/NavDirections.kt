package navigation

import android.os.Bundle
import androidx.core.os.bundleOf
import entries.EntriesFilter

object NavDirections {
    object AuthFragment {
        fun actionAuthFragmentToNewsFragment(filter: EntriesFilter?): Bundle {
            return bundleOf("filter" to filter)
        }

        fun actionAuthFragmentToFeedsFragment(): Bundle {
            return Bundle()
        }

        fun actionAuthFragmentToMinifluxAuthFragment(): Bundle {
            return Bundle()
        }

        fun actionAuthFragmentToNextcloudAuthFragment(): Bundle {
            return Bundle()
        }
    }

    object MinifluxAuthFragment {
        fun actionMinifluxAuthFragmentToNewsFragment(): Bundle {
            return bundleOf("filter" to EntriesFilter.NotBookmarked)
        }
    }

    object NextcloudAuthFragment {
        fun actionNextcloudAuthFragmentToNewsFragment(): Bundle {
            return bundleOf("filter" to EntriesFilter.NotBookmarked)
        }
    }

    object EntriesFragment {
        fun actionEntriesFragmentToEntryFragment(entryId: String): Bundle {
            return bundleOf("entryId" to entryId)
        }

        fun actionEntriesFragmentToSearchFragment(): Bundle {
            return Bundle()
        }

        fun actionEntriesFragmentToSettingsFragment(): Bundle {
            return Bundle()
        }
    }

    object FeedsFragment {
        fun actionFeedsFragmentToFeedEntriesFragment(filter: EntriesFilter?): Bundle {
            return bundleOf("filter" to filter)
        }

        fun actionFeedsFragmentToFeedSettingsFragment(url: String): Bundle {
            return bundleOf("url" to url)
        }
    }

    object EntryFragment {
        fun actionEntryFragmentToFeedSettingsFragment(feedId: String): Bundle {
            return bundleOf("feedId" to feedId)
        }
    }

    object SearchFragment {
        fun actionSearchFragmentToEntryFragment(entryId: String): Bundle {
            return bundleOf("entryId" to entryId)
        }
    }

    object SettingsFragment {
        fun actionSettingsFragmentToEnclosuresFragment(): Bundle {
            return Bundle()
        }
    }
}

class AuthFragmentArgs(args: Bundle) {
    companion object {
        fun fromBundle(bundle: Bundle): AuthFragmentArgs {
            return AuthFragmentArgs(bundle)
        }
    }
}

class MinifluxAuthFragmentArgs(args: Bundle) {
    companion object {
        fun fromBundle(bundle: Bundle): MinifluxAuthFragmentArgs {
            return MinifluxAuthFragmentArgs(bundle)
        }
    }
}

class NextcloudAuthFragmentArgs(args: Bundle) {
    companion object {
        fun fromBundle(bundle: Bundle): NextcloudAuthFragmentArgs {
            return NextcloudAuthFragmentArgs(bundle)
        }
    }
}

class EntriesFragmentArgs(args: Bundle) {
    val filter: EntriesFilter? by lazy { args.getParcelable("filter") }

    companion object {
        fun fromBundle(bundle: Bundle): EntriesFragmentArgs {
            return EntriesFragmentArgs(bundle)
        }
    }
}

class FeedsFragmentArgs(args: Bundle) {
    val url: String by lazy { args.getString("url", "") }

    companion object {
        fun fromBundle(bundle: Bundle): FeedsFragmentArgs {
            return FeedsFragmentArgs(bundle)
        }
    }
}

class EntryFragmentArgs(args: Bundle) {
    val entryId: String by lazy { args.getString("entryId", "") }

    companion object {
        fun fromBundle(bundle: Bundle): EntryFragmentArgs {
            return EntryFragmentArgs(bundle)
        }
    }
}

class SearchFragmentArgs(args: Bundle) {
    companion object {
        fun fromBundle(bundle: Bundle): SearchFragmentArgs {
            return SearchFragmentArgs(bundle)
        }
    }
}

class FeedSettingsFragmentArgs(args: Bundle) {
    val feedId: String by lazy { args.getString("feedId", "") }

    companion object {
        fun fromBundle(bundle: Bundle): FeedSettingsFragmentArgs {
            return FeedSettingsFragmentArgs(bundle)
        }
    }
}

class SettingsFragmentArgs(args: Bundle) {
    companion object {
        fun fromBundle(bundle: Bundle): SettingsFragmentArgs {
            return SettingsFragmentArgs(bundle)
        }
    }
}

class EnclosuresFragmentArgs(args: Bundle) {
    companion object {
        fun fromBundle(bundle: Bundle): EnclosuresFragmentArgs {
            return EnclosuresFragmentArgs(bundle)
        }
    }
}

fun Bundle.putParcelable(key: String, value: android.os.Parcelable?) {
    putParcelable(key, value)
}
