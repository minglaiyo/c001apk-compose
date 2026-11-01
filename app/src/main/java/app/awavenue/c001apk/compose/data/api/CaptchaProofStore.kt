package app.awavenue.c001apk.compose.data.api

import android.content.Context

/**
 * Holds the captcha verification proof for the validate-cookie
 * protocol, mirroring the Flutter app's apiValidationV1 persistence:
 * a proof that a retried request accepted is kept for 24 hours and
 * automatically attached to later API requests (see CaptchaCookieJar
 * in ApiClient). A proof that a retried request rejects is cleared
 * immediately by the detail screen's re-challenge check.
 *
 * The skeleton's device code is generated fresh per process and the
 * session cookie jar is in-memory, so no stable cross-restart session
 * binding is available yet: a persisted proof is reused while it is
 * younger than 24 hours, and the server rejecting it is what clears
 * it. That matches Flutter's invalidation rule; Flutter's extra
 * session-tuple binding can be added once device identity is
 * persisted.
 *
 * Must be initialized once from MainActivity before any API use.
 * Proofs must never be printed or logged.
 */
object CaptchaProofStore {

    private const val PREFS = "captcha_validation"
    private const val KEY_PROOF = "proof"
    private const val KEY_SAVED_AT = "savedAtMillis"
    private const val VALID_MILLIS = 24L * 60 * 60 * 1000

    private var appContext: Context? = null
    private var memoryProof: String? = null
    private var memorySavedAt: Long = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        memoryProof = prefs?.getString(KEY_PROOF, null)
        memorySavedAt = prefs?.getLong(KEY_SAVED_AT, 0L) ?: 0L
        if (memoryProof != null && !isFresh(memorySavedAt)) {
            clear()
        }
    }

    /** Persists a proof accepted by a retried request. */
    fun save(proof: String) {
        memoryProof = proof
        memorySavedAt = System.currentTimeMillis()
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_PROOF, proof)
            ?.putLong(KEY_SAVED_AT, memorySavedAt)
            ?.apply()
    }

    /** The stored proof while it is younger than 24 hours, else null. */
    fun validProof(): String? {
        val proof = memoryProof ?: return null
        if (!isFresh(memorySavedAt)) {
            clear()
            return null
        }
        return proof
    }

    /** Drops the stored proof (server rejected it, or user restarts). */
    fun clear() {
        memoryProof = null
        memorySavedAt = 0L
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(KEY_PROOF)
            ?.remove(KEY_SAVED_AT)
            ?.apply()
    }

    private fun isFresh(savedAt: Long): Boolean {
        if (savedAt <= 0L) return false
        return System.currentTimeMillis() - savedAt < VALID_MILLIS
    }
}
