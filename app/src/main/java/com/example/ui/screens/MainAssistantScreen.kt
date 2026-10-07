package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InteractionMemory
import com.example.data.model.LlmProvider
import com.example.service.VoiceState
import com.example.ui.components.MemoryCard
import com.example.ui.components.MicWidget
import com.example.ui.theme.GlowGreen
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.SoftPurple
import com.example.viewmodel.AssistantViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAssistantScreen(
    viewModel: AssistantViewModel,
    hasAudioPermission: Boolean,
    onRequestAudioPermission: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val memories by viewModel.filteredMemories.collectAsStateWithLifecycle()
    val allMemories by viewModel.allMemories.collectAsStateWithLifecycle()
    val voiceState by viewModel.speechManager.voiceState.collectAsStateWithLifecycle()
    val soundLevel by viewModel.speechManager.soundLevel.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.notificationMessage) {
        uiState.notificationMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissNotification()
        }
    }

    var showManualAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBarHeader(
                activeTab = uiState.activeTab,
                isDarkTheme = uiState.isDarkTheme,
                onToggleTheme = { viewModel.toggleTheme() },
                onTabSelect = { viewModel.setActiveTab(it) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Prominent Mic Widget Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                MicWidget(
                    voiceState = voiceState,
                    soundLevel = soundLevel,
                    isAvailable = viewModel.speechManager.isAvailable(),
                    onMicClick = {
                        if (!hasAudioPermission) {
                            onRequestAudioPermission()
                        } else {
                            if (voiceState is VoiceState.Listening) {
                                viewModel.stopListening()
                            } else {
                                viewModel.startListening()
                            }
                        }
                    }
                )
            }

            // Quick LLM & AI status banner
            LlmStatusBanner(
                provider = uiState.currentLlmProvider,
                isAnalyzing = uiState.isAnalyzingLlm,
                latestAnalysis = uiState.latestAnalysis,
                onConfigureClick = { viewModel.setActiveTab(3) }
            )

            // Dynamic Body Content based on active tab
            when (uiState.activeTab) {
                0 -> FeedTab(
                    memories = memories,
                    selectedCategory = uiState.selectedCategoryFilter,
                    onSelectCategory = { viewModel.setCategoryFilter(it) },
                    onManualAddClick = { showManualAddDialog = true },
                    onCompleteToggle = { viewModel.toggleTaskComplete(it) },
                    onSyncCalendar = { viewModel.syncToCalendar(context, it) },
                    onSyncTasks = { viewModel.syncToTasks(context, it) },
                    onDelete = { viewModel.deleteMemory(it.id) }
                )
                1 -> SemanticSearchTab(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.performSemanticSearch(it) },
                    isSearching = uiState.isSearching,
                    results = uiState.semanticSearchResults,
                    onCompleteToggle = { viewModel.toggleTaskComplete(it) },
                    onSyncCalendar = { viewModel.syncToCalendar(context, it) },
                    onSyncTasks = { viewModel.syncToTasks(context, it) },
                    onDelete = { viewModel.deleteMemory(it.id) }
                )
                2 -> TasksRemindersTab(
                    memories = allMemories.filter { it.category == "TASK" || it.category == "REMINDER" },
                    onCompleteToggle = { viewModel.toggleTaskComplete(it) },
                    onSyncCalendar = { viewModel.syncToCalendar(context, it) },
                    onSyncTasks = { viewModel.syncToTasks(context, it) },
                    onDelete = { viewModel.deleteMemory(it.id) }
                )
                3 -> SettingsAndBackupTab(
                    currentProvider = uiState.currentLlmProvider,
                    apiKey = uiState.apiKey,
                    customModel = uiState.customModel,
                    totalMemoriesCount = allMemories.size,
                    isDarkTheme = uiState.isDarkTheme,
                    onToggleTheme = { viewModel.toggleTheme() },
                    onSaveLlm = { p, k, m -> viewModel.updateLlmConfig(p, k, m) },
                    onCreateBackup = { pass -> viewModel.createEncryptedBackup(pass) },
                    onRestoreBackup = { data, pass -> viewModel.restoreEncryptedBackup(data, pass) }
                )
            }
        }
    }

    if (showManualAddDialog) {
        ManualAddDialog(
            onDismiss = { showManualAddDialog = false },
            onAdd = {
                viewModel.manualAddInteraction(it)
                showManualAddDialog = false
            }
        )
    }
}

