package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PipPackage
import com.example.data.PythonScript
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PythonRunnerApp(
    viewModel: PythonViewModel,
    modifier: Modifier = Modifier
) {
    val scripts by viewModel.scripts.collectAsStateWithLifecycle()
    val installedPackages by viewModel.installedPackages.collectAsStateWithLifecycle()

    var activeMainTab by remember { mutableStateOf(0) } // 0: Editor, 1: Pip Manager, 2: Active Terminal
    var showScriptSelector by remember { mutableStateOf(false) }
    var showNewScriptDialog by remember { mutableStateOf(false) }
    var deleteConfirmDialog by remember { mutableStateOf<PythonScript?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Cyberpunk Dark Color Palette
    val darkBg = Color(0xFF0C0E14)
    val cardBg = Color(0xFF141824)
    val accentCyan = Color(0xFF00E676) // Bright Tech green
    val neonTeal = Color(0xFF00E5FF) // Cyber Cyan
    val terminalGrid = Color(0xFF1B2333)

    Scaffold(
        modifier = modifier.background(darkBg),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cardBg,
                    titleContentColor = Color.White
                ),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Python Shell Logo",
                            tint = neonTeal,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = "PyDroid Runner",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = viewModel.fileNameInput,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = Color.LightGray,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showScriptSelector = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "Select Scripts",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showNewScriptDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Script",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = cardBg,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeMainTab == 0,
                    onClick = { activeMainTab = 0 },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Editor") },
                    label = { Text("Editor") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = neonTeal,
                        selectedTextColor = neonTeal,
                        indicatorColor = terminalGrid
                    )
                )
                NavigationBarItem(
                    selected = activeMainTab == 1,
                    onClick = { activeMainTab = 1 },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Pip Manager") },
                    label = { Text("Pip") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = neonTeal,
                        selectedTextColor = neonTeal,
                        indicatorColor = terminalGrid
                    )
                )
                NavigationBarItem(
                    selected = activeMainTab == 2,
                    onClick = { activeMainTab = 2 },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Console") },
                    label = { Text("Console") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = neonTeal,
                        selectedTextColor = neonTeal,
                        indicatorColor = terminalGrid
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(darkBg)
        ) {
            when (activeMainTab) {
                0 -> EditorTab(
                    viewModel = viewModel,
                    onRunTriggered = {
                        activeMainTab = 2
                    }
                )
                1 -> PipManagerTab(
                    viewModel = viewModel,
                    installedPackages = installedPackages
                )
                2 -> ConsoleTerminalTab(
                    viewModel = viewModel,
                    onClearLogs = { viewModel.clearConsole() },
                    onCopyLogs = {
                        clipboardManager.setText(AnnotatedString(viewModel.consoleLogs))
                    }
                )
            }

            // Script Selection Dialog Drawer
            if (showScriptSelector) {
                AlertDialog(
                    onDismissRequest = { showScriptSelector = false },
                    title = { Text("Saved Python Scripts", color = Color.White) },
                    containerColor = cardBg,
                    text = {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxHeight(0.6f)
                        ) {
                            items(scripts) { script ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = terminalGrid),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectScript(script)
                                            showScriptSelector = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Python Code",
                                                tint = accentCyan,
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = script.name,
                                                    style = TextStyle(
                                                        color = Color.White,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                )
                                                Text(
                                                    text = "Updated: ${java.text.SimpleDateFormat("MMM dd, HH:mm").format(java.util.Date(script.updatedAt))}",
                                                    style = TextStyle(color = Color.Gray, fontSize = 10.sp)
                                                )
                                            }
                                        }
                                        IconButton(onClick = { deleteConfirmDialog = script }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete script",
                                                tint = Color(0xFFFF5252)
                                            )
                                        }
                                    }
                                }
                            }
                            if (scripts.isEmpty()) {
                                item {
                                    Text(
                                        text = "No saved scripts. Click '+' to make one!",
                                        color = Color.LightGray,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showScriptSelector = false }) {
                            Text("Close", color = neonTeal)
                        }
                    }
                )
            }

            // New Script Dialog
            if (showNewScriptDialog) {
                var newName by remember { mutableStateOf("script.py") }
                AlertDialog(
                    onDismissRequest = { showNewScriptDialog = false },
                    title = { Text("Create Python File", color = Color.White) },
                    containerColor = cardBg,
                    text = {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("File Name", color = Color.LightGray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = neonTeal,
                                unfocusedBorderColor = Color.Gray
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.createNewScript()
                                viewModel.fileNameInput = newName
                                viewModel.saveCurrentScript()
                                showNewScriptDialog = false
                                activeMainTab = 0
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = neonTeal)
                        ) {
                            Text("Create", color = Color.Black)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNewScriptDialog = false }) {
                            Text("Cancel", color = Color.LightGray)
                        }
                    }
                )
            }

            // Delete Script Confirmation
            deleteConfirmDialog?.let { scriptToDelete ->
                AlertDialog(
                    onDismissRequest = { deleteConfirmDialog = null },
                    title = { Text("Delete Script?", color = Color.White) },
                    containerColor = cardBg,
                    text = { Text("Are you sure you want to delete '${scriptToDelete.name}'?", color = Color.LightGray) },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteScriptById(scriptToDelete.id)
                                deleteConfirmDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                        ) {
                            Text("Delete", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirmDialog = null }) {
                            Text("Cancel", color = Color.LightGray)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EditorTab(
    viewModel: PythonViewModel,
    onRunTriggered: () -> Unit
) {
    val cardBg = Color(0xFF141824)
    val terminalGrid = Color(0xFF1B2333)
    val neonTeal = Color(0xFF00E5FF)
    val accentCyan = Color(0xFF00E676)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Toolbar controls for script save & execution settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File Name Editor
            OutlinedTextField(
                value = viewModel.fileNameInput,
                onValueChange = { viewModel.fileNameInput = it },
                label = { Text("Filename", color = Color.Gray, fontSize = 10.sp) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, color = Color.White, fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = neonTeal,
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(55.dp)
                    .padding(end = 8.dp)
            )

            // Save Action Button
            Button(
                onClick = { viewModel.saveCurrentScript() },
                colors = ButtonDefaults.buttonColors(containerColor = terminalGrid),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(45.dp)
                    .padding(end = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save file",
                    tint = neonTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save", color = Color.White, fontSize = 12.sp)
            }
        }

        // Execution Info Panel (100% Local Sandbox)
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Mode indicator",
                    tint = neonTeal,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Column {
                    Text(
                        text = "Native Local Interpreter 🔌",
                        style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    )
                    Text(
                        text = "100% Secure on-device execution with local virtualenv package mapping.",
                        style = TextStyle(color = Color.LightGray, fontSize = 10.sp)
                    )
                }
            }
        }

        // The actual high-performance text workspace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(cardBg)
                .border(1.dp, terminalGrid, RoundedCornerShape(8.dp))
        ) {
            val scrollState = rememberScrollState()
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Line Numbers Sidebar
                val lineCount = remember(viewModel.editorText) {
                    viewModel.editorText.lines().size.coerceAtLeast(1)
                }
                
                Column(
                    modifier = Modifier
                        .background(terminalGrid)
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lineCount) {
                        Text(
                            text = "$i",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.End
                            ),
                            modifier = Modifier.height(18.5.dp)
                        )
                    }
                }

                // Interactive Text Field
                BasicTextField(
                    value = viewModel.editorText,
                    onValueChange = { viewModel.editorText = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.5.sp
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .weight(1f),
                    visualTransformation = PythonSyntaxHighlighter()
                )
            }

            // Floated Run Action Button
            FloatingActionButton(
                onClick = {
                    viewModel.runPythonScript()
                    onRunTriggered()
                },
                containerColor = accentCyan,
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run Script")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RUN", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun PipManagerTab(
    viewModel: PythonViewModel,
    installedPackages: List<PipPackage>
) {
    var selectedPipSubTab by remember { mutableStateOf(2) } // Set default directly to 2: "INSTALL" tab as in user screenshot!
    val tabs = listOf("LIBRARIES", "SEARCH LIBRARIES", "INSTALL", "QUICK INSTALL")

    val cardBg = Color(0xFF141824)
    val terminalGrid = Color(0xFF1B2333)
    val neonTeal = Color(0xFF00E5FF)
    val accentCyan = Color(0xFF00E676)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Horizontal Scrollable Scroll indicators for the Pip sub-menus
        ScrollableTabRow(
            selectedTabIndex = selectedPipSubTab,
            containerColor = cardBg,
            contentColor = neonTeal,
            edgePadding = 8.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedPipSubTab == index,
                    onClick = { selectedPipSubTab = index },
                    text = {
                        Text(
                            text = title,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                        )
                    }
                )
            }
        }

        // Render selected tab contents
        Box(modifier = Modifier.weight(1f)) {
            when (selectedPipSubTab) {
                0 -> InstalledLibrariesView(
                    installedPackages = installedPackages,
                    onUninstall = { viewModel.uninstallLib(it) }
                )
                1 -> SearchLibrariesView(
                    viewModel = viewModel,
                    onInstallTriggered = { name ->
                        viewModel.runPipInstall(name)
                        selectedPipSubTab = 2 // Redirect to Install progress tab!
                    }
                )
                2 -> InstallConsoleView(
                    viewModel = viewModel,
                    neonTeal = neonTeal,
                    terminalGrid = terminalGrid,
                    cardBg = cardBg
                )
                3 -> QuickInstallView(
                    viewModel = viewModel,
                    onInstallStarted = {
                        selectedPipSubTab = 2 // Switch tab index to installation console!
                    }
                )
            }
        }
    }
}

