package net.bible.service.download

import kotlinx.serialization.Serializable

@Serializable
data class PseudoBook(
    val id: String,
    val suggested: String,
)

