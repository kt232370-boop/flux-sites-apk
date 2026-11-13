package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.FluxAiGenerator
import com.example.data.billing.GooglePayManager
import com.example.data.firebase.configureAppCheck
import com.example.data.model.HostingPlans
import com.example.data.model.SiteProject
import com.example.data.repository.FluxSitesRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.screens.BillingAndHostingScreen
import com.example.ui.screens.BuilderScreen
import com.example.ui.screens.DeploymentsScreen
import com.example.ui.screens.SitePreviewScreen
import com.example.ui.theme.FluxSitesTheme
import com.example.ui.viewmodel.FluxStudioViewModel
import com.example.ui.viewmodel.GenerationState
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure App Check with launch intent before Compose UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            FluxSitesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FluxSitesAppRoot()
                }
            }
        }
    }
}

private enum class StudioTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    BUILDER("builder", "Studio", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    DEPLOYMENTS("deployments", "Deploys", Icons.Rounded.CloudDone, Icons.Outlined.CloudQueue),
    BILLING("billing", "Pay & Host", Icons.Rounded.CreditCard, Icons.Outlined.CreditCard),
}

@Composable
fun FluxSitesAppRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            FluxStudioShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: user.email ?: "Creator",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FluxStudioShell(
    currentUserId: String,
    userDisplayName: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: FluxStudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FluxStudioViewModel(
                    repository = FluxSitesRepository(db),
                    aiGenerator = FluxAiGenerator(),
                    googlePayManager = GooglePayManager(app),
                    currentUserId = currentUserId,
                )
            }
        },
    )

    val sitesState by viewModel.sitesState.collectAsStateWithLifecycle()
    val deploymentsState by viewModel.deploymentsState.collectAsStateWithLifecycle()
    val subscriptionsState by viewModel.subscriptionsState.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val checkoutState by viewModel.checkoutState.collectAsStateWithLifecycle()
    val googlePayAvailability by viewModel.googlePayAvailability.collectAsStateWithLifecycle()

    val googlePayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        viewModel.handleGooglePayActivityResult(activityResult.resultCode, activityResult.data)
    }

    var selectedTab by rememberSaveable { mutableStateOf(StudioTab.BUILDER) }
    var activeSiteId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(generationState) {
        val created = generationState as? GenerationState.Created
        if (created != null) {
            activeSiteId = created.siteId
            viewModel.resetGenerationState()
        }
    }

    val activePlan = remember(subscriptionsState) {
        val subs = (subscriptionsState as? UiState.Success)?.data.orEmpty()
        viewModel.activePlanFor(subs)
    }

    val selectedSite: SiteProject? = remember(activeSiteId, sitesState) {
        val id = activeSiteId ?: return@remember null
        (sitesState as? UiState.Success)?.data?.firstOrNull { it.id == id }
    }

    if (selectedSite != null) {
        SitePreviewScreen(
            site = selectedSite,
            activePlan = activePlan,
            generationState = generationState,
            checkoutState = checkoutState,
            googlePayAvailability = googlePayAvailability,
            googlePayLauncher = googlePayLauncher,
            onBack = {
                activeSiteId = null
                viewModel.resetCheckoutState()
            },
            onRefineSite = viewModel::refineSite,
            onDeployFree = viewModel::deploySiteWithoutCheckout,
            onDeployWithGooglePay = { site, plan, customDomain, launcher ->
                viewModel.startGooglePayCheckout(plan, site, customDomain, launcher)
            },
            onDismissCheckoutMessage = viewModel::resetCheckoutState,
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = "Flux Sites",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(6.dp).size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Flux Sites",
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    },
                    actions = {
                        Surface(
                            color = if (activePlan.isPaid) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text(
                                text = if (activePlan.planId == HostingPlans.enterprise.planId) {
                                    "ENTERPRISE"
                                } else if (activePlan.isPaid) {
                                    "PRO"
                                } else {
                                    "STARTER"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activePlan.isPaid) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                signOutUser(credentialManager, scope)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign out $userDisplayName",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}"),
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (selectedTab) {
                        StudioTab.BUILDER -> BuilderScreen(
                            sitesState = sitesState,
                            generationState = generationState,
                            activePlan = activePlan,
                            onGenerateSite = viewModel::generateSite,
                            onOpenSite = { site -> activeSiteId = site.id },
                            onDeleteSite = viewModel::deleteSite,
                            onUpgradeClick = { selectedTab = StudioTab.BILLING },
                        )

                        StudioTab.DEPLOYMENTS -> DeploymentsScreen(
                            sitesState = sitesState,
                            deploymentsState = deploymentsState,
                            onOpenSite = { site -> activeSiteId = site.id },
                            onRollbackDeployment = viewModel::rollbackDeployment,
                        )

                        StudioTab.BILLING -> BillingAndHostingScreen(
                            subscriptionsState = subscriptionsState,
                            activePlan = activePlan,
                            checkoutState = checkoutState,
                            googlePayAvailability = googlePayAvailability,
                            googlePayLauncher = googlePayLauncher,
                            onSubscribeWithGooglePay = { plan, launcher ->
                                viewModel.startGooglePayCheckout(plan, null, "", launcher)
                            },
                            onCancelSubscription = viewModel::cancelSubscription,
                            onRefreshGooglePay = viewModel::refreshGooglePayStatus,
                        )
                    }
                }
            }
        }
    }
}
