package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class SiteProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val prompt: String = "",
    val category: String = "landing",
    val htmlContent: String = "",
    val summary: String = "",
    val subdomain: String = "",
    val hostingTier: String = "free",
    val deployStatus: String = "draft",
    val deployedUrl: String = "",
    val customDomain: String = "",
    val sslEnabled: Boolean = true,
    val deployCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DeploymentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val siteId: String = "",
    val siteTitle: String = "",
    val deployedUrl: String = "",
    val hostingTier: String = "free",
    val paymentOrderId: String = "",
    val amountUsdCents: Long = 0L,
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class HostingSubscription(
    @DocumentId val id: String = "",
    val userId: String = "",
    val planId: String = "starter_free",
    val planName: String = "Starter Edge",
    val priceUsdCents: Long = 0L,
    val currencyCode: String = "USD",
    val paymentNetwork: String = "Free Tier",
    val paymentTokenSummary: String = "No card required",
    val status: String = "active",
    val customDomainsLimit: Long = 0L,
    val bandwidthGb: Long = 10L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class HostingPlanSpec(
    val planId: String,
    val tierKey: String,
    val name: String,
    val priceUsdFormatted: String,
    val priceUsdCents: Long,
    val bandwidthGb: Long,
    val customDomainsLimit: Long,
    val tagline: String,
    val features: List<String>,
    val isPaid: Boolean,
)

object HostingPlans {
    val starter = HostingPlanSpec(
        planId = "starter_free",
        tierKey = "free",
        name = "Starter Edge",
        priceUsdFormatted = "0.00",
        priceUsdCents = 0L,
        bandwidthGb = 10L,
        customDomainsLimit = 0L,
        tagline = "Instant AI previews & fluxsites.app staging",
        features = listOf(
            "10 GB Global Edge Bandwidth",
            "Automatic *.fluxsites.app SSL",
            "Unlimited AI Drafts & Live Preview"
        ),
        isPaid = false
    )

    val pro = HostingPlanSpec(
        planId = "pro_monthly",
        tierKey = "pro",
        name = "Flux Pro Hosting",
        priceUsdFormatted = "9.99",
        priceUsdCents = 999L,
        bandwidthGb = 500L,
        customDomainsLimit = 5L,
        tagline = "Custom domains, priority edge CDN & zero watermarks",
        features = listOf(
            "500 GB High-Speed Edge CDN",
            "Up to 5 Custom Domains + Auto TLS",
            "One-Tap Production Deploys",
            "Priority Gemini 3.8 Flash Generation"
        ),
        isPaid = true
    )

    val enterprise = HostingPlanSpec(
        planId = "enterprise_monthly",
        tierKey = "enterprise",
        name = "Enterprise Edge",
        priceUsdFormatted = "29.99",
        priceUsdCents = 2999L,
        bandwidthGb = 2500L,
        customDomainsLimit = 25L,
        tagline = "Multi-region edge clusters, 99.99% SLA & DDoS shield",
        features = listOf(
            "2.5 TB Multi-Region Global CDN",
            "25 Custom Domains + Wildcard SSL",
            "Dedicated Edge Workers & Instant Rollback",
            "Enterprise Billing & Team Audit Trail"
        ),
        isPaid = true
    )

    val all = listOf(starter, pro, enterprise)

    fun byTierKey(tierKey: String): HostingPlanSpec =
        all.firstOrNull { it.tierKey == tierKey } ?: starter
}

enum class SiteCategory(val key: String, val label: String) {
    LANDING("landing", "Landing"),
    SAAS("saas", "SaaS App"),
    PORTFOLIO("portfolio", "Portfolio"),
    STORE("store", "Storefront"),
    BLOG("blog", "Editorial");
}
