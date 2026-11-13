package com.example

import com.example.data.model.HostingPlans
import com.example.data.repository.FluxSitesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluxDomainTest {

    @Test
    fun sanitizeSubdomain_producesValidLowercaseSlug() {
        assertEquals("nova-coffee-shop", FluxSitesRepository.sanitizeSubdomain("Nova Coffee Shop!!"))
        assertEquals("ai-site", FluxSitesRepository.sanitizeSubdomain("AI"))
        assertTrue(FluxSitesRepository.sanitizeSubdomain("---").startsWith("flux-"))
    }

    @Test
    fun hostingPlans_lookupMatchesTierKeys() {
        assertEquals(HostingPlans.starter, HostingPlans.byTierKey("free"))
        assertEquals(HostingPlans.pro, HostingPlans.byTierKey("pro"))
        assertEquals(HostingPlans.enterprise, HostingPlans.byTierKey("enterprise"))
        assertFalse(HostingPlans.starter.isPaid)
        assertTrue(HostingPlans.pro.isPaid)
        assertEquals("9.99", HostingPlans.pro.priceUsdFormatted)
    }
}
