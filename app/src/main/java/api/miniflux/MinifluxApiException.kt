package api.miniflux

import okio.IOException

class MinifluxApiException(message: String) : IOException(message)