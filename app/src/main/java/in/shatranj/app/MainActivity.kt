package `in`.shatranj.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import `in`.shatranj.app.data.AiEngineType
import `in`.shatranj.app.data.BoardTheme
import `in`.shatranj.app.data.PieceColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import `in`.shatranj.app.data.GameMode
import `in`.shatranj.app.data.PieceType
import `in`.shatranj.app.ui.ChessViewModel
import `in`.shatranj.app.ui.screens.*
import `in`.shatranj.app.ui.theme.ArenaColors
import `in`.shatranj.app.ui.theme.ChessArenaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChessArenaTheme {
                val vm: ChessViewModel = viewModel()
                val navController = rememberNavController()
                var showSettingsDialog by remember { mutableStateOf(false) }
                val profile by vm.profile.collectAsState()
                val activeGame by vm.activeGame.collectAsState()
                val selectedModeId by vm.selectedModeId.collectAsState()
                val selectedAiElo by vm.selectedAiElo.collectAsState()
                val showResign by vm.showResignDialog.collectAsState()
                val toastMsg by vm.toastMessage.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }
                val clipboard = LocalClipboardManager.current

                LaunchedEffect(toastMsg) {
                    toastMsg?.let {
                        snackbarHostState.showSnackbar(it)
                        vm.clearToast()
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideScreen = maxWidth >= 700.dp
                    val inActiveMatch = activeGame != null && activeGame?.over == false && activeGame?.reviewMode == false
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val navItems = listOf(
                        Triple("home", "Home", Icons.Default.Home),
                        Triple("play", "Play", Icons.Default.PlayArrow),
                        Triple("stats", "Stats", Icons.Default.BarChart),
                        Triple("ranks", "Ranks", Icons.Default.EmojiEvents)
                    )

                    Scaffold(
                        topBar = {
                            if (!inActiveMatch) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ArenaColors.ObsidianSurface)
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Image(
                                            painter = painterResource(id = R.drawable.app_icon),
                                            contentDescription = "Shatranj.in",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                        Text(
                                            "Shatranj.in",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ArenaColors.TextPrimary
                                        )
                                    }
                                    FilledTonalButton(onClick = { showSettingsDialog = true }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                                        Spacer(Modifier.width(6.dp))
                                        Text("Settings")
                                    }
                                }
                            }
                        },
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            if (!inActiveMatch && !isWideScreen) {
                                NavigationBar(containerColor = ArenaColors.ObsidianSurface) {
                                    navItems.forEach { (route, label, icon) ->
                                        NavigationBarItem(
                                            selected = currentRoute == route,
                                            onClick = {
                                                if (route == "play" && activeGame?.over == true) vm.dismissGameOver()
                                                navController.navigate(route) { launchSingleTop = true }
                                            },
                                            icon = { Icon(icon, contentDescription = label) },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }
                        }
                    ) { padding ->
                        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                            if (!inActiveMatch && isWideScreen) {
                                NavigationRail(containerColor = ArenaColors.ObsidianSurface) {
                                    Spacer(Modifier.height(12.dp))
                                    navItems.forEach { (route, label, icon) ->
                                        NavigationRailItem(
                                            selected = currentRoute == route,
                                            onClick = {
                                                if (route == "play" && activeGame?.over == true) vm.dismissGameOver()
                                                navController.navigate(route) { launchSingleTop = true }
                                            },
                                            icon = { Icon(icon, contentDescription = label) },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }
                            NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        composable("home") {
                            HomeScreen(
                                profile = profile,
                                activeGame = activeGame,
                                onQuickPlay = { vm.launchQuickPlayRecent { navController.navigate("play") { launchSingleTop = true } } },
                                onOpenLobby = { navController.navigate("play") { launchSingleTop = true } },
                                onResumeMatch = { navController.navigate("play") { launchSingleTop = true } },
                                onImportPgn = vm::loadPgnReplay
                            )
                        }
                        composable("play") {
                            PlayScreen(
                                activeGame = activeGame,
                                selectedModeId = selectedModeId,
                                selectedAiElo = selectedAiElo,
                                selectedEngine = profile.selectedEngine,
                                onSelectEngine = vm::selectAiEngine,
                                canRejoinLastResigned = vm.canRejoinLastResigned,
                                onSelectMode = vm::selectMode,
                                onSelectAiElo = vm::selectAiElo,
                                onStartMatch = { mode -> vm.startNewMatch(mode) },
                                onSquareTap = vm::onSquareTapped,
                                onFlipBoard = vm::toggleFlip,
                                onUndoMove = vm::undoMove,
                                onRequestResign = vm::requestResign,
                                onRejoinLast = vm::rejoinLastResignedMatch,
                                onStepReview = vm::stepReview,
                                onExitReview = vm::dismissGameOver,
                                onCopyPgn = { clipboard.setText(AnnotatedString(vm.buildPgn())) },
                                whiteAccuracy = vm.computeSideAccuracy(PieceColor.WHITE),
                                blackAccuracy = vm.computeSideAccuracy(PieceColor.BLACK),
                                boardTheme = profile.boardTheme,
                                onSelectBoardTheme = vm::selectBoardTheme,
onImportPgn = vm::loadPgnReplay
                            )
                        }
                        composable("stats") { StatsScreen(profile) }
                        composable("ranks") { LeaderboardScreen(profile) }
                    }
                        }
                    }
                }

                if (showSettingsDialog) {
                    var nameDraft by remember(profile.displayName) { mutableStateOf(profile.displayName) }
                    AlertDialog(
                        onDismissRequest = { showSettingsDialog = false },
                        containerColor = ArenaColors.ObsidianSurface,
                        title = { Text("Settings", fontWeight = FontWeight.Bold, color = ArenaColors.TextPrimary) },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = nameDraft,
                                    onValueChange = { if (it.length <= 20) nameDraft = it },
                                    label = { Text("Player Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Sound Effects", color = ArenaColors.TextPrimary)
                                    Switch(
                                        checked = profile.soundEnabled,
                                        onCheckedChange = { vm.toggleSound() }
                                    )
                                }
                                Text("Board Theme", color = ArenaColors.TextMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BoardTheme.entries.forEach { t ->
                                        FilterChip(
                                            selected = profile.boardTheme == t,
                                            onClick = { vm.selectBoardTheme(t) },
                                            label = { Text(t.label) }
                                        )
                                    }
                                }
                                Text("AI Engine (Default: Leela Lc0)", color = ArenaColors.TextMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AiEngineType.entries.forEach { eng ->
                                        FilterChip(
                                            selected = profile.selectedEngine == eng,
                                            onClick = { vm.selectAiEngine(eng) },
                                            label = { Text(eng.label) }
                                        )
                                    }
                                }
                                Text("Open-Source Licenses & Credits", color = ArenaColors.ChampagneGold, fontWeight = FontWeight.Bold)
                                Surface(
                                    color = ArenaColors.ObsidianRaised,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("• Stockfish & Leela Chess Zero (Lc0): GNU GPLv3 (when native libstockfish.so / liblc0.so binaries are bundled; full source available under GPLv3).", color = ArenaColors.TextMuted)
                                        Text("• Built-in Fallback Engine: Original Shatranj.in Kotlin engine.", color = ArenaColors.TextMuted)
                                        Text("• AndroidX / Material Icons: Apache License 2.0.", color = ArenaColors.TextMuted)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                vm.updateDisplayName(nameDraft)
                                showSettingsDialog = false
                            }) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSettingsDialog = false }) { Text("Close") }
                        }
                    )
                }

                if (showResign) {
                    AlertDialog(
                        onDismissRequest = vm::dismissResignDialog,
                        title = { Text("Resign Match?", fontWeight = FontWeight.Bold) },
                        text = { Text("Are you sure you want to resign the current game?") },
                        confirmButton = {
                            TextButton(onClick = vm::confirmResign) { Text("Resign") }
                        },
                        dismissButton = {
                            TextButton(onClick = vm::dismissResignDialog) { Text("Keep Playing") }
                        }
                    )
                }

                activeGame?.pendingPromotion?.let {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Promote Pawn") },
                        text = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { type ->
                                    Button(onClick = { vm.completePromotion(type) }) { Text(type.symbol.toString()) }
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }

                activeGame?.takeIf { it.over && !it.reviewMode }?.let { g ->
                    AlertDialog(
                        onDismissRequest = vm::dismissGameOver,
                        title = { Text(g.resultTitle, fontWeight = FontWeight.ExtraBold) },
                        text = {
                            val isRated = g.mode == GameMode.AI && g.modeId in setOf("timeless", "blitz3", "blitz5", "rapid")
                            val suffix = when {
                                isRated -> " (${if (g.eloDelta >= 0) "+" else ""}${g.eloDelta} Elo)"
                                g.mode == GameMode.AI -> " (0 Elo • Unrated Variant)"
                                else -> " (Pass & Play • Unrated)"
                            }
                            Text("${g.resultReason}$suffix")
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = vm::enterReviewMode) { Text("Review") }
                                TextButton(onClick = { clipboard.setText(AnnotatedString(vm.buildPgn())) }) { Text("PGN") }
                                TextButton(onClick = vm::dismissGameOver) { Text("Back") }
                                TextButton(onClick = {
                                    val prevMode = g.mode
                                    vm.dismissGameOver()
                                    vm.startNewMatch(prevMode) {
                                        navController.navigate("play") { launchSingleTop = true }
                                    }
                                }) { Text("Play Again") }
                            }
                        },
                        dismissButton = if (vm.canRejoinLastResigned) {
                            { TextButton(onClick = vm::rejoinLastResignedMatch) { Text("Rejoin (Undo Resign)") } }
                        } else null
                    )
                }
            }
        }
    }
}
