package com.velthy.client.auth

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

private const val MUSIC_ORIGIN = "https://music.youtube.com"

private const val TAG = "Velthy"

/**
 * Google's own account chooser, asked to present itself every time.
 *
 * `prompt=select_account` is the whole difference between "sign in" and "add
 * another account". Without it, a browser that already holds a session is
 * answered with the account in that session and Google redirects straight
 * through — never drawing the chooser — so adding a second account silently
 * signs the first one back in. With no session at all, the same page *is* the
 * sign-in form.
 *
 * Deliberately not a logout first. Ending the Google session to force the form
 * is a heavier hammer than it looks: the session it ends is the one the other
 * saved accounts were captured from, and an account that stops working the
 * moment a second one is added is a worse bug than the one it would fix.
 */
private const val LOGIN_URL =
    "https://accounts.google.com/AccountChooser" +
        "?service=youtube" +
        "&prompt=select_account" +
        "&continue=https%3A%2F%2Fmusic.youtube.com%2F"

/** What the in-app browser is being opened for. */
enum class WebSessionMode {
    /** No usable session yet, or signing in as someone else: show the form. */
    SIGN_IN,

    /**
     * Already signed in, but on the wrong channel. Opens YouTube Music itself
     * so its own Accounts switcher can be used, and takes the session from
     * whatever page the listener ends up on.
     */
    SWITCH_CHANNEL,
}

/**
 * A session lifted out of the in-app browser: the cookie, plus who the page
 * being looked at says it is.
 *
 * The identity fields come from the live page's `ytcfg` rather than from a
 * later server-side fetch, and that is the whole point: which channel YouTube
 * Music serves by default is not a question this app gets to answer, but which
 * channel the page in front of the listener is *currently* showing is written
 * down in the page itself. Reading it there is what lets "switch to the channel
 * I want, then save" work.
 *
 * All identity fields are nullable: a page that will not give them up leaves
 * the app exactly where it was.
 */
data class CapturedSession(
    val cookie: String,
    /** `DELEGATED_SESSION_ID` — set only while a brand channel is selected. */
    val pageId: String?,
    /** `DATASYNC_ID`, account half only. */
    val dataSyncId: String?,
    /** `SESSION_INDEX` — which Google account in the cookie jar. */
    val authUser: String?,
    /** Whether the page reported itself signed in at all. */
    val loggedIn: Boolean,
)

/**
 * The WebView's own cookie jar, which is not the app's.
 *
 * These are separate stores and the difference is invisible until it bites:
 * signing out of Velthy forgets the cookie the app makes requests with and
 * leaves the browser's copy untouched. The next sign-in then loads
 * accounts.google.com, is recognised immediately, redirects straight through to
 * music.youtube.com and hands back a cookie for the account that was just
 * signed out of — a sign-in screen that cannot be used to sign in as anyone
 * else, and shows barely a flicker while refusing to.
 *
 * Google's jar cannot be cleared domain by domain. Expiring individual cookies
 * misses HttpOnly ones, cookies on a path other than `/`, and the `__Secure-`
 * family that rejects a value lacking its `Secure` attribute; and `setCookie`
 * is asynchronous, so a load started straight after it can still see the old
 * jar. [removeAllCookies] with its completion callback is the only removal that
 * is both complete and ordered before the next load.
 */
object BrowserSession {

    /**
     * Forgets everything the in-app browser is holding, then calls [onCleared].
     *
     * Saved YouTube Music accounts are not affected: their cookies live in the
     * app's own store, not here. Discord and Last.fm keep their tokens there
     * too, so clearing this jar only makes their next *web* sign-in fresh.
     */
    fun clearAllCookies(onCleared: () -> Unit = {}) {
        // Best effort throughout. CookieManager needs a WebView provider, and
        // on a device that has none there isn't one — which is a reason for the
        // next sign-in to be less convenient, not a reason to crash.
        val manager = runCatching { CookieManager.getInstance() }.getOrElse {
            Log.w(TAG, "no cookie manager to clear: ${it.message}")
            onCleared()
            return
        }
        manager.removeAllCookies {
            runCatching { manager.flush() }
            Log.d(TAG, "cleared the browser cookie jar")
            onCleared()
        }
    }
}

