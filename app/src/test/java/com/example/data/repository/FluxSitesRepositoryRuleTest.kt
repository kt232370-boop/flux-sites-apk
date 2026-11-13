package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.HostingPlans
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluxSitesRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveSite_authenticatedOwner_succeedsAndStreams(): Unit = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FluxSitesRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createSite(
                title = "Aura AI Studio",
                prompt = "Sleek AI creative studio landing page",
                category = "saas",
                htmlContent = "<html><body><h1>Aura AI</h1></body></html>",
                summary = "Dark hero + feature cards",
                subdomain = "aura-ai-studio",
                hostingTier = "free"
            )
        }
        assertTrue(createResult.isSuccess)
        val siteId = createResult.getOrThrow()
        assertTrue(siteId.isNotBlank())

        val sites = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSites(uid).first { list -> list.any { it.id == siteId } }
        }
        val created = sites.first { it.id == siteId }
        assertEquals("Aura AI Studio", created.title)
        assertEquals("draft", created.deployStatus)
    }

    @Test
    fun deploySiteAndActivateSubscription_authenticatedOwner_succeeds(): Unit = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = FluxSitesRepository(firestore, auth)

        val siteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createSite(
                title = "Vertex Coffee",
                prompt = "Coffee roaster storefront",
                category = "store",
                htmlContent = "<html><body><h1>Vertex Coffee</h1></body></html>",
                summary = "Storefront hero",
                subdomain = "vertex-coffee"
            ).getOrThrow()
        }

        val site = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSites(uid).first { list -> list.any { it.id == siteId } }
        }.first { it.id == siteId }

        val subResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.activateSubscription(
                plan = HostingPlans.pro,
                paymentNetwork = "Google Pay • VISA 4242",
                paymentTokenSummary = "gpay_tok_test123"
            )
        }
        assertTrue(subResult.isSuccess)

        val deployResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deploySite(
                site = site,
                hostingTier = "pro",
                customDomain = "vertexcoffee.io",
                paymentOrderId = "gpay_ord_abc",
                amountUsdCents = 999L
            )
        }
        assertTrue(deployResult.isSuccess)
        assertEquals("https://vertexcoffee.io", deployResult.getOrThrow())

        val deployments = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeDeployments(uid).first { list -> list.any { it.siteId == siteId } }
        }
        assertEquals("pro", deployments.first { it.siteId == siteId }.hostingTier)
    }

    @Test
    fun observeSites_crossUserAccess_failsWithPermissionDenied(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FluxSitesRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createSite(
                title = "Private Site",
                prompt = "Test",
                category = "landing",
                htmlContent = "<html><body>Private</body></html>",
                summary = "Private",
                subdomain = "private-site"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FluxSitesRepository(firestore, auth)
        var thrown: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeSites(aliceUid).first()
            }
        } catch (e: Exception) {
            thrown = e
        }
        assertNotNull(thrown)
        val firestoreEx = thrown!!.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $thrown", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    @Test
    fun observeSites_unauthenticatedUser_failsWithPermissionDenied(): Unit = runBlocking {
        auth.signOut()
        val repo = FluxSitesRepository(firestore, auth)
        var thrown: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeSites("some_user_123").first()
            }
        } catch (e: Exception) {
            thrown = e
        }
        assertNotNull(thrown)
        val firestoreEx = thrown!!.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $thrown", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_flux@test.com"
        const val BOB_EMAIL = "bob_flux@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