@Composable
fun InstalledLibrariesView(
    installedPackages: List<PipPackage>,
    onUninstall: (String) -> Unit
) {
    val cardBg = Color(0xFF141824)
    val terminalGrid = Color(0xFF1B2333)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (installedPackages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "No packages",
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No external python libraries installed.", color = Color.Gray, fontSize = 13.sp)
                        Text("Go to INSTALL or QUICK INSTALL to obtain Pip libraries.", color = Color.DarkGray, fontSize = 11.sp)
                    }
                }
            }
        }

        items(installedPackages) { pkg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = pkg.name,
                                style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = terminalGrid,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "v${pkg.version}",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = pkg.summary, color = Color.LightGray, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onUninstall(pkg.name) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Uninstall Package",
                            tint = Color(0xFFFF5252)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchLibrariesView(
    viewModel: PythonViewModel,
    onInstallTriggered: (String) -> Unit
) {
    val cardBg = Color(0xFF141824)
    val terminalGrid = Color(0xFF1B2333)
    val neonTeal = Color(0xFF00E5FF)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search Input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = viewModel.pypiSearchQuery,
                onValueChange = { viewModel.pypiSearchQuery = it },
                label = { Text("Search PyPI Package...", color = Color.LightGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = neonTeal,
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.searchPyPi() },
                colors = ButtonDefaults.buttonColors(containerColor = neonTeal)
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Query PyPI", tint = Color.Black)
            }
        }

        // Search Outcomes State Selector
        when (val state = viewModel.pypiSearchState) {
            is PyPiSearchState.Idle -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Search above to explore real packages catalog indexes live on PyPI!", color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
            is PyPiSearchState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = neonTeal)
                }
            }
            is PyPiSearchState.NotFound -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("⚠️ Package not found on PyPI registers. Double-check the exact library name.", color = Color.Red, textAlign = TextAlign.Center)
                }
            }
            is PyPiSearchState.Success -> {
                val p = state.info
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.name, style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp))
                            Button(
                                onClick = { onInstallTriggered(p.name) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                            ) {
                                Text("INSTALL", color = Color.Black, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Latest Stable Version: ${p.version}", color = neonTeal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Divider(color = terminalGrid, modifier = Modifier.padding(vertical = 8.dp))
                        Text(p.summary ?: "No official summary provided.", color = Color.LightGray, fontSize = 14.sp)
                        if (p.author != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Author: ${p.author}", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstallConsoleView(
    viewModel: PythonViewModel,
    neonTeal: Color,
    terminalGrid: Color,
    cardBg: Color
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Screenshot replica interface layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = viewModel.libraryNameInput,
                onValueChange = { viewModel.libraryNameInput = it },
                label = { Text("Library name", color = Color.LightGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = neonTeal,
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    viewModel.runPipInstall(viewModel.libraryNameInput)
                },
                enabled = viewModel.libraryNameInput.trim().isNotEmpty() && !viewModel.isInstalling,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A4D54),
                    disabledContainerColor = Color(0xFF26282B)
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("INSTALL", color = Color.White)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = viewModel.usePrebuiltRepo,
                onCheckedChange = { viewModel.usePrebuiltRepo = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFFFFC107),
                    uncheckedColor = Color.Gray
                )
            )
            Text(
                text = "Use prebuilt libraries repository",
                color = Color.White,
                fontSize = 14.sp
            )
        }

        // Live Installation Progress Engine logs output
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF090A0E))
                .border(2.dp, terminalGrid, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            val logLines = viewModel.pipLogs.value
            if (logLines.isEmpty() && !viewModel.isInstalling) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ready",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Pip Package compilation server is online & idle.",
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (viewModel.isInstalling) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = neonTeal, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Installing dependencies...", color = neonTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(color = neonTeal, trackColor = terminalGrid, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logLines) { log ->
                            Text(
                                text = log,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = if (log.startsWith("✅")) Color(0xFF00E676) else Color.LightGray,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickInstallView(
    viewModel: PythonViewModel,
    onInstallStarted: () -> Unit
) {
    val cardBg = Color(0xFF141824)
    val neonTeal = Color(0xFF00E5FF)

    val popularLibs = listOf(
        Pair("requests", "Excellent HTTP parsing for communicating with public Web REST APIs."),
        Pair("numpy", "Advanced multidimensional matrix arrays calculations & science logic."),
        Pair("pandas", "Interactive high-performance structures loading, CSV parsers, and data analysis."),
        Pair("beautifulsoup4", "Comprehensive web-scraping parser to analyze html content hierarchies."),
        Pair("sympy", "Algorithmic computer algebra calculations, algebra solvers, and symbolic math."),
        Pair("jinja2", "Modern, full-featured Python rendering templates parser engine."),
        Pair("pillow", "Image loading pipelines, file conversions and drawings context."),
        Pair("matplotlib", "Virtual science drawings and standard graphs plots emulator.")
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(popularLibs) { (name, description) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(name, style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(description, color = Color.Gray, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.runPipInstall(name)
                            onInstallStarted()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = neonTeal),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text("QUICK INSTALL", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleTerminalTab(
    viewModel: PythonViewModel,
    onClearLogs: () -> Unit,
    onCopyLogs: () -> Unit
) {
    val darkBg = Color(0xFF0C0E14)
    val cardBg = Color(0xFF141824)
    val terminalGrid = Color(0xFF1B2333)
    val neonTeal = Color(0xFF00E5FF)
    val codeKeyword = Color(0xFFFF5252)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Console control action button tray
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onClearLogs,
                colors = ButtonDefaults.buttonColors(containerColor = terminalGrid),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Clear logs", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear", fontSize = 12.sp)
            }
            Button(
                onClick = onCopyLogs,
                colors = ButtonDefaults.buttonColors(containerColor = terminalGrid),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Copy text logs", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Logs", fontSize = 12.sp)
            }
        }

        // Output Display Box Window
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF050608))
                .border(1.5.dp, terminalGrid, RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                if (viewModel.isExecuting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        CircularProgressIndicator(color = neonTeal, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sandbox Python compilation computing in progress...",
                            color = neonTeal,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }

                // Stdout print elements
                Text(
                    text = viewModel.consoleLogs,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                )

                // Stderr print elements highlighted in error states red
                if (viewModel.consoleErrors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = viewModel.consoleErrors,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace, color = codeKeyword,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        )
                    )
                }
            }
        }
    }
}
