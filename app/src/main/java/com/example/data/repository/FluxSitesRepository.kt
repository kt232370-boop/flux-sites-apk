package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.DeploymentRecord
import com.example.data.model.HostingPlanSpec
import com.example.data.model.HostingSubscription
import com.example.data.model.SiteProject
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FluxSitesRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeSites(userId: String): Flow<List<SiteProject>> = flow {
        val path = "users/$userId/sites"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("sites")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SiteProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeDeployments(userId: String): Flow<List<DeploymentRecord>> = flow {
        val path = "users/$userId/deployments"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("deployments")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DeploymentRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSubscriptions(userId: String): Flow<List<HostingSubscription>> = flow {
        val path = "users/$userId/subscriptions"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("subscriptions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        HostingSubscription::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createSite(
        title: String,
        prompt: String,
        category: String,
        htmlContent: String,
        summary: String,
        subdomain: String,
        hostingTier: String = "free",
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("sites").document()
        val normalizedSubdomain = sanitizeSubdomain(subdomain)
        val normalizedCategory = category.lowercase().takeIf {
            it in setOf("landing", "portfolio", "saas", "store", "blog")
        } ?: "landing"
        val normalizedTier = hostingTier.lowercase().takeIf {
            it in setOf("free", "pro", "enterprise")
        } ?: "free"

        val payload = mapOf(
            "userId" to uid,
            "title" to title.trim().ifBlank { "Untitled Flux Site" }.take(100),
            "prompt" to prompt.trim().ifBlank { "AI generated website" }.take(1500),
            "category" to normalizedCategory,
            "htmlContent" to htmlContent.take(150000),
            "summary" to summary.trim().take(500),
            "subdomain" to normalizedSubdomain,
            "hostingTier" to normalizedTier,
            "deployStatus" to "draft",
            "deployedUrl" to "",
            "customDomain" to "",
            "sslEnabled" to true,
            "deployCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSiteContent(
        siteId: String,
        title: String,
        prompt: String,
        htmlContent: String,
        summary: String,
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("sites").document(siteId)
        val updates = mapOf(
            "title" to title.trim().ifBlank { "Untitled Flux Site" }.take(100),
            "prompt" to prompt.trim().ifBlank { "AI refined website" }.take(1500),
            "htmlContent" to htmlContent.take(150000),
            "summary" to summary.trim().take(500),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deploySite(
        site: SiteProject,
        hostingTier: String,
        customDomain: String,
        paymentOrderId: String,
        amountUsdCents: Long,
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val siteRef = db.collection("users").document(uid).collection("sites").document(site.id)
        val depRef = db.collection("users").document(uid).collection("deployments").document()

        val normalizedDomain = customDomain.trim().lowercase().take(120)
        val endpointUrl = if (normalizedDomain.isNotBlank() && hostingTier != "free") {
            "https://$normalizedDomain"
        } else {
            "https://${sanitizeSubdomain(site.subdomain)}.fluxsites.app"
        }

        val siteUpdates = mapOf(
            "hostingTier" to hostingTier,
            "deployStatus" to "deployed",
            "deployedUrl" to endpointUrl,
            "customDomain" to normalizedDomain,
            "sslEnabled" to true,
            "deployCount" to FieldValue.increment(1L),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        val depPayload = mapOf(
            "userId" to uid,
            "siteId" to site.id,
            "siteTitle" to site.title.take(100),
            "deployedUrl" to endpointUrl,
            "hostingTier" to hostingTier,
            "paymentOrderId" to paymentOrderId.take(128),
            "amountUsdCents" to amountUsdCents.coerceAtLeast(0L),
            "status" to "active",
            "createdAt" to FieldValue.serverTimestamp(),
        )

        return try {
            val batch = db.batch()
            batch.update(siteRef, siteUpdates)
            batch.set(depRef, depPayload)
            batch.commit().await()
            Result.success(endpointUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, siteRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSite(siteId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("sites").document(siteId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun activateSubscription(
        plan: HostingPlanSpec,
        paymentNetwork: String,
        paymentTokenSummary: String,
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val subRef = db.collection("users").document(uid).collection("subscriptions").document()
        val payload = mapOf(
            "userId" to uid,
            "planId" to plan.planId,
            "planName" to plan.name.take(80),
            "priceUsdCents" to plan.priceUsdCents,
            "currencyCode" to "USD",
            "paymentNetwork" to paymentNetwork.take(80),
            "paymentTokenSummary" to paymentTokenSummary.take(160),
            "status" to "active",
            "customDomainsLimit" to plan.customDomainsLimit,
            "bandwidthGb" to plan.bandwidthGb,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            subRef.set(payload).await()
            Result.success(subRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, subRef.path)
            Result.failure(e)
        }
    }

    suspend fun cancelSubscription(subscriptionId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val subRef = db.collection("users").document(uid).collection("subscriptions").document(subscriptionId)
        return try {
            subRef.update(
                mapOf(
                    "status" to "cancelled",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, subRef.path)
            Result.failure(e)
        }
    }

    suspend fun rollbackDeployment(deploymentId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val depRef = db.collection("users").document(uid).collection("deployments").document(deploymentId)
        return try {
            depRef.update(mapOf("status" to "rolled_back")).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, depRef.path)
            Result.failure(e)
        }
    }

    companion object {
        fun sanitizeSubdomain(raw: String): String {
            val cleaned = raw.lowercase()
                .replace(Regex("[^a-z0-9-]+"), "-")
                .replace(Regex("-{2,}"), "-")
                .trim('-')
            return when {
                cleaned.length >= 3 -> cleaned.take(64)
                cleaned.isNotEmpty() -> "${cleaned}-site".take(64)
                else -> "flux-${System.currentTimeMillis().toString().takeLast(6)}"
            }
        }
    }
}
