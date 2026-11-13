package com.example.ui.screens

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.billing.GooglePayAvailability
import com.example.data.model.DeploymentRecord
import com.example.data.model.HostingPlanSpec
import com.example.data.model.HostingPlans
import com.example.data.model.HostingSubscription
import com.example.data.model.SiteProject
import com.example.ui.components.GooglePayCheckoutButton
import com.example.ui.theme.EmeraldLive
import com.example.ui.viewmodel.CheckoutState
import com.example.ui.viewmodel.UiState

@Composable
fun DeploymentsScreen(
    sitesState: UiState<List<SiteProject>>,
    deploymentsState: UiState<List<DeploymentRecord>>,
    onOpenSite: (SiteProject) -> Unit,
    onRollbackDeployment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(22.dp),
                        ),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_deploy_globe),
                        contentDescription = "Firebase Cloud Hosting Edge Network",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                                    )
                                )
                            )
                            .padding(18.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Firebase Edge Deployments",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "Live site endpoints, SSL status & instant rollback logs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (sitesState is UiState.Success) {
                val deployedSites = sitesState.data.filter { it.deployStatus == "deployed" }
                item {
                    Text(
                        text = "Live Endpoints (${deployedSites.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (deployedSites.isEmpty()) {
                    item {
                        EmptyDeploymentCard(
                            title = "No live sites published yet",
                            subtitle = "Open any generated site in Flux Studio and tap Deploy & Pay to publish it live."
                        )
                    }
                } else {
                    items(deployedSites, key = { "live_${it.id}" }) { site ->
                        SiteProjectCard(
                            site = site,
                            onOpenSite = { onOpenSite(site) },
                            onDeleteSite = {},
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Deployment Audit Ledger",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            when (deploymentsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Text(
                            text = deploymentsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is UiState.Success -> {
                    if (deploymentsState.data.isEmpty()) {
                        item {
                            EmptyDeploymentCard(
                                title = "No deployment events logged",
                                subtitle = "Every free or Google Pay deployment records a verifiable snapshot here."
                            )
                        }
                    } else {
                        items(deploymentsState.data, key = { it.id }) { record ->
                            DeploymentRecordCard(
                                record = record,
                                onRollback = { onRollbackDeployment(record.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BillingAndHostingScreen(
    subscriptionsState: UiState<List<HostingSubscription>>,
    activePlan: HostingPlanSpec,
    checkoutState: CheckoutState,
    googlePayAvailability: GooglePayAvailability,
    googlePayLauncher: ActivityResultLauncher<IntentSenderRequest>,
    onSubscribeWithGooglePay: (HostingPlanSpec, ActivityResultLauncher<IntentSenderRequest>) -> Unit,
    onCancelSubscription: (String) -> Unit,
    onRefreshGooglePay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isProcessing = checkoutState is CheckoutState.Processing

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(22.dp),
                        ),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_pro_hosting),
                        contentDescription = "Premium Pro Hosting Infrastructure",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.48f),
                                    )
                                )
                            )
                            .padding(18.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Google Pay & Premium Hosting",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "Active Plan: ${activePlan.name} • ${activePlan.bandwidthGb} GB Edge CDN",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = "Google Pay Status",
                                tint = when (googlePayAvailability) {
                                    is GooglePayAvailability.Ready -> EmeraldLive
                                    is GooglePayAvailability.Checking -> MaterialTheme.colorScheme.primary
                                    is GooglePayAvailability.Unavailable -> MaterialTheme.colorScheme.tertiary
                                },
                            )
                            Column {
                                Text(
                                    text = "Google Pay Integration",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = when (googlePayAvailability) {
                                        is GooglePayAvailability.Checking -> "Checking Wallet API..."
                                        is GooglePayAvailability.Ready -> "Connected • Test Environment Active"
                                        is GooglePayAvailability.Unavailable -> googlePayAvailability.reason
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        OutlinedButton(onClick = onRefreshGooglePay) {
                            Text("Verify")
                        }
                    }
                }
            }

            if (checkoutState is CheckoutState.Error) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = checkoutState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            if (checkoutState is CheckoutState.Success) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = EmeraldLive.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            text = checkoutState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldLive,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Hosting Tiers",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            items(HostingPlans.all.filter { it.isPaid }, key = { it.planId }) { plan ->
                val isCurrent = activePlan.planId == plan.planId
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isCurrent) 1.5.dp else 1.dp,
                            color = if (isCurrent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(20.dp),
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = plan.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = plan.tagline,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "$${plan.priceUsdFormatted}/mo",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            plan.features.forEach { feature ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = feature,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        if (isCurrent) {
                            Surface(
                                color = EmeraldLive.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "Current Active Plan",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldLive,
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        } else {
                            GooglePayCheckoutButton(
                                label = "Subscribe to ${plan.name}",
                                priceUsdFormatted = plan.priceUsdFormatted,
                                isLoading = isProcessing,
                                enabled = googlePayAvailability !is GooglePayAvailability.Checking,
                                testTag = "subscribe_gpay_${plan.planId}",
                                onClick = {
                                    onSubscribeWithGooglePay(plan, googlePayLauncher)
                                },
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Subscription Receipts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            when (subscriptionsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Text(
                            text = subscriptionsState.message,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is UiState.Success -> {
                    if (subscriptionsState.data.isEmpty()) {
                        item {
                            EmptyDeploymentCard(
                                title = "No active Google Pay subscriptions",
                                subtitle = "You are currently on Starter Edge Free. Upgrade above with Google Pay to unlock custom domains and 500 GB+ CDN."
                            )
                        }
                    } else {
                        items(subscriptionsState.data, key = { it.id }) { sub ->
                            SubscriptionReceiptCard(
                                subscription = sub,
                                onCancel = { onCancelSubscription(sub.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeploymentRecordCard(
    record: DeploymentRecord,
    onRollback: () -> Unit,
) {
    val isActive = record.status == "active"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Rounded.CloudDone else Icons.Rounded.History,
                        contentDescription = null,
                        tint = if (isActive) EmeraldLive else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = record.siteTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    color = if (isActive) EmeraldLive.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = record.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) EmeraldLive else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Text(
                text = record.deployedUrl,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val paymentText = if (record.amountUsdCents > 0L) {
                    val dollars = record.amountUsdCents / 100.0
                    "Tier: ${record.hostingTier.uppercase()} • G Pay $${String.format("%.2f", dollars)}"
                } else {
                    "Tier: ${record.hostingTier.uppercase()} • Free Edge"
                }
                Text(
                    text = paymentText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isActive) {
                    OutlinedButton(onClick = onRollback) {
                        Text("Roll Back")
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionReceiptCard(
    subscription: HostingSubscription,
    onCancel: () -> Unit,
) {
    val isActive = subscription.status == "active"
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreditCard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = subscription.planName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Surface(
                    color = if (isActive) EmeraldLive.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = subscription.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) EmeraldLive else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Text(
                text = "${subscription.paymentNetwork} • ${subscription.paymentTokenSummary}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isActive) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("cancel_sub_${subscription.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Plan")
                }
            }
        }
    }
}

@Composable
private fun EmptyDeploymentCard(
    title: String,
    subtitle: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.RocketLaunch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
