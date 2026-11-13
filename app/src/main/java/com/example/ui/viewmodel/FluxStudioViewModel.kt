package com.example.ui.viewmodel

import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.FluxAiGenerator
import com.example.data.billing.GooglePayAvailability
import com.example.data.billing.GooglePayManager
import com.example.data.model.DeploymentRecord
import com.example.data.model.HostingPlanSpec
import com.example.data.model.HostingPlans
import com.example.data.model.HostingSubscription
import com.example.data.model.SiteProject
import com.example.data.repository.FluxSitesRepository
import com.google.android.gms.common.api.ResolvableApiException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface GenerationState {
    data object Idle : GenerationState
    data object Generating : GenerationState
    data object Refining : GenerationState
    data class Created(val siteId: String) : GenerationState
    data class Refined(val siteId: String) : GenerationState
    data class Error(val message: String) : GenerationState
}

sealed interface CheckoutState {
    data object Idle : CheckoutState
    data object Processing : CheckoutState
    data class Success(val message: String, val deployedUrl: String? = null) : CheckoutState
    data class Error(val message: String) : CheckoutState
}

data class PendingGooglePayCheckout(
    val plan: HostingPlanSpec,
    val siteToDeploy: SiteProject? = null,
    val customDomain: String = "",
)

class FluxStudioViewModel(
    private val repository: FluxSitesRepository,
    private val aiGenerator: FluxAiGenerator,
    private val googlePayManager: GooglePayManager,
    private val currentUserId: String,
) : ViewModel() {

    val sitesState: StateFlow<UiState<List<SiteProject>>> = repository.observeSites(currentUserId)
        .map<List<SiteProject>, UiState<List<SiteProject>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing sites", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load sites"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading,
        )

    val deploymentsState: StateFlow<UiState<List<DeploymentRecord>>> = repository.observeDeployments(currentUserId)
        .map<List<DeploymentRecord>, UiState<List<DeploymentRecord>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing deployments", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load deployments"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading,
        )

    val subscriptionsState: StateFlow<UiState<List<HostingSubscription>>> = repository.observeSubscriptions(currentUserId)
        .map<List<HostingSubscription>, UiState<List<HostingSubscription>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing subscriptions", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load subscriptions"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading,
        )

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _checkoutState = MutableStateFlow<CheckoutState>(CheckoutState.Idle)
    val checkoutState: StateFlow<CheckoutState> = _checkoutState.asStateFlow()

    private val _googlePayAvailability = MutableStateFlow<GooglePayAvailability>(GooglePayAvailability.Checking)
    val googlePayAvailability: StateFlow<GooglePayAvailability> = _googlePayAvailability.asStateFlow()

    private var pendingCheckout: PendingGooglePayCheckout? = null

    init {
        refreshGooglePayStatus()
    }

    fun refreshGooglePayStatus() {
        viewModelScope.launch {
            _googlePayAvailability.value = GooglePayAvailability.Checking
            _googlePayAvailability.value = googlePayManager.checkReadiness()
        }
    }

    fun resetGenerationState() {
        _generationState.value = GenerationState.Idle
    }

    fun resetCheckoutState() {
        _checkoutState.value = CheckoutState.Idle
    }

    fun generateSite(prompt: String, category: String) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            _generationState.value = GenerationState.Error("Describe the website you want Flux AI to build.")
            return
        }
        viewModelScope.launch {
            _generationState.value = GenerationState.Generating
            try {
                val generated = aiGenerator.generateSite(trimmed, category)
                val createResult = repository.createSite(
                    title = generated.title,
                    prompt = trimmed,
                    category = category,
                    htmlContent = generated.htmlContent,
                    summary = generated.summary,
                    subdomain = generated.subdomain,
                    hostingTier = "free",
                )
                createResult.onSuccess { siteId ->
                    _generationState.value = GenerationState.Created(siteId)
                }.onFailure { error ->
                    _generationState.value = GenerationState.Error(
                        error.localizedMessage ?: "Failed to save site to Firebase."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Site generation timed out", e)
                _generationState.value = GenerationState.Error(
                    "Flux AI timed out after 60 seconds. Try a more concise prompt."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Site generation failed", e)
                _generationState.value = GenerationState.Error(
                    e.localizedMessage ?: "Flux AI failed to generate site."
                )
            }
        }
    }

    fun refineSite(site: SiteProject, instruction: String) {
        val trimmed = instruction.trim()
        if (trimmed.isEmpty()) {
            _generationState.value = GenerationState.Error("Enter an update instruction for Flux AI.")
            return
        }
        viewModelScope.launch {
            _generationState.value = GenerationState.Refining
            try {
                val refined = aiGenerator.refineSite(
                    currentTitle = site.title,
                    currentHtml = site.htmlContent,
                    instruction = trimmed,
                )
                val updateResult = repository.updateSiteContent(
                    siteId = site.id,
                    title = refined.title,
                    prompt = "${site.prompt} | Refined: $trimmed",
                    htmlContent = refined.htmlContent,
                    summary = refined.summary,
                )
                updateResult.onSuccess {
                    _generationState.value = GenerationState.Refined(site.id)
                }.onFailure { error ->
                    _generationState.value = GenerationState.Error(
                        error.localizedMessage ?: "Failed to save refined site."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Site refinement timed out", e)
                _generationState.value = GenerationState.Error(
                    "Refinement timed out after 60 seconds. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Site refinement failed", e)
                _generationState.value = GenerationState.Error(
                    e.localizedMessage ?: "Failed to refine site."
                )
            }
        }
    }

    fun deploySiteWithoutCheckout(
        site: SiteProject,
        tierKey: String,
        customDomain: String,
    ) {
        viewModelScope.launch {
            _checkoutState.value = CheckoutState.Processing
            val result = repository.deploySite(
                site = site,
                hostingTier = tierKey,
                customDomain = customDomain,
                paymentOrderId = "",
                amountUsdCents = 0L,
            )
            result.onSuccess { url ->
                _checkoutState.value = CheckoutState.Success(
                    message = "Deployed to $url",
                    deployedUrl = url,
                )
            }.onFailure { error ->
                _checkoutState.value = CheckoutState.Error(
                    error.localizedMessage ?: "Failed to deploy site."
                )
            }
        }
    }

    fun startGooglePayCheckout(
        plan: HostingPlanSpec,
        siteToDeploy: SiteProject?,
        customDomain: String,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        pendingCheckout = PendingGooglePayCheckout(
            plan = plan,
            siteToDeploy = siteToDeploy,
            customDomain = customDomain,
        )
        _checkoutState.value = CheckoutState.Processing

        val task = googlePayManager.createLoadPaymentDataTask(
            priceUsdFormatted = plan.priceUsdFormatted,
            planLabel = plan.name,
        )

        task.addOnSuccessListener { paymentData ->
            val parsed = googlePayManager.parsePaymentData(paymentData)
            parsed.onSuccess { receipt ->
                finalizePaidCheckout(receipt.orderId, receipt.paymentNetwork, receipt.tokenSummary)
            }.onFailure { err ->
                pendingCheckout = null
                _checkoutState.value = CheckoutState.Error(
                    err.localizedMessage ?: "Unable to parse Google Pay response."
                )
            }
        }.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    launcher.launch(
                        IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                    )
                } catch (sendEx: Exception) {
                    pendingCheckout = null
                    _checkoutState.value = CheckoutState.Error(
                        sendEx.localizedMessage ?: "Could not open Google Pay sheet."
                    )
                }
            } else {
                pendingCheckout = null
                Log.e(TAG, "Google Pay loadPaymentData failed", exception)
                _checkoutState.value = CheckoutState.Error(
                    exception.localizedMessage ?: "Google Pay unavailable on this device."
                )
            }
        }
    }

    fun handleGooglePayActivityResult(resultCode: Int, data: Intent?) {
        when (resultCode) {
            android.app.Activity.RESULT_OK -> {
                val parsed = googlePayManager.parsePaymentResult(data)
                parsed.onSuccess { receipt ->
                    finalizePaidCheckout(
                        orderId = receipt.orderId,
                        paymentNetwork = receipt.paymentNetwork,
                        tokenSummary = receipt.tokenSummary,
                    )
                }.onFailure { err ->
                    pendingCheckout = null
                    _checkoutState.value = CheckoutState.Error(
                        err.localizedMessage ?: "Failed to verify Google Pay payment data."
                    )
                }
            }
            android.app.Activity.RESULT_CANCELED -> {
                pendingCheckout = null
                _checkoutState.value = CheckoutState.Idle
            }
            else -> {
                val message = googlePayManager.parseErrorMessage(data)
                pendingCheckout = null
                _checkoutState.value = CheckoutState.Error(message)
            }
        }
    }

    private fun finalizePaidCheckout(
        orderId: String,
        paymentNetwork: String,
        tokenSummary: String,
    ) {
        val pending = pendingCheckout
        pendingCheckout = null
        if (pending == null) {
            _checkoutState.value = CheckoutState.Error("No active checkout session found.")
            return
        }

        viewModelScope.launch {
            _checkoutState.value = CheckoutState.Processing
            val subResult = repository.activateSubscription(
                plan = pending.plan,
                paymentNetwork = paymentNetwork,
                paymentTokenSummary = "$tokenSummary ($orderId)",
            )
            if (subResult.isFailure) {
                _checkoutState.value = CheckoutState.Error(
                    subResult.exceptionOrNull()?.localizedMessage
                        ?: "Payment succeeded but subscription activation failed."
                )
                return@launch
            }

            val site = pending.siteToDeploy
            if (site != null) {
                val deployResult = repository.deploySite(
                    site = site,
                    hostingTier = pending.plan.tierKey,
                    customDomain = pending.customDomain,
                    paymentOrderId = orderId,
                    amountUsdCents = pending.plan.priceUsdCents,
                )
                deployResult.onSuccess { url ->
                    _checkoutState.value = CheckoutState.Success(
                        message = "${pending.plan.name} active via $paymentNetwork & deployed!",
                        deployedUrl = url,
                    )
                }.onFailure { err ->
                    _checkoutState.value = CheckoutState.Error(
                        err.localizedMessage ?: "Subscription active, but deploy write failed."
                    )
                }
            } else {
                _checkoutState.value = CheckoutState.Success(
                    message = "${pending.plan.name} activated via $paymentNetwork!"
                )
            }
        }
    }

    fun cancelSubscription(subscriptionId: String) {
        viewModelScope.launch {
            repository.cancelSubscription(subscriptionId).onFailure { err ->
                _checkoutState.value = CheckoutState.Error(
                    err.localizedMessage ?: "Failed to cancel subscription."
                )
            }
        }
    }

    fun rollbackDeployment(deploymentId: String) {
        viewModelScope.launch {
            repository.rollbackDeployment(deploymentId).onFailure { err ->
                _checkoutState.value = CheckoutState.Error(
                    err.localizedMessage ?: "Failed to roll back deployment."
                )
            }
        }
    }

    fun deleteSite(siteId: String) {
        viewModelScope.launch {
            repository.deleteSite(siteId).onFailure { err ->
                _checkoutState.value = CheckoutState.Error(
                    err.localizedMessage ?: "Failed to delete site."
                )
            }
        }
    }

    fun activePlanFor(subscriptions: List<HostingSubscription>): HostingPlanSpec {
        val active = subscriptions.firstOrNull { it.status == "active" }
        return when (active?.planId) {
            HostingPlans.enterprise.planId -> HostingPlans.enterprise
            HostingPlans.pro.planId -> HostingPlans.pro
            else -> HostingPlans.starter
        }
    }

    private companion object {
        const val TAG = "FluxStudioVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
