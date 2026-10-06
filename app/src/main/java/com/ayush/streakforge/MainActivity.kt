package com.ayush.streakforge

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ayush.streakforge.ui.MainUiState
import com.ayush.streakforge.ui.MainViewModel
import com.ayush.streakforge.ui.OAuthUiState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Night = Color(0xFF1B1230)
private val Plum = Color(0xFF2A1D45)
private val Ember = Color(0xFFFF6B2C)
private val Gold = Color(0xFFFFC24B)
private val Ash = Color(0xFFEDE6F5)
private val Muted = Color(0xFFA99BC4)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleRefresh(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Ember, background = Night, surface = Plum,
                    onSurface = Ash, onBackground = Ash, onPrimary = Color.White
                )
            ) {
                MainAppScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.resumePolling()
    }

    override fun onPause() {
        super.onPause()
        viewModel.pausePolling()
    }
}

@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val oauthState by viewModel.oauthState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var showHelpScreen by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(false) }
    var showManualTokenDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        if (showHelpScreen) {
            HelpScreen(onBack = { showHelpScreen = false })
        } else if (showOnboarding) {
            OnboardingScreen(onFinish = { showOnboarding = false })
        } else {
            when (val state = uiState) {
                is MainUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold)
                    }
                }
                is MainUiState.NotConnected -> {
                    when (val oState = oauthState) {
                        is OAuthUiState.CodeReceived -> {
                            DeviceCodeScreen(
                                state = oState,
                                onCopyCode = { code ->
                                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("GitHub User Code", code))
                                    Toast.makeText(ctx, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                onOpenGitHub = { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    ctx.startActivity(intent)
                                },
                                onCancel = { viewModel.cancelOAuth() }
                            )
                        }
                        is OAuthUiState.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Gold)
                                    Spacer(Modifier.height(16.dp))
                                    Text("Connecting to GitHub...", color = Ash)
                                }
                            }
                        }
                        is OAuthUiState.Error -> {
                            WelcomeScreen(
                                errorMessage = oState.message,
                                onContinueGitHub = { viewModel.startDeviceCodeFlow() },
                                onCreateAccount = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/signup"))
                                    ctx.startActivity(intent)
                                },
                                onUsePat = { showManualTokenDialog = true },
                                onOpenHelp = { showHelpScreen = true },
                                onStartOnboarding = { showOnboarding = true }
                            )
                        }
                        is OAuthUiState.Idle -> {
                            WelcomeScreen(
                                errorMessage = state.errorMsg,
                                onContinueGitHub = { viewModel.startDeviceCodeFlow() },
                                onCreateAccount = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/signup"))
                                    ctx.startActivity(intent)
                                },
                                onUsePat = { showManualTokenDialog = true },
                                onOpenHelp = { showHelpScreen = true },
                                onStartOnboarding = { showOnboarding = true }
                            )
                        }
                    }
                }
                is MainUiState.Success -> {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(containerColor = Plum) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Text("🔥", fontSize = 20.sp) },
                                    label = { Text("Home", color = if (selectedTab == 0) Gold else Muted) }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Text("🎁", fontSize = 20.sp) },
                                    label = { Text("Rewards", color = if (selectedTab == 1) Gold else Muted) }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Text("💻", fontSize = 20.sp) },
                                    label = { Text("DSA", color = if (selectedTab == 2) Gold else Muted) }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 },
                                    icon = { Text("👤", fontSize = 20.sp) },
                                    label = { Text("Account", color = if (selectedTab == 3) Gold else Muted) }
                                )
                            }
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            when (selectedTab) {
                                0 -> HomeTabContent(
                                    state = state,
                                    onRefresh = { viewModel.refresh() },
                                    onRestoreStreak = { viewModel.restoreStreak() }
                                )
                                1 -> RewardsTabContent(
                                    state = state,
                                    onClaimReward = { day, grantShield -> viewModel.claimReward(day, grantShield) },
                                    onSaveCustomReward = { day, emoji, label, grantShield ->
                                        viewModel.saveCustomReward(day, emoji, label, grantShield)
                                    }
                                )
                                2 -> DsaTabContent(
                                    state = state,
                                    onSetSolvedDsa = { solved -> viewModel.setSolvedDsa(solved) },
                                    onUpdateDsaGoal = { goal -> viewModel.updateDsaGoal(goal) }
                                )
                                3 -> AccountTabContent(
                                    state = state,
                                    onRefresh = { viewModel.refresh() },
                                    onSignOut = { viewModel.signOut() },
                                    onRevokeAccess = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/applications"))
                                        ctx.startActivity(intent)
                                    },
                                    onOpenProfile = { user ->
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$user"))
                                        ctx.startActivity(intent)
                                    },
                                    onExportData = {
                                        viewModel.exportData { jsonStr ->
                                            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("StreakForge Export", jsonStr))
                                            Toast.makeText(ctx, "Exported JSON copied to clipboard!", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onDeleteData = { viewModel.deleteData() },
                                    onOpenHelp = { showHelpScreen = true },
                                    onSetReminders = { enabled, hour, minute ->
                                        viewModel.setReminders(enabled, hour, minute)
                                    },
                                    onCheckForUpdates = { onResult ->
                                        viewModel.checkForUpdates(onResult)
                                    }
                                )

                            }
                        }
                    }
                }
            }
        }

        if (showManualTokenDialog) {
            ManualTokenDialog(
                onDismiss = { showManualTokenDialog = false },
                onSave = { user, token ->
                    showManualTokenDialog = false
                    viewModel.saveLogin(user, token)
                }
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    errorMessage: String,
    onContinueGitHub: () -> Unit,
    onCreateAccount: () -> Unit,
    onUsePat: () -> Unit,
    onOpenHelp: () -> Unit,
    onStartOnboarding: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Spacer(Modifier.height(30.dp)) }
        item {
            Image(
                painter = painterResource(R.drawable.flame_lit),
                contentDescription = "StreakForge Flame Logo",
                modifier = Modifier.size(110.dp)
            )
        }
        item {
            Text("StreakForge", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Code every day. Keep the fire alive.",
                color = Ash, textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.Medium
            )
        }

        if (errorMessage.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Ember.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        errorMessage,
                        color = Ember, modifier = Modifier.padding(14.dp),
                        fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Plum),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(
                        onClick = onContinueGitHub,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .semantics { contentDescription = "Continue with GitHub sign in" },
                        colors = ButtonDefaults.buttonColors(containerColor = Ember)
                    ) {
                        Text("Continue with GitHub", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onCreateAccount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .semantics { contentDescription = "Create a new GitHub account" }
                    ) {
                        Text("New here? Create a GitHub account", color = Ash)
                    }

                    TextButton(
                        onClick = onUsePat,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .semantics { contentDescription = "Use a personal access token instead" }
                    ) {
                        Text("Advanced: Use a personal access token instead", color = Gold, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onStartOnboarding) { Text("How it works 📖", color = Muted) }
                TextButton(onClick = onOpenHelp) { Text("Help & FAQ ❓", color = Muted) }
            }
        }
    }
}

