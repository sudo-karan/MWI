package com.ismartcoding.plain.web.api

import com.ismartcoding.plain.crypto.Crypto
import com.ismartcoding.plain.crypto.DecryptResult
import com.ismartcoding.plain.crypto.ReplayGuard
import com.ismartcoding.plain.web.TokenEnvelope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiPipelineTest {

    private fun bodyFor(ts: Long, nonce: String, opJson: String) = TokenEnvelope.format(ts, nonce, opJson)

    @Test
    fun validRequest_dispatches() = runTest {
        val pipeline = ApiPipeline(ReplayGuard(now = { 1000L }))
        val body = bodyFor(1000L, "n1", """{"operation":"ping"}""")
        val r = pipeline.process(body) { req -> ApiResponse(data = JsonPrimitive("pong-${req.operation}")) }
        assertTrue(r is ApiPipeline.Result.Ok)
        assertTrue((r as ApiPipeline.Result.Ok).responseJson.contains("pong-ping"))
    }

    @Test
    fun replayedNonce_rejected() = runTest {
        val pipeline = ApiPipeline(ReplayGuard(now = { 1000L }))
        val body = bodyFor(1000L, "dup", """{"operation":"x"}""")
        assertTrue(pipeline.process(body) { ApiResponse() } is ApiPipeline.Result.Ok)
        assertTrue(pipeline.process(body) { ApiResponse() } is ApiPipeline.Result.Replay)
    }

    @Test
    fun staleTimestamp_rejected() = runTest {
        val pipeline = ApiPipeline(ReplayGuard(windowMs = 30_000L, now = { 100_000L }))
        val body = bodyFor(10_000L, "old", """{"operation":"x"}""")
        assertTrue(pipeline.process(body) { ApiResponse() } is ApiPipeline.Result.Stale)
    }

    @Test
    fun malformedBody_badRequest() = runTest {
        val pipeline = ApiPipeline(ReplayGuard(now = { 1000L }))
        // No separators → rejected before the replay check.
        assertTrue(pipeline.process("garbage") { ApiResponse() } is ApiPipeline.Result.BadRequest)
        // Valid envelope (fresh timestamp+nonce) but the JSON payload isn't an ApiRequest.
        val badJson = bodyFor(1000L, "bad", "not-json")
        assertTrue(pipeline.process(badJson) { ApiResponse() } is ApiPipeline.Result.BadRequest)
    }

    @Test
    fun processEncrypted_roundTrips_andRejectsWrongToken() = runTest {
        val token = Crypto.generateKey()
        val pipeline = ApiPipeline(ReplayGuard(now = { 5000L }))
        val plain = bodyFor(5000L, "nn", """{"operation":"echo","variables":{"v":"hi"}}""")
        val ct = Crypto.encrypt(token, plain.encodeToByteArray())

        val respCt = pipeline.processEncrypted(token, ct) { req -> ApiResponse(data = req.variables) }
        assertNotNull(respCt)
        val respPlain = (Crypto.decrypt(token, respCt) as DecryptResult.Success).plaintext.decodeToString()
        assertTrue(respPlain.contains("\"v\":\"hi\""))

        // A wrong token cannot decrypt the request → null (unauthorized).
        assertNull(pipeline.processEncrypted(Crypto.generateKey(), ct) { ApiResponse() })
    }
}