/**
 * In-app Google sign-in for YouTube Music, and the way to change which channel
 * it listens as.
 *
 * [WebSessionMode.SIGN_IN] loads Google's account chooser with
 * `continue=music.youtube.com`. The user authenticates directly against
 * accounts.google.com (2FA, passkeys etc. all work — it's the real page). When
 * Google redirects back to music.youtube.com the session is taken automatically
 * and the screen closes. The browser's cookie jar is emptied before anything
 * loads, and again when the screen closes, so a session it happens to be
 * holding cannot answer for the listener — see [BrowserSession.clearAllCookies].
 *
 * [WebSessionMode.SWITCH_CHANNEL] keeps those cookies and opens YouTube Music
 * itself, so the listener can use the avatar menu's own Accounts list — the one
 * screen that authoritatively knows which channels exist and which is which.
 * Nothing is taken automatically there: the session is read when they say so,
 * by raising [captureRequest]. The credential itself never passes through app
 * code.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtMusicLoginScreen(
    mode: WebSessionMode,
    onCaptured: (CapturedSession) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Raise to take the session from the page as it stands. Ignored at its
     * initial value, so arriving on the screen doesn't capture anything.
     */
    captureRequest: Int = 0,
    /** Told when a capture was asked for and there was no session to take. */
    onCaptureUnavailable: () -> Unit = {},
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var captureJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnUnavailable by rememberUpdatedState(onCaptureUnavailable)

    LaunchedEffect(captureRequest) {
        if (captureRequest == 0) return@LaunchedEffect
        captureJob?.cancel()
        val view = webView
        if (view == null || !captureFrom(view, currentOnCaptured)) currentOnUnavailable()
    }

    DisposableEffect(mode) {
        onDispose {
            captureJob?.cancel()
            // Leaving the sign-in screen leaves nothing behind: the next open
            // must start from an empty jar even if the listener changed their
            // mind and never completed a login.
            if (mode == WebSessionMode.SIGN_IN) BrowserSession.clearAllCookies()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                webViewClient = object : WebViewClient() {
                    private var captured = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Only [WebSessionMode.SIGN_IN] finishes by itself. In
                        // the switch flow the first music.youtube.com page is
                        // where the listener starts, not where they are done —
                        // grabbing the session there would save the channel
                        // they came to change.
                        if (mode != WebSessionMode.SIGN_IN) return
                        if (captured || view == null || !isMusicSessionUrl(url)) return
                        // Google can report the page finished a frame before
                        // the session cookie is visible to CookieManager, so
                        // one look is not enough: retry briefly, and let the
                        // manual button cover whatever remains.
                        captureJob?.cancel()
                        captureJob = scope.launch {
                            repeat(CAPTURE_RETRY_DELAYS_MS.size + 1) { attempt ->
                                if (captureFrom(view, currentOnCaptured)) {
                                    captured = true
                                    return@launch
                                }
                                val delayMs = CAPTURE_RETRY_DELAYS_MS.getOrNull(attempt)
                                if (delayMs != null) delay(delayMs)
                            }
                        }
                    }
                }

                webView = this
                if (mode == WebSessionMode.SIGN_IN) {
                    // Loaded only once the jar is actually empty: both the
                    // removal and the load are asynchronous, and the old
                    // ordering let a request start before the cookies were gone.
                    BrowserSession.clearAllCookies {
                        post { runCatching { loadUrl(LOGIN_URL) } }
                    }
                } else {
                    loadUrl("$MUSIC_ORIGIN/")
                }
            }
        },
    )
}

/** How long to keep looking for the session cookie after the redirect lands. */
private val CAPTURE_RETRY_DELAYS_MS = longArrayOf(250, 600, 1200, 2000)

/** True only for the origin Google's `continue` sends the listener back to. */
internal fun isMusicSessionUrl(url: String?): Boolean =
    url == MUSIC_ORIGIN || url?.startsWith("$MUSIC_ORIGIN/") == true

/**
 * Takes the session from [view], if it is holding one.
 *
 * @return whether there was one to take. False means the cookie jar has no
 *   signing secret in it yet — the page is mid-login, or is not a YouTube page
 *   at all — and the caller should leave the screen open rather than saving
 *   something that cannot sign a request.
 */
private fun captureFrom(view: WebView, onCaptured: (CapturedSession) -> Unit): Boolean {
    val cookies = CookieManager.getInstance().getCookie(MUSIC_ORIGIN)
    if (cookies == null || "SAPISID" !in cookies) return false
    // Flushed here rather than left to the WebView's own schedule: the screen
    // is usually closing in the next frame, and a cookie jar written after that
    // is a jar the next sign-in reads instead of this one.
    CookieManager.getInstance().flush()

    view.evaluateJavascript(YTCFG_PROBE) { raw ->
        val config = raw.parseConfig()
        if (config == null) {
            Log.w(TAG, "no ytcfg on the page; taking the cookie without an identity")
        }
        onCaptured(
            CapturedSession(
                cookie = cookies,
                pageId = config?.string("pageId"),
                // `<accountSyncId>||<sessionSyncId>` — only the first half names
                // the account; the second changes on its own schedule.
                dataSyncId = config?.string("dataSyncId")?.substringBefore("||"),
                authUser = config?.string("authUser"),
                loggedIn = config?.get("loggedIn").let { it is JsonPrimitive && it.content == "true" },
            ),
        )
    }
    return true
}

/**
 * The identity of the page as the page itself has it.
 *
 * Returns an object rather than a string so the WebView serialises it — a probe
 * that stringified its own result would come back double-encoded. A page
 * without `ytcfg` (an error page, a redirect that hasn't landed) returns null,
 * which is a fine answer and not an error.
 */
private const val YTCFG_PROBE = """
(function () {
  try {
    if (!window.ytcfg || !window.ytcfg.get) return null;
    var get = function (key) {
      var value = window.ytcfg.get(key);
      return (value === undefined || value === null || value === '') ? null : String(value);
    };
    return {
      loggedIn: String(!!window.ytcfg.get('LOGGED_IN')),
      pageId: get('DELEGATED_SESSION_ID'),
      dataSyncId: get('DATASYNC_ID'),
      authUser: get('SESSION_INDEX')
    };
  } catch (e) {
    return null;
  }
})()
"""

private val json = Json { ignoreUnknownKeys = true }

/** The probe's result, or null for anything that isn't the object it promises. */
private fun String?.parseConfig(): JsonObject? =
    this?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