@Composable
fun DeviceCodeScreen(
    state: OAuthUiState.CodeReceived,
    onCopyCode: (String) -> Unit,
    onOpenGitHub: (String) -> Unit,
    onCancel: () -> Unit
) {
    val minutes = state.remainingSeconds / 60
    val seconds = state.remainingSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Spacer(Modifier.height(20.dp)) }
        item {
            Text("GitHub Device Sign-In", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
            Text("Follow these 3 steps to connect your account securely", color = Muted, fontSize = 14.sp)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Step 1: Copy your code", fontWeight = FontWeight.Bold, color = Ash, fontSize = 15.sp)
                    Card(colors = CardDefaults.cardColors(containerColor = Night), shape = RoundedCornerShape(12.dp)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                state.response.userCode,
                                fontSize = 36.sp, fontWeight = FontWeight.Black,
                                color = Gold, letterSpacing = 4.sp,
                                modifier = Modifier.semantics { contentDescription = "User code: ${state.response.userCode}" }
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { onCopyCode(state.response.userCode) },
                                colors = ButtonDefaults.buttonColors(containerColor = Ember)
                            ) {
                                Text("📋 Copy Code")
                            }
                        }
                    }

                    Text("Step 2: Approve on GitHub", fontWeight = FontWeight.Bold, color = Ash, fontSize = 15.sp)
                    Button(
                        onClick = { onOpenGitHub(state.response.verificationUri) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Plum.copy(alpha = 0.8f))
                    ) {
                        Text("🌐 Open GitHub Verification Page", color = Gold)
                    }

                    Text("Step 3: Come back to StreakForge", fontWeight = FontWeight.Bold, color = Ash, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Waiting for GitHub approval... ($formattedTime remaining)", color = Ash, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Ember)
            }
        }
    }
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }

    val titles = listOf(
        "Code Every Single Day",
        "Encrypted & Local-First",
        "Live Home-screen Widget"
    )
    val descriptions = listOf(
        "StreakForge tracks your daily GitHub contribution streak, awards milestones, and guides your 6-month DSA journey.",
        "Zero external servers. Your authentication token is encrypted using Android Keystore directly on your phone.",
        "Add the live flame widget to your home screen to keep your streak visual front and center all day."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("StreakForge Onboarding", color = Muted, fontSize = 13.sp)
            TextButton(onClick = onFinish) { Text("Skip", color = Gold) }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(if (page == 0) R.drawable.flame_lit else if (page == 1) R.drawable.flame_fading else R.drawable.wood_burnt),
                contentDescription = "Onboarding visual step ${page + 1}",
                modifier = Modifier.size(120.dp)
            )
            Spacer(Modifier.height(30.dp))
            Text(titles[page], fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Gold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(descriptions[page], fontSize = 15.sp, color = Ash, textAlign = TextAlign.Center)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..2).forEach { index ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (index == page) Gold else Muted)
                    )
                }
            }

            Button(
                onClick = {
                    if (page < 2) page++ else onFinish()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Ember)
            ) {
                Text(if (page == 2) "Get Started" else "Next")
            }
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("⬅️", fontSize = 20.sp) }
                Text("Help & Troubleshooting", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gold)
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📲 How to Add the Home-Screen Widget", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                    Text("• Google Pixel / Stock Android: Long press any empty space on home screen -> Widgets -> StreakForge -> Drag to screen.", color = Ash, fontSize = 13.sp)
                    Text("• Samsung One UI: Pinch home screen -> Widgets -> Search StreakForge -> Add.", color = Ash, fontSize = 13.sp)
                    Text("• Xiaomi (MIUI / HyperOS): Enable 'Autostart' and 'Display on Lock screen' in App Info permissions for background widget updates.", color = Ash, fontSize = 13.sp)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("❓ Why a Streak May Not Update", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                    Text("1. Commit email not verified on GitHub.", color = Ash, fontSize = 13.sp)
                    Text("2. Commits pushed to non-default branch (must be pushed to default branch).", color = Ash, fontSize = 13.sp)
                    Text("3. Fork contributions: Enable 'Private & Fork contributions' on your GitHub profile settings.", color = Ash, fontSize = 13.sp)
                    Text("4. GitHub UTC Days: GitHub operates on UTC timezone (00:00 UTC reset).", color = Ash, fontSize = 13.sp)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🔋 Battery & Background Refresh Settings", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                    Text("To ensure background updates every 30 minutes, exclude StreakForge from OS aggressive battery saver optimization.", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ManualTokenDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use Personal Access Token") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("GitHub Username") }, singleLine = true)
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("Personal Access Token") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(user, token) }, colors = ButtonDefaults.buttonColors(containerColor = Ember)) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ash) }
        }
    )
}

