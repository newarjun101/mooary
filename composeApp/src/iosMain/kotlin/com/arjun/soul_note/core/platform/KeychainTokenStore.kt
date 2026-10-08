package com.arjun.soul_note.core.platform

import com.arjun.soul_note.core.logging.AppLogger
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.posix.memcpy
import platform.CoreFoundation.kCFBooleanTrue
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS token store backed by the Keychain.
 *
 * `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` is the deliberate choice: the token is
 * readable after the first unlock, so a background sync can use it, but it is excluded from
 * backups and from migration to another device — a credential should not travel to hardware
 * the user has never unlocked.
 *
 * The query is assembled as a CoreFoundation dictionary by hand because Kotlin/Native
 * exposes no `Map.toCValue()` bridge for the Objective-C class methods.
 *
 * Every call logs and returns null rather than throwing: an unavailable Keychain means
 * "signed out", which the app handles, while a crash on launch means the app cannot be used
 * at all.
 */
class KeychainTokenStore(
    private val logger: AppLogger,
) : SecureTokenStore {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun read(): String? = withContext(Dispatchers.Default) {
        keychainQuery {
            CFDictionaryAddValue(it, kSecReturnData.key(), kCFBooleanTrue.value())
            CFDictionaryAddValue(it, kSecMatchLimit.key(), kSecMatchLimitOne.key())
        }.releasing { query ->
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                val item = result.value

                when {
                    status != SUCCESS -> {
                        // errSecItemNotFound is the ordinary "never signed in" case.
                        if (status != ITEM_NOT_FOUND) {
                            logger.warn(TAG) { "keychain read failed with status $status" }
                        }
                        null
                    }

                    item == null -> null

                    else -> try {
                        decode(item)
                    } finally {
                        CFRelease(item)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun write(token: String) {
        val bytes = token.encodeToByteArray().toUByteArray()
        val data = bytes.usePinned { pinned ->
            CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0), bytes.size.toLong())
        } ?: return

        try {
            keychainQuery {
                CFDictionaryAddValue(it, kSecValueData.key(), data)
                CFDictionaryAddValue(
                    it,
                    kSecAttrAccessible.key(),
                    kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly.key(),
                )
            }.releasing { query ->
                // Delete first: SecItemAdd fails with errSecDuplicateItem if the item already
                // exists, and a write that silently does nothing is worse than a replace.
                // Released after the call, not before: handing SecItemDelete an already
                // freed query is a use-after-free, and the symptom is a crash on an ordinary
                // token refresh rather than anything that points back here.
                identityQuery().releasing { identity ->
                    SecItemDelete(identity)
                }
                val status = SecItemAdd(query, null)
                if (status != SUCCESS) {
                    logger.error(TAG) { "keychain write failed with status $status" }
                }
            }
        } finally {
            CFRelease(data)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun clear() {
        identityQuery().releasing { query ->
            val status = SecItemDelete(query)
            if (status != SUCCESS && status != ITEM_NOT_FOUND) {
                logger.warn(TAG) { "keychain delete failed with status $status" }
            }
        }
    }

    /** Builds the item-identifying attributes, then lets [build] add the operation's extras. */
    @OptIn(ExperimentalForeignApi::class)
    private inline fun keychainQuery(
        build: (CFMutableDictionaryRef) -> Unit,
    ): CPointer<cnames.structs.__CFDictionary> {
        val dictionary = requireNotNull(
            CFDictionaryCreateMutable(kCFAllocatorDefault, 0L, null, null),
        ) { "Could not create a CoreFoundation dictionary" }
        build(dictionary)
        return dictionary
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun identityQuery(): CPointer<cnames.structs.__CFDictionary> = keychainQuery { dictionary ->
        CFDictionaryAddValue(dictionary, kSecClass.key(), kSecClassGenericPassword.key())
        dictionary.setStringAttribute(kSecAttrService.key(), SERVICE)
        dictionary.setStringAttribute(kSecAttrAccount.key(), ACCOUNT)
    }

    /**
     * Adds a string attribute, releasing the temporary CFString once the dictionary holds it.
     *
     * A mutable CoreFoundation dictionary retains what is added to it and releases it when it
     * is deallocated, so keeping the +1 from [cfString] would leak one string per query.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun CFMutableDictionaryRef.setStringAttribute(
        key: CValuesRef<*>,
        value: String,
    ) {
        val string = value.cfString()
        try {
            CFDictionaryAddValue(this, key, string)
        } finally {
            CFRelease(string)
        }
    }

    /** Releases the dictionary once the Keychain call has copied it. */
    @OptIn(ExperimentalForeignApi::class)
    private inline fun <R> CPointer<cnames.structs.__CFDictionary>.releasing(
        block: (CPointer<cnames.structs.__CFDictionary>) -> R,
    ): R = try {
        block(this)
    } finally {
        CFRelease(this)
    }

    /** Copies the stored bytes out as a UTF-8 string. */
    @OptIn(ExperimentalForeignApi::class)
    private fun decode(item: CPointer<out CPointed>): String? {
        val data: CFDataRef? = item.reinterpret()
        val length = CFDataGetLength(data)
        if (length <= 0L) return null
        val source = CFDataGetBytePtr(data) ?: return null
        val out = ByteArray(length.toInt())
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), source, out.size.convert())
        }
        return out.decodeToString()
    }

    /** The Keychain's own string constants are nullable bindings; a missing one is a bug. */
    @OptIn(ExperimentalForeignApi::class)
    private fun CPointer<cnames.structs.__CFString>?.key(): CValuesRef<*> =
        requireNotNull(this) { "A Keychain attribute constant is missing" }

    @OptIn(ExperimentalForeignApi::class)
    private fun CPointer<cnames.structs.__CFBoolean>?.value(): CValuesRef<*> =
        requireNotNull(this) { "kCFBooleanTrue is missing" }

    /**
     * CFStringRef for a Kotlin constant, e.g. the service name.
     *
     * The binding takes a `String?` for the `const char *` parameter and copies it, so
     * there is no pinning or NUL termination to get wrong here.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun String.cfString(): CPointer<cnames.structs.__CFString> =
        requireNotNull(
            CFStringCreateWithCString(kCFAllocatorDefault, this, kCFStringEncodingUTF8),
        ) { "Could not create a CFString" }

    private companion object {
        const val SERVICE = "com.arjun.soul_note.session"
        const val ACCOUNT = "firebase-id-token"
        const val TAG = "KeychainTokenStore"
        const val SUCCESS = 0
        const val ITEM_NOT_FOUND = -25300
    }
}
