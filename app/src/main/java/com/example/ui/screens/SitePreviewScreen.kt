package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.billing.GooglePayAvailability
import com.example.data.model.HostingPlanSpec
import com.example.data.model.HostingPlans
import com.example.data.model.SiteProject
import com.example.ui.components.GooglePayCheckoutButton
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.viewmodel.CheckoutState
import com.example.ui.viewmodel.GenerationState

private enum class PreviewMode {
    LIVE_RENDER,
    HTML_SOURCE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePreviewScreen(
    site: SiteProject,
    activePlan: HostingPlanSpec,
    generationState: GenerationState,
    checkoutState: CheckoutState,
    googlePayAvailability: GooglePayAvailability,
    googlePayLauncher: ActivityResultLauncher<IntentSenderRequest>,
    onBack: () -> Unit,
    onRefineSite: (SiteProject, String) -> Unit,
    onDeployFree: (SiteProject, String, String) -> Unit,
    onDeployWithGooglePay: (SiteProject, HostingPlanSpec, String, ActivityResultLauncher<IntentSenderRequest>) -> Unit,
    onDismissCheckoutMessage: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var previewMode by remember { mutableStateOf(PreviewMode.LIVE_RENDER) }
    var desktopViewport by remember { mutableStateOf(false) }
    var refineInput by rememberSaveable { mutableStateOf("") }
    var showDeploySheet by remember { mutableStateOf(false) }
    var copiedCode by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val isRefining = generationState is GenerationState.Refining

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("preview_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to Studio",
                            )
                        }
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = site.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = site.deployedUrl.ifBlank { "${site.subdomain}.fluxsites.app (Draft)" },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (site.deployStatus == "deployed") EmeraldLive else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Button(
                        onClick = { showDeploySheet = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("open_deploy_sheet_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RocketLaunch,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deploy & Pay", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = refineInput,
                            onValueChange = { refineInput = it },
                            placeholder = { Text("Refine with Flux AI (e.g., add pricing FAQ)") },
                            singleLine = true,
                            enabled = !isRefining,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("refine_prompt_input"),
                        )
                        Button(
                            onClick = {
                                onRefineSite(site, refineInput)
                                refineInput = ""
                            },
                            enabled = !isRefining && refineInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("refine_site_button"),
                        ) {
                            if (isRefining) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Refine site with Flux AI",
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = previewMode == PreviewMode.LIVE_RENDER,
                        onClick = { previewMode = PreviewMode.LIVE_RENDER },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    ) {
                        Text("Live Site")
                    }
                    SegmentedButton(
                        selected = previewMode == PreviewMode.HTML_SOURCE,
                        onClick = { previewMode = PreviewMode.HTML_SOURCE },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    ) {
                        Text("HTML/CSS")
                    }
                }

                if (previewMode == PreviewMode.LIVE_RENDER) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { desktopViewport = false },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneAndroid,
                                contentDescription = "Mobile viewport",
                                tint = if (!desktopViewport) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(
                            onClick = { desktopViewport = true },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DesktopWindows,
                                contentDescription = "Desktop viewport",
                                tint = if (desktopViewport) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(site.htmlContent))
                            copiedCode = true
                        },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copiedCode) "Copied!" else "Copy HTML")
                    }
                }
            }

            AnimatedVisibility(visible = checkoutState is CheckoutState.Success) {
                val success = checkoutState as? CheckoutState.Success
                if (success != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = EmeraldLive.copy(alpha = 0.16f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDismissCheckoutMessage),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldLive,
                            )
                            Text(
                                text = success.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = EmeraldLive,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (previewMode == PreviewMode.LIVE_RENDER) {
                    SiteWebView(
                        htmlContent = site.htmlContent,
                        desktopMode = desktopViewport,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(14.dp),
                    ) {
                        Text(
                            text = site.htmlContent,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    if (showDeploySheet) {
        DeployAndPayBottomSheet(
            site = site,
            activePlan = activePlan,
            checkoutState = checkoutState,
            googlePayAvailability = googlePayAvailability,
            onDismiss = { showDeploySheet = false },
            onDeployFree = { tier, domain ->
                onDeployFree(site, tier, domain)
            },
            onDeployWithGooglePay = { plan, domain ->
                onDeployWithGooglePay(site, plan, domain, googlePayLauncher)
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SiteWebView(
    htmlContent: String,
    desktopMode: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = desktopMode
                settings.loadWithOverviewMode = desktopMode
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://fluxsites.app/", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.settings.useWideViewPort = desktopMode
            webView.settings.loadWithOverviewMode = desktopMode
            val prevTag = webView.tag as? String
            if (prevTag != htmlContent) {
                webView.tag = htmlContent
                webView.loadDataWithBaseURL("https://fluxsites.app/", htmlContent, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeployAndPayBottomSheet(
    site: SiteProject,
    activePlan: HostingPlanSpec,
    checkoutState: CheckoutState,
    googlePayAvailability: GooglePayAvailability,
    onDismiss: () -> Unit,
    onDeployFree: (tierKey: String, customDomain: String) -> Unit,
    onDeployWithGooglePay: (plan: HostingPlanSpec, customDomain: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPlan by remember {
        mutableStateOf(if (activePlan.isPaid) activePlan else HostingPlans.pro)
    }
    var customDomain by rememberSaveable { mutableStateOf(site.customDomain) }
    val isProcessing = checkoutState is CheckoutState.Processing

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Deploy & Hosting Checkout",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "Publish \"${site.title}\" to Firebase-backed edge hosting. Choose Starter free staging or unlock custom domains & CDN with Google Pay.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HostingPlans.all.forEach { plan ->
                val isSelected = selectedPlan.planId == plan.planId
                val alreadySubscribed = activePlan.planId == plan.planId && plan.isPaid
                Card(
                    onClick = { selectedPlan = plan },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(14.dp),
                        )
                        .testTag("deploy_plan_option_${plan.planId}"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = plan.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (alreadySubscribed) {
                                    Surface(
                                        color = EmeraldLive.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldLive,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = plan.tagline,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (plan.isPaid) "$${plan.priceUsdFormatted}/mo" else "Free",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (plan.isPaid) MaterialTheme.colorScheme.tertiary else EmeraldLive,
                        )
                    }
                }
            }

            if (selectedPlan.isPaid) {
                OutlinedTextField(
                    value = customDomain,
                    onValueChange = { customDomain = it },
                    label = { Text("Custom Domain (optional)") },
                    placeholder = { Text("e.g., ${site.subdomain}.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_domain_input"),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                val statusText = when (googlePayAvailability) {
                    is GooglePayAvailability.Checking -> "Checking Google Pay API..."
                    is GooglePayAvailability.Ready -> "Google Pay Ready (Test Gateway Environment)"
                    is GooglePayAvailability.Unavailable -> "Google Pay API: ${googlePayAvailability.reason}"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (checkoutState is CheckoutState.Error) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = checkoutState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (checkoutState is CheckoutState.Success) {
                Surface(
                    color = EmeraldLive.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = checkoutState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EmeraldLive,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            val canDeployActivePaidTier = !selectedPlan.isPaid || activePlan.planId == selectedPlan.planId ||
                (activePlan.planId == HostingPlans.enterprise.planId && selectedPlan.planId == HostingPlans.pro.planId)

            if (canDeployActivePaidTier) {
                Button(
                    onClick = {
                        onDeployFree(
                            selectedPlan.tierKey,
                            if (selectedPlan.isPaid) customDomain else "",
                        )
                    },
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("deploy_free_button"),
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.RocketLaunch,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedPlan.isPaid) {
                                "Deploy on Active ${activePlan.name}"
                            } else {
                                "Deploy Free to *.fluxsites.app"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else {
                GooglePayCheckoutButton(
                    label = "Deploy & Subscribe",
                    priceUsdFormatted = selectedPlan.priceUsdFormatted,
                    isLoading = isProcessing,
                    enabled = googlePayAvailability !is GooglePayAvailability.Checking,
                    testTag = "google_pay_deploy_button",
                    onClick = {
                        onDeployWithGooglePay(selectedPlan, customDomain)
                    },
                )
            }
        }
    }
}
