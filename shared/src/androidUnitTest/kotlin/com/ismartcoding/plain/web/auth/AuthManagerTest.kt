package com.ismartcoding.plain.web.auth

import com.ismartcoding.plain.crypto.AuthTokens
import com.ismartcoding.plain.db.DSession
import com.ismartcoding.plain.web.AuthRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

private class FakeAuthStore(private val password: String?) : AuthStore {
    val sessions = mutableListOf<DSession>()
    override fun passwordHash(): String? = password?.let { AuthTokens.passwordHash(it) }
    override fun saveSession(session: DSession) { sessions.add(session) }
    override fun sessionByToken(token: String): DSession? = sessions.firstOrNull { it.token == token }
}

class AuthManagerTest {

    private fun req(pw: String) = AuthRequest(password = pw, name = "Chrome on Mac", osName = "macOS", browserName = "Chrome")

    @Test
    fun noPasswordSet_denies() {
        val mgr = AuthManager(FakeAuthStore(null), twoFactorEnabled = { false })
        assertTrue(mgr.authenticate("cid", req("anything")) is AuthManager.Result.Denied)
    }

    @Test
    fun wrongPassword_denies() {
        val mgr = AuthManager(FakeAuthStore("correct-horse"), twoFactorEnabled = { false })
        assertTrue(mgr.authenticate("cid", req("wrong")) is AuthManager.Result.Denied)
    }

    @Test
    fun correctPassword_no2fa_grantsAndPersists() {
        val store = FakeAuthStore("correct-horse")
        val mgr = AuthManager(store, twoFactorEnabled = { false })
        val r = mgr.authenticate("cid-1", req("correct-horse"))
        assertTrue(r is AuthManager.Result.Granted)
        r as AuthManager.Result.Granted
        assertEquals(1, store.sessions.size)
        val session = mgr.sessionForToken(r.token)
        assertNotNull(session)
        assertEquals("cid-1", session.clientId)
        assertEquals(r.sessionId, session.id)
    }

    @Test
    fun correctPassword_with2fa_pendingUntilApproved() {
        val store = FakeAuthStore("correct-horse")
        val mgr = AuthManager(store, twoFactorEnabled = { true })
        val r = mgr.authenticate("cid-2", req("correct-horse"))
        assertTrue(r is AuthManager.Result.Pending)
        r as AuthManager.Result.Pending
        // Nothing persisted until the user approves on the device.
        assertEquals(0, store.sessions.size)
        assertEquals(1, mgr.pendingApprovals().size)

        val granted = mgr.approve(r.approvalId)
        assertNotNull(granted)
        assertEquals(1, store.sessions.size)
        assertNotNull(mgr.sessionForToken(granted.token))
        assertTrue(mgr.pendingApprovals().isEmpty())
        // Approving again is a no-op.
        assertNull(mgr.approve(r.approvalId))
    }

    @Test
    fun twoFactor_rejectRemovesPending() {
        val store = FakeAuthStore("pw")
        val mgr = AuthManager(store, twoFactorEnabled = { true })
        val r = mgr.authenticate("cid", req("pw")) as AuthManager.Result.Pending
        assertTrue(mgr.reject(r.approvalId))
        assertFalse(mgr.reject(r.approvalId)) // already gone
        assertEquals(0, store.sessions.size)
        assertTrue(mgr.pendingApprovals().isEmpty())
    }

    @Test
    fun statusMapping() {
        assertEquals(com.ismartcoding.plain.web.AuthStatus.DENIED, AuthManager.Result.Denied.status)
        assertEquals(com.ismartcoding.plain.web.AuthStatus.GRANTED, AuthManager.Result.Granted("t", "s").status)
        assertEquals(com.ismartcoding.plain.web.AuthStatus.PENDING, AuthManager.Result.Pending("a").status)
    }
}
