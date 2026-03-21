package org.vestifeed.navigation

import android.os.Bundle

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
