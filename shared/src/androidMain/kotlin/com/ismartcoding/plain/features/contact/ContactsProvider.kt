package com.ismartcoding.plain.features.contact

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.ismartcoding.plain.platform.AndroidApp

/**
 * Reads/edits contacts via ContactsContract (spec §6). Listing uses two queries per page — the base
 * Contacts rows, then a single batched Data query for the page's phones/emails — to avoid N+1.
 */
object ContactsProvider {

    private val resolver: ContentResolver get() = AndroidApp.context.contentResolver

    fun count(): Int = resolver.query(
        ContactsContract.Contacts.CONTENT_URI,
        arrayOf(ContactsContract.Contacts._ID),
        null, null, null,
    )?.use { it.count } ?: 0

    fun contacts(offset: Int, limit: Int): List<DContact> {
        val ids = ArrayList<Long>(limit.coerceAtMost(256))
        val names = HashMap<Long, String>()
        val starred = HashMap<Long, Boolean>()
        pagedQuery(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.STARRED,
            ),
            ContactsContract.Contacts.DISPLAY_NAME,
            limit, offset,
        )?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val starIdx = c.getColumnIndex(ContactsContract.Contacts.STARRED)
            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                ids.add(id)
                names[id] = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                starred[id] = starIdx >= 0 && c.getInt(starIdx) == 1
            }
        }
        if (ids.isEmpty()) return emptyList()

        val phones = HashMap<Long, MutableList<DPhone>>()
        val emails = HashMap<Long, MutableList<DEmail>>()
        loadData(ids, phones, emails)

        return ids.map { id ->
            DContact(
                id = id.toString(),
                displayName = names[id] ?: "",
                phones = phones[id] ?: emptyList(),
                emails = emails[id] ?: emptyList(),
                starred = starred[id] ?: false,
            )
        }
    }

    fun sources(): List<ContactSource> {
        val seen = LinkedHashSet<Pair<String, String>>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
            null, null, null,
        )?.use { c ->
            val n = c.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
            val t = c.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            while (c.moveToNext()) {
                seen.add((c.getString(n) ?: "") to (c.getString(t) ?: ""))
            }
        }
        return seen.map { ContactSource(it.first, it.second) }
    }

    fun groups(): List<ContactGroup> {
        val out = ArrayList<ContactGroup>()
        resolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(
                ContactsContract.Groups._ID,
                ContactsContract.Groups.TITLE,
                ContactsContract.Groups.ACCOUNT_NAME,
            ),
            null, null, null,
        )?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.Groups._ID)
            val titleIdx = c.getColumnIndex(ContactsContract.Groups.TITLE)
            val accIdx = c.getColumnIndex(ContactsContract.Groups.ACCOUNT_NAME)
            while (c.moveToNext()) {
                out.add(
                    ContactGroup(
                        id = c.getLong(idIdx).toString(),
                        title = if (titleIdx >= 0) c.getString(titleIdx) ?: "" else "",
                        accountName = if (accIdx >= 0) c.getString(accIdx) ?: "" else "",
                    ),
                )
            }
        }
        return out
    }

    /** Delete the aggregated contacts (all their raw contacts). Returns the count removed. */
    fun deleteContacts(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        val placeholders = ids.joinToString(",") { "?" }
        return resolver.delete(
            ContactsContract.RawContacts.CONTENT_URI,
            "${ContactsContract.RawContacts.CONTACT_ID} IN ($placeholders)",
            ids.toTypedArray(),
        )
    }

    // ---- internals ----

    private fun loadData(
        ids: List<Long>,
        phones: HashMap<Long, MutableList<DPhone>>,
        emails: HashMap<Long, MutableList<DEmail>>,
    ) {
        val placeholders = ids.joinToString(",") { "?" }
        val selection = "${ContactsContract.Data.CONTACT_ID} IN ($placeholders) AND " +
            "${ContactsContract.Data.MIMETYPE} IN (?, ?)"
        val args = ids.map { it.toString() }.toTypedArray() +
            arrayOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE)
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
            ),
            selection, args, null,
        )?.use { c ->
            val cid = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            val mime = c.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val d1 = c.getColumnIndex(ContactsContract.Data.DATA1)
            val d2 = c.getColumnIndex(ContactsContract.Data.DATA2)
            while (c.moveToNext()) {
                val contactId = c.getLong(cid)
                val value = c.getString(d1) ?: continue
                when (c.getString(mime)) {
                    Phone.CONTENT_ITEM_TYPE -> phones.getOrPut(contactId) { mutableListOf() }
                        .add(DPhone(value, PhoneNumbers.normalize(value), phoneType(c.getInt(d2))))
                    Email.CONTENT_ITEM_TYPE -> emails.getOrPut(contactId) { mutableListOf() }
                        .add(DEmail(value, emailType(c.getInt(d2))))
                }
            }
        }
    }

    private fun phoneType(type: Int): String = when (type) {
        Phone.TYPE_MOBILE -> "mobile"
        Phone.TYPE_HOME -> "home"
        Phone.TYPE_WORK -> "work"
        Phone.TYPE_MAIN -> "main"
        Phone.TYPE_FAX_WORK, Phone.TYPE_FAX_HOME -> "fax"
        else -> "other"
    }

    private fun emailType(type: Int): String = when (type) {
        Email.TYPE_HOME -> "home"
        Email.TYPE_WORK -> "work"
        Email.TYPE_MOBILE -> "mobile"
        else -> "other"
    }

    private fun pagedQuery(
        uri: Uri,
        projection: Array<String>,
        sortColumn: String,
        limit: Int,
        offset: Int,
    ): Cursor? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bundle = Bundle().apply {
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortColumn))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        resolver.query(uri, projection, bundle, null)
    } else {
        @Suppress("DEPRECATION")
        resolver.query(uri, projection, null, null, "$sortColumn ASC LIMIT $limit OFFSET $offset")
    }
}