@Composable
fun HomeTabContent(
    state: MainUiState.Success,
    onRefresh: () -> Unit,
    onRestoreStreak: () -> Unit
) {
    val ctx = LocalContext.current
    val userState = state.userState
    val streakInfo = userState.streakInfo

    var debugStreakOverride by remember { mutableStateOf<StreakInfo?>(null) }
    val effectiveStreak = debugStreakOverride ?: streakInfo

    var selectedDayDetail by remember { mutableStateOf<ContributionDay?>(null) }

    val quote = QUOTES[LocalDate.now().dayOfYear % QUOTES.size]
    val stState = flameState(effectiveStreak)

    val animScale = try {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: Exception) {
        1f
    }
    val isAnimationEnabled = animScale > 0f

    val flameScale = if (stState == FlameState.LIT && isAnimationEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "flameFlicker")
        val s by infiniteTransition.animateFloat(
            initialValue = 0.96f, targetValue = 1.04f,
            animationSpec = infiniteRepeatable(animation = tween(800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "flameScale"
        )
        s
    } else 1f

    if (selectedDayDetail != null) {
        AlertDialog(
            onDismissRequest = { selectedDayDetail = null },
            title = { Text("Contribution Details 📅") },
            text = {
                Column {
                    Text("Date: ${selectedDayDetail?.date}", color = Ash, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Contributions: ${selectedDayDetail?.count}",
                        color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDayDetail = null }) { Text("Close", color = Ember) }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (userState.isStale) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Ember.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "⚠️ Stale Data (Last synced > 6 hours ago)",
                            color = Ember, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRefresh) { Text("Sync Now", color = Gold, fontSize = 12.sp) }
                    }
                }
            }
        }

        // Flame Hero Section
        item {
            val flameRes = when (stState) {
                FlameState.LIT -> R.drawable.flame_lit
                FlameState.FADING -> R.drawable.flame_fading
                FlameState.BROKEN -> R.drawable.wood_burnt
            }
            val numberColor = when (stState) {
                FlameState.LIT -> Gold
                FlameState.FADING -> Muted
                FlameState.BROKEN -> Ash
            }
            val statusMsg = when {
                effectiveStreak.shieldActive -> "🛡️ Shield Active! Protected 1 missed day."
                stState == FlameState.LIT -> "Today is in the bank. Nice work."
                stState == FlameState.FADING -> "Push today to keep it alive"
                else -> "Streak broke. Start again today."
            }
            val statusColor = when (stState) {
                FlameState.LIT -> Gold
                FlameState.FADING -> Ember
                FlameState.BROKEN -> Ember
            }

            val flameAccessibilityText = when (stState) {
                FlameState.LIT -> "Flame status: Lit. Current streak ${effectiveStreak.current} days."
                FlameState.FADING -> "Flame status: Fading. Current streak ${effectiveStreak.current} days. Push today to keep it alive."
                FlameState.BROKEN -> "Flame status: Broken. Current streak 0 days. Streak broke. Start again today."
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@${userState.userLogin}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (userState.lastSynced > 0) {
                        val minsAgo = ((System.currentTimeMillis() - userState.lastSynced) / (60 * 1000)).coerceAtLeast(0)
                        Text(" • Synced ${minsAgo}m ago", color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))

                Image(
                    painter = painterResource(flameRes),
                    contentDescription = flameAccessibilityText,
                    modifier = Modifier
                        .size(110.dp)
                        .scale(flameScale)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${effectiveStreak.current}",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    color = numberColor,
                    modifier = if (BuildConfig.DEBUG) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    debugStreakOverride = when (stState) {
                                        FlameState.LIT -> effectiveStreak.copy(todayDone = false)
                                        FlameState.FADING -> effectiveStreak.copy(current = 0, todayDone = false)
                                        FlameState.BROKEN -> effectiveStreak.copy(current = 5, todayDone = true)
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
                Text("day streak", fontSize = 18.sp, color = Ash)
                Spacer(Modifier.height(8.dp))
                Text(statusMsg, color = statusColor, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡️ ${effectiveStreak.shields} Shield${if (effectiveStreak.shields != 1) "s" else ""} available", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(" • Best: ${effectiveStreak.longest} days", color = Muted, fontSize = 13.sp)
                }
                if (state.statusMessage.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.statusMessage, color = Ember, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                    Text(if (state.isRefreshing) "Refreshing..." else "Refresh", color = Gold)
                }
            }
        }

        // 14-Day Contribution Strip
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📊 14-Day Activity Strip", fontWeight = FontWeight.Bold, color = Ash, fontSize = 15.sp)
                        Text(userState.streakInfo.localUtcResetTime, color = Gold, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Tap any day square to see exact GitHub contribution count.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(effectiveStreak.last14Days) { day ->
                            val isActive = day.count > 0
                            val dayDesc = "${day.date}: ${day.count} contribution${if (day.count != 1) "s" else ""}"
                            Card(
                                onClick = { selectedDayDetail = day },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) Ember.copy(alpha = 0.35f) else Night
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.semantics { contentDescription = dayDesc }
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        if (day.date.length >= 5) day.date.substring(5) else day.date,
                                        color = Muted, fontSize = 10.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${day.count}",
                                        color = if (isActive) Gold else Ash,
                                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Restore Streak Action
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛡️ Restore Streak", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                        Text("${userState.restoresLeft}/2 left this month", color = Gold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("You can restore your streak up to 2 times every month if you missed a commit.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRestoreStreak,
                        enabled = userState.restoresLeft > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Ember)
                    ) {
                        Text("Restore Streak (${userState.restoresLeft} available)")
                    }
                }
            }
        }

        // Quote of the Day
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Quote of the Day", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("“$quote”", fontStyle = FontStyle.Italic, fontSize = 15.sp, color = Ash)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun RewardsTabContent(
    state: MainUiState.Success,
    onClaimReward: (Int, Boolean) -> Unit,
    onSaveCustomReward: (Int, String, String, Boolean) -> Unit
) {
    val userState = state.userState
    val streakInfo = userState.streakInfo
    val rewards = userState.customRewards

    val nextReward = rewards.firstOrNull { it.day > streakInfo.current }
    var editingReward by remember { mutableStateOf<Reward?>(null) }

    if (editingReward != null) {
        val target = editingReward!!
        var emoji by remember { mutableStateOf(target.emoji) }
        var label by remember { mutableStateOf(target.label) }

        AlertDialog(
            onDismissRequest = { editingReward = null },
            title = { Text("Edit Reward (Day ${target.day})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Emoji") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Reward Name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCustomReward(target.day, emoji.trim(), label.trim(), target.grantShield)
                        editingReward = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ember)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingReward = null }) { Text("Cancel", color = Ash) }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Streak Rewards", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
            Text("Set goals, unlock treats, and earn Streak Shields!", color = Muted, fontSize = 14.sp)
        }

        // Next Reward Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Next Reward Goal", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    if (nextReward == null) {
                        Text("Every reward unlocked. Unstoppable legend!", color = Gold, fontWeight = FontWeight.Bold)
                    } else {
                        val remaining = nextReward.day - streakInfo.current
                        Text("$remaining days to go: ${nextReward.emoji} ${nextReward.label}", color = Ash, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))
                        val progressFraction = (streakInfo.current.toFloat() / nextReward.day).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = Ember,
                            trackColor = Night
                        )
                    }
                }
            }
        }

        // Reward List Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("All Rewards", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                        Text("${userState.claimedRewards.size}/${rewards.size} claimed", color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))

                    rewards.forEach { r ->
                        val unlocked = streakInfo.longest >= r.day || streakInfo.current >= r.day
                        val isClaimed = r.day in userState.claimedRewards

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(r.emoji, fontSize = 24.sp)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    r.label,
                                    color = if (unlocked) Ash else Muted,
                                    fontWeight = if (r.grantShield) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    "Day ${r.day}${if (r.grantShield) " • +1 Shield 🛡️" else ""}",
                                    color = if (r.grantShield) Gold else Muted, fontSize = 12.sp
                                )
                            }

                            IconButton(onClick = { editingReward = r }) {
                                Text("✏️", fontSize = 14.sp)
                            }

                            when {
                                isClaimed -> Text("Claimed", color = Muted, fontSize = 13.sp)
                                unlocked -> Button(
                                    onClick = { onClaimReward(r.day, r.grantShield) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Ember)
                                ) { Text("Claim", fontSize = 13.sp) }
                                else -> Text("Locked", color = Muted, fontSize = 13.sp)
                            }
                        }
                        Divider(color = Night.copy(alpha = 0.5f), thickness = 1.dp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun DsaTabContent(
    state: MainUiState.Success,
    onSetSolvedDsa: (Int) -> Unit,
    onUpdateDsaGoal: (Int) -> Unit
) {
    val userState = state.userState
    val solved = userState.solvedDsa
    val goal = userState.dailyDsaGoal

    var showGoalDialog by remember { mutableStateOf(false) }

    val planStart = try { LocalDate.parse(userState.planStart) } catch (e: Exception) { LocalDate.now() }
    val daysIn = ChronoUnit.DAYS.between(planStart, LocalDate.now()).toInt().coerceAtLeast(0)
    val week = (daysIn / 7 + 1).coerceAtMost(DSA_WEEKS.size)
    val expected = ((daysIn + 1) * goal).coerceAtMost(DSA_TARGET)
    val diff = solved - expected

    val paceStatus = when {
        diff > 0 -> "Ahead by $diff problem${if (diff != 1) "s" else ""}! 🚀"
        diff == 0 -> "On pace! 👍"
        else -> "Behind by ${-diff} problem${if (-diff != 1) "s" else ""}. 🎯"
    }

    val paceColor = when {
        diff >= 0 -> Gold
        else -> Ember
    }

    if (showGoalDialog) {
        var tempGoal by remember { mutableIntStateOf(goal) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Edit Daily DSA Goal") },
            text = {
                Column {
                    Text("Daily Problem Goal:", color = Ash)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { if (tempGoal > 1) tempGoal-- },
                            enabled = tempGoal > 1
                        ) { Text("-") }
                        Spacer(Modifier.width(16.dp))
                        Text("$tempGoal problems/day", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(16.dp))
                        OutlinedButton(onClick = { tempGoal++ }) { Text("+") }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDsaGoal(tempGoal)
                        showGoalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ember)
                ) { Text("Save Goal") }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text("Cancel", color = Ash) }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("6-Month DSA Tracker", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Text("Goal: 360 Problems ($goal / day)", color = Muted, fontSize = 14.sp)
                }
                OutlinedButton(onClick = { showGoalDialog = true }) {
                    Text("Goal Settings ⚙️", color = Gold, fontSize = 12.sp)
                }
            }
        }

        // Progress Overview Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Overall Progress", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    val progressFraction = (solved.toFloat() / DSA_TARGET).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = Gold,
                        trackColor = Night
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("$solved / $DSA_TARGET Solved", color = Ash, fontWeight = FontWeight.Bold)
                        Text("${(progressFraction * 100).toInt()}% Complete", color = Gold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(paceStatus, color = paceColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Expected target by today: $expected solved", color = Muted, fontSize = 12.sp)

                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSetSolvedDsa(solved - 1) },
                            enabled = solved > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text("-1 Solved", color = Ash) }

                        Button(
                            onClick = { onSetSolvedDsa(solved + 1) },
                            colors = ButtonDefaults.buttonColors(containerColor = Ember),
                            modifier = Modifier.weight(1f)
                        ) { Text("+1 Solved") }
                    }
                }
            }
        }

        // 26-Week Topics List
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("26-Week Curriculum", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))

                    DSA_WEEKS.forEachIndexed { idx, topic ->
                        val wNum = idx + 1
                        val isCurrentWeek = wNum == week
                        val isPastWeek = wNum < week

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "W$wNum",
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrentWeek) Ember else if (isPastWeek) Gold else Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.width(36.dp)
                            )
                            Text(
                                topic,
                                color = if (isCurrentWeek) Ash else if (isPastWeek) Ash.copy(alpha = 0.8f) else Muted,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrentWeek) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCurrentWeek) {
                                Text("Current", color = Ember, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (idx < DSA_WEEKS.size - 1) {
                            Divider(color = Night.copy(alpha = 0.5f), thickness = 1.dp)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun AccountTabContent(
    state: MainUiState.Success,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onRevokeAccess: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onExportData: () -> Unit,
    onDeleteData: () -> Unit,
    onOpenHelp: () -> Unit,
    onSetReminders: (Boolean, Int, Int) -> Unit,
    onCheckForUpdates: ((com.ayush.streakforge.data.UpdateCheckResult) -> Unit) -> Unit
) {
    val ctx = LocalContext.current
    val userState = state.userState
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<com.ayush.streakforge.data.UpdateCheckResult?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onSetReminders(true, userState.reminderHour, userState.reminderMinute)
        } else {
            Toast.makeText(ctx, "Notification permission is required for daily reminders", Toast.LENGTH_SHORT).show()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete My Data") },
            text = { Text("Are you sure you want to permanently delete all local contribution data and credentials for @${userState.userLogin}?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDeleteData() }) {
                    Text("Delete", color = Ember)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = Ash) }
            }
        )
    }

    if (updateCheckResult != null) {
        when (val res = updateCheckResult) {
            is com.ayush.streakforge.data.UpdateCheckResult.UpdateAvailable -> {
                AlertDialog(
                    onDismissRequest = { updateCheckResult = null },
                    title = { Text("Update Available! 🚀") },
                    text = {
                        Column {
                            Text("Version ${res.latestVersion} is now available on GitHub Releases!", color = Gold, fontWeight = FontWeight.Bold)
                            if (res.notes.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(res.notes, color = Ash, fontSize = 13.sp)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(res.releaseUrl))
                                ctx.startActivity(intent)
                                updateCheckResult = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Ember)
                        ) {
                            Text("Open Release Page")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { updateCheckResult = null }) { Text("Later", color = Ash) }
                    }
                )
            }
            is com.ayush.streakforge.data.UpdateCheckResult.AlreadyLatest -> {
                Toast.makeText(ctx, "You are using the latest version (v${res.currentVersion})", Toast.LENGTH_LONG).show()
                updateCheckResult = null
            }
            is com.ayush.streakforge.data.UpdateCheckResult.NoReleasesFound -> {
                Toast.makeText(ctx, "No releases published yet on GitHub.", Toast.LENGTH_SHORT).show()
                updateCheckResult = null
            }
            is com.ayush.streakforge.data.UpdateCheckResult.Error -> {
                Toast.makeText(ctx, res.message, Toast.LENGTH_LONG).show()
                updateCheckResult = null
            }
            else -> {}
        }
    }

    if (showReminderTimePicker) {
        var hour by remember { mutableIntStateOf(userState.reminderHour) }
        var minute by remember { mutableIntStateOf(userState.reminderMinute) }

        AlertDialog(
            onDismissRequest = { showReminderTimePicker = false },
            title = { Text("Set Reminder Time ⏰") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Default: 8:00 PM (20:00)", color = Muted, fontSize = 13.sp)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Hour", color = Ash, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { hour = (hour - 1 + 24) % 24 }) { Text("-") }
                                Text(String.format("%02d", hour), color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                OutlinedButton(onClick = { hour = (hour + 1) % 24 }) { Text("+") }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Minute", color = Ash, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { minute = (minute - 15 + 60) % 60 }) { Text("-") }
                                Text(String.format("%02d", minute), color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                OutlinedButton(onClick = { minute = (minute + 15) % 60 }) { Text("+") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSetReminders(true, hour, minute)
                        showReminderTimePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Ember)
                ) { Text("Set Reminder") }
            },
            dismissButton = {
                TextButton(onClick = { showReminderTimePicker = false }) { Text("Cancel", color = Ash) }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Header Card with Coil Image Loading
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (userState.avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = userState.avatarUrl,
                            contentDescription = "GitHub Avatar for @${userState.userLogin}",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.flame_lit),
                            contentDescription = "Default Avatar",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(userState.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Text("@${userState.userLogin}", fontSize = 14.sp, color = Ash)
                    if (userState.bio.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(userState.bio, fontSize = 13.sp, color = Muted, textAlign = TextAlign.Center)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${userState.followersCount}", fontWeight = FontWeight.Bold, color = Gold, fontSize = 16.sp)
                            Text("Followers", color = Muted, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${userState.publicReposCount}", fontWeight = FontWeight.Bold, color = Gold, fontSize = 16.sp)
                            Text("Public Repos", color = Muted, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${userState.streakInfo.total}", fontWeight = FontWeight.Bold, color = Gold, fontSize = 16.sp)
                            Text("Total Commits", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Daily Reminders Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("🔔 Daily Reminders", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                            Text(
                                if (userState.remindersEnabled)
                                    "Fires at ${String.format("%02d:%02d", userState.reminderHour, userState.reminderMinute)} if not pushed today"
                                else "Disabled",
                                color = Muted, fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = userState.remindersEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onSetReminders(true, userState.reminderHour, userState.reminderMinute)
                                    }
                                } else {
                                    onSetReminders(false, userState.reminderHour, userState.reminderMinute)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Ember)
                        )
                    }

                    if (userState.remindersEnabled) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showReminderTimePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("⏰ Change Time (${String.format("%02d:%02d", userState.reminderHour, userState.reminderMinute)})", color = Gold)
                        }
                    }
                }
            }
        }

        // Actions Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Account Actions", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)

                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Ember)
                    ) {
                        Text("🔄 Sync Now")
                    }

                    OutlinedButton(
                        onClick = {
                            isCheckingUpdate = true
                            Toast.makeText(ctx, "Checking for updates...", Toast.LENGTH_SHORT).show()
                            onCheckForUpdates { result ->
                                isCheckingUpdate = false
                                updateCheckResult = result
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isCheckingUpdate) "Checking..." else "🚀 Check for Updates", color = Ash)
                    }

                    OutlinedButton(
                        onClick = { onOpenProfile(userState.userLogin) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🌐 Open My GitHub Profile", color = Ash)
                    }

                    OutlinedButton(
                        onClick = onRevokeAccess,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔑 Revoke Access on GitHub", color = Ash)
                    }

                    OutlinedButton(
                        onClick = onExportData,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📦 Export My Data (JSON)", color = Ash)
                    }

                    Button(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Plum.copy(alpha = 0.9f))
                    ) {
                        Text("🚪 Sign Out", color = Gold)
                    }

                    TextButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Delete My Data", color = Ember, fontSize = 13.sp)
                    }
                }
            }
        }

        // Privacy & Security Guarantee
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Plum), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🛡️ Privacy & Security Guarantee", fontWeight = FontWeight.Bold, color = Ash, fontSize = 16.sp)
                    Text("• Requested Scope: read:user (read-only profile & contributions).", color = Muted, fontSize = 13.sp)
                    Text("• Encryption: OAuth token is stored encrypted using Android Keystore.", color = Muted, fontSize = 13.sp)
                    Text("• Zero Tracking: No ads, no analytics, no third-party servers.", color = Muted, fontSize = 13.sp)
                    Text("• Direct Connection: Data flows directly between your phone and api.github.com.", color = Muted, fontSize = 13.sp)
                    TextButton(onClick = onOpenHelp) { Text("Help & Troubleshooting ❓", color = Gold) }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