@Composable
fun TopAppBarHeader(
    activeTab: Int,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onTabSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(GlowGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OmniVoice AI",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Theme Toggle Icon Button
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("theme_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted Local Vector DB",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "VectorDB • Offline",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Tabs
        val tabs = listOf("Interactions", "Semantic Search", "Tasks & Sync", "LLM & Backup")
        ScrollableTabRow(
            selectedTabIndex = activeTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 12.dp,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = activeTab == index,
                    onClick = { onTabSelect(index) },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (activeTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("tab_button_$index")
                )
            }
        }
    }
}

@Composable
fun LlmStatusBanner(
    provider: LlmProvider,
    isAnalyzing: Boolean,
    latestAnalysis: com.example.data.model.AssistantAnalysis?,
    onConfigureClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onConfigureClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = SoftPurple,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (isAnalyzing) {
                    Text(
                        text = "Analyzing context with ${provider.displayName}...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (latestAnalysis != null) {
                    Text(
                        text = "${latestAnalysis.category}: ${latestAnalysis.summary.take(28)}...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "Assistant Model: ${provider.displayName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "Configure",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun FeedTab(
    memories: List<InteractionMemory>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onManualAddClick: () -> Unit,
    onCompleteToggle: (InteractionMemory) -> Unit,
    onSyncCalendar: (InteractionMemory) -> Unit,
    onSyncTasks: (InteractionMemory) -> Unit,
    onDelete: (InteractionMemory) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter pills + Add Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                val categories = listOf("ALL", "TASK", "REMINDER", "INSIGHT", "NOTE")
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onSelectCategory(cat) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("filter_chip_$cat")
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            IconButton(
                onClick = onManualAddClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NeonIndigo)
                    .testTag("manual_add_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Manual text interaction",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ViewAgenda,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No interactions stored yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the glowing mic above or '+' to capture your daily interactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(memories, key = { it.id }) { mem ->
                    MemoryCard(
                        memory = mem,
                        onCompleteToggle = { onCompleteToggle(mem) },
                        onSyncCalendar = { onSyncCalendar(mem) },
                        onSyncTasks = { onSyncTasks(mem) },
                        onDelete = { onDelete(mem) }
                    )
                }
            }
        }
    }
}

@Composable
fun SemanticSearchTab(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    results: List<com.example.data.model.SearchResult>,
    onCompleteToggle: (InteractionMemory) -> Unit,
    onSyncCalendar: (InteractionMemory) -> Unit,
    onSyncTasks: (InteractionMemory) -> Unit,
    onDelete: (InteractionMemory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("semantic_search_input"),
            placeholder = { Text("Offline semantic query, e.g. 'project schedule' or 'meetings'") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = GlowGreen,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Powered by on-device Vector DB embeddings (Zero network required)",
                fontSize = 11.sp,
                color = GlowGreen
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (query.isNotBlank() && results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No matching context found in local vector memory for '$query'",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else if (query.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Search by meaning, concepts, or context",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { it.memory.id }) { item ->
                    MemoryCard(
                        memory = item.memory,
                        similarityScore = item.similarityScore,
                        onCompleteToggle = { onCompleteToggle(item.memory) },
                        onSyncCalendar = { onSyncCalendar(item.memory) },
                        onSyncTasks = { onSyncTasks(item.memory) },
                        onDelete = { onDelete(item.memory) }
                    )
                }
            }
        }
    }
}

@Composable
fun TasksRemindersTab(
    memories: List<InteractionMemory>,
    onCompleteToggle: (InteractionMemory) -> Unit,
    onSyncCalendar: (InteractionMemory) -> Unit,
    onSyncTasks: (InteractionMemory) -> Unit,
    onDelete: (InteractionMemory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Extracted Action Items & Reminders (${memories.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (memories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No tasks or reminders extracted yet.\nSay 'Remind me tomorrow...' or 'Add a task to...'",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(memories, key = { it.id }) { mem ->
                    MemoryCard(
                        memory = mem,
                        onCompleteToggle = { onCompleteToggle(mem) },
                        onSyncCalendar = { onSyncCalendar(mem) },
                        onSyncTasks = { onSyncTasks(mem) },
                        onDelete = { onDelete(mem) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAndBackupTab(
    currentProvider: LlmProvider,
    apiKey: String,
    customModel: String,
    totalMemoriesCount: Int,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSaveLlm: (LlmProvider, String, String) -> Unit,
    onCreateBackup: (String) -> String,
    onRestoreBackup: (String, String) -> Boolean
) {
    var selectedProvider by remember { mutableStateOf(currentProvider) }
    var keyInput by remember { mutableStateOf(apiKey) }
    var modelInput by remember { mutableStateOf(customModel) }
    var providerExpanded by remember { mutableStateOf(false) }

    // Backup state
    var backupPassphrase by remember { mutableStateOf("") }
    var restorePassphrase by remember { mutableStateOf("") }
    var restoreDataInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Appearance card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Theme Appearance",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDarkTheme) "Dark minimalist cyber mode" else "Light minimalist clean mode",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onToggleTheme,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (isDarkTheme) "Switch to Light" else "Switch to Dark",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "LLM & AI Assistant Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Configure any cloud LLM or keep it blank for 100% offline rule-based extraction.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            // Provider dropdown
            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = !providerExpanded }
            ) {
                OutlinedTextField(
                    value = selectedProvider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("LLM Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth()
                        .testTag("provider_dropdown"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false }
                ) {
                    LlmProvider.values().forEach { prov ->
                        DropdownMenuItem(
                            text = { Text(prov.displayName) },
                            onClick = {
                                selectedProvider = prov
                                providerExpanded = false
                            }
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("API Key (${selectedProvider.displayName})") },
                placeholder = { Text("Leave blank for 100% offline mode") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        item {
            OutlinedTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                label = { Text("Model Name (Optional override)") },
                placeholder = { Text("Default: ${selectedProvider.defaultModel}") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        item {
            Button(
                onClick = { onSaveLlm(selectedProvider, keyInput, modelInput) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_llm_btn")
            ) {
                Text(
                    text = "Save AI Configuration",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Privacy & Encrypted Backups",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Export and restore your vector database memories with AES-256-GCM encryption. Total memories: $totalMemoriesCount",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            // Encrypted Backup creation
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Create Encrypted Backup",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Encryption Passphrase") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_passphrase_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (backupPassphrase.length < 4) {
                                Toast.makeText(context, "Passphrase must be at least 4 characters", Toast.LENGTH_SHORT).show()
                            } else {
                                val path = onCreateBackup(backupPassphrase)
                                if (path.isNotBlank()) {
                                    Toast.makeText(context, "Encrypted backup saved!", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonIndigo),
                        modifier = Modifier.fillMaxWidth().testTag("create_backup_btn")
                    ) {
                        Text("Generate AES-256 Encrypted Backup", color = Color.White)
                    }
                }
            }
        }

        item {
            // Restore Backup
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Restore Encrypted Backup",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text("Passphrase") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_passphrase_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreDataInput,
                        onValueChange = { restoreDataInput = it },
                        label = { Text("Encrypted Payload (Base64)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .testTag("restore_payload_input"),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (restorePassphrase.isBlank() || restoreDataInput.isBlank()) {
                                Toast.makeText(context, "Enter passphrase & encrypted payload", Toast.LENGTH_SHORT).show()
                            } else {
                                onRestoreBackup(restoreDataInput, restorePassphrase)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GlowGreen),
                        modifier = Modifier.fillMaxWidth().testTag("restore_backup_btn")
                    ) {
                        Text("Decrypt & Restore Memories", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ManualAddDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Interaction Context", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    text = "Type any voice transcript, thought, task, or reminder. The local vector engine will embed and index it automatically.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("e.g. Schedule meeting with design team every Friday at 3 PM") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("manual_text_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textInput.isNotBlank()) onAdd(textInput)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("dialog_submit_btn")
            ) {
                Text("Process Context", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
