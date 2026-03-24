package com.openclaw.agent.core.tools.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import com.openclaw.agent.core.tools.Tool
import com.openclaw.agent.core.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TAG = "ContactsTool"
private const val MAX_RESULTS = 20

class ContactsTool(private val context: Context) : Tool {
    override val name = "contacts"
    override val description = "Search and view contacts. Actions: 'search' (fuzzy search by name or phone number), 'get' (get full details of a contact by ID), 'list_recent' (list contacts sorted by display name)."
    override val parameterSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add(JsonPrimitive("search")); add(JsonPrimitive("get")); add(JsonPrimitive("list_recent")) })
                put("description", "Action: search, get, or list_recent")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query (name or phone number). Required for 'search' action.")
            }
            putJsonObject("contact_id") {
                put("type", "string")
                put("description", "Contact ID. Required for 'get' action.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Max number of contacts to return for 'list_recent'. Default 10, max $MAX_RESULTS.")
            }
        }
        put("required", buildJsonArray { add(JsonPrimitive("action")) })
    }

    override suspend fun execute(args: JsonObject): ToolResult {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ToolResult(
                success = false,
                content = "",
                errorMessage = "READ_CONTACTS permission not granted. Please grant contacts permission in system settings."
            )
        }

        val action = args["action"]?.jsonPrimitive?.content ?: return ToolResult(
            success = false, content = "", errorMessage = "Missing 'action' parameter"
        )

        return when (action) {
            "search" -> {
                val query = args["query"]?.jsonPrimitive?.content ?: return ToolResult(
                    success = false, content = "", errorMessage = "Missing 'query' parameter for search action"
                )
                searchContacts(query)
            }
            "get" -> {
                val contactId = args["contact_id"]?.jsonPrimitive?.content ?: return ToolResult(
                    success = false, content = "", errorMessage = "Missing 'contact_id' parameter for get action"
                )
                getContactDetail(contactId)
            }
            "list_recent" -> {
                val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 10
                listContacts(limit.coerceIn(1, MAX_RESULTS))
            }
            else -> ToolResult(
                success = false, content = "", errorMessage = "Unknown action: $action. Use search, get, or list_recent."
            )
        }
    }

    private suspend fun searchContacts(query: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_FILTER_URI,
                Uri.encode(query)
            )
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )

            val contacts = mutableListOf<String>()
            context.contentResolver.query(
                uri, projection, null, null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                var count = 0
                while (cursor.moveToNext() && count < MAX_RESULTS) {
                    val id = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val hasPhone = cursor.getInt(hasPhoneIdx) > 0

                    val phones = if (hasPhone) queryPhones(id) else emptyList()
                    val phonesStr = if (phones.isNotEmpty()) " | ${phones.joinToString(", ")}" else ""

                    contacts.add("• $name (ID: $id)$phonesStr")
                    count++
                }
            }

            if (contacts.isEmpty()) {
                ToolResult(success = true, content = "No contacts found for \"$query\".")
            } else {
                ToolResult(
                    success = true,
                    content = "Found ${contacts.size} contact(s) for \"$query\":\n\n${contacts.joinToString("\n")}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search contacts failed", e)
            ToolResult(success = false, content = "", errorMessage = "Search contacts failed: ${e.message}")
        }
    }

    private suspend fun getContactDetail(contactId: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            // Get display name
            val displayName = queryDisplayName(contactId)
                ?: return@withContext ToolResult(
                    success = false, content = "", errorMessage = "Contact not found with ID: $contactId"
                )

            val phones = queryPhones(contactId)
            val emails = queryEmails(contactId)

            val sb = StringBuilder()
            sb.appendLine("## $displayName")
            sb.appendLine("- Contact ID: $contactId")

            if (phones.isNotEmpty()) {
                sb.appendLine("\n### Phone Numbers")
                phones.forEach { sb.appendLine("  • $it") }
            } else {
                sb.appendLine("\n### Phone Numbers\n  (none)")
            }

            if (emails.isNotEmpty()) {
                sb.appendLine("\n### Email Addresses")
                emails.forEach { sb.appendLine("  • $it") }
            } else {
                sb.appendLine("\n### Email Addresses\n  (none)")
            }

            ToolResult(success = true, content = sb.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Get contact detail failed", e)
            ToolResult(success = false, content = "", errorMessage = "Get contact detail failed: ${e.message}")
        }
    }

    private suspend fun listContacts(limit: Int): ToolResult = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )

            val contacts = mutableListOf<String>()
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val hasPhone = cursor.getInt(hasPhoneIdx) > 0

                    val phones = if (hasPhone) queryPhones(id) else emptyList()
                    val phonesStr = if (phones.isNotEmpty()) " | ${phones.joinToString(", ")}" else ""

                    contacts.add("• $name (ID: $id)$phonesStr")
                    count++
                }
            }

            if (contacts.isEmpty()) {
                ToolResult(success = true, content = "No contacts found on this device.")
            } else {
                ToolResult(
                    success = true,
                    content = "Contacts (${contacts.size}):\n\n${contacts.joinToString("\n")}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "List contacts failed", e)
            ToolResult(success = false, content = "", errorMessage = "List contacts failed: ${e.message}")
        }
    }

    private fun queryDisplayName(contactId: String): String? {
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(
                    cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                )
            }
        }
        return null
    }

    private fun queryPhones(contactId: String): List<String> {
        val phones = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIdx) ?: continue
                val typeInt = cursor.getInt(typeIdx)
                val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources, typeInt, ""
                ).toString()
                val label = if (typeLabel.isNotBlank()) " ($typeLabel)" else ""
                phones.add("$number$label")
            }
        }
        return phones
    }

    private fun queryEmails(contactId: String): List<String> {
        val emails = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE
            ),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            val addressIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)
            while (cursor.moveToNext()) {
                val address = cursor.getString(addressIdx) ?: continue
                val typeInt = cursor.getInt(typeIdx)
                val typeLabel = ContactsContract.CommonDataKinds.Email.getTypeLabel(
                    context.resources, typeInt, ""
                ).toString()
                val label = if (typeLabel.isNotBlank()) " ($typeLabel)" else ""
                emails.add("$address$label")
            }
        }
        return emails
    }
}
