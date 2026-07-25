package com.ismartcoding.plain.features.contact

import kotlinx.serialization.Serializable

@Serializable
data class DPhone(val value: String, val normalized: String, val type: String)

@Serializable
data class DEmail(val value: String, val type: String)

/** A contact (spec §6 `contacts`). */
@Serializable
data class DContact(
    val id: String,
    val displayName: String,
    val phones: List<DPhone> = emptyList(),
    val emails: List<DEmail> = emptyList(),
    val starred: Boolean = false,
)

/** A contacts account/source (spec §6 `contactSources`). */
@Serializable
data class ContactSource(val name: String, val type: String)

/** A contact group (spec §6 `contactGroups`). */
@Serializable
data class ContactGroup(val id: String, val title: String, val accountName: String)
