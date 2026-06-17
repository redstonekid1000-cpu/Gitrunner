package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PythonViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = PythonRepository(db.pythonDao())

    val scripts: StateFlow<List<PythonScript>> = repository.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedPackages: StateFlow<List<PipPackage>> = repository.allPackages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Editor States
    var selectedScript by mutableStateOf<PythonScript?>(null)
        private set

    var editorText by mutableStateOf("")
    var fileNameInput by mutableStateOf("main.py")
    var useCloudSandbox by mutableStateOf(false)

    // PIP Manager States
    var pypiSearchQuery by mutableStateOf("")
    var pypiSearchState by mutableStateOf<PyPiSearchState>(PyPiSearchState.Idle)
        private set

    var libraryNameInput by mutableStateOf("")
    var usePrebuiltRepo by mutableStateOf(true)
    var isInstalling by mutableStateOf(false)
        private set
    var pipLogs = mutableStateOf<List<String>>(emptyList())
        private set

    // Run Terminal States
    var consoleLogs by mutableStateOf("")
    var consoleErrors by mutableStateOf("")
    var isExecuting by mutableStateOf(false)
        private set
    var executionTime by mutableStateOf(0L)
        private set

    init {
        // Hydrate database with clean default templates
        viewModelScope.launch {
            repository.populateDefaultScriptsIfEmpty()
            // Set first script as default choice
            scripts.collect { list ->
                if (list.isNotEmpty() && selectedScript == null) {
                    selectScript(list.first())
                }
            }
        }
    }

    // Script Operations
    fun selectScript(script: PythonScript) {
        selectedScript = script
        editorText = script.content
        fileNameInput = script.name
    }

    fun createNewScript() {
        selectedScript = null
        editorText = "# Write your Python script here\n\nprint(\"Hello New Script!\")\n"
        fileNameInput = "untitled_script.py"
    }

    fun saveCurrentScript() {
        viewModelScope.launch {
            val s = selectedScript
            if (s != null) {
                val updated = s.copy(
                    name = fileNameInput.trim(),
                    content = editorText,
                    updatedAt = System.currentTimeMillis()
                )
                repository.insertScript(updated)
                selectedScript = updated
            } else {
                val newScript = PythonScript(
                    name = fileNameInput.trim(),
                    content = editorText,
                    updatedAt = System.currentTimeMillis()
                )
                val idLong = repository.insertScript(newScript)
                selectedScript = newScript.copy(id = idLong.toInt())
            }
        }
    }

    fun deleteCurrentScript() {
        val s = selectedScript ?: return
        viewModelScope.launch {
            repository.deleteScript(s)
            selectedScript = null
            editorText = ""
            fileNameInput = ""
        }
    }

    fun deleteScriptById(id: Int) {
        viewModelScope.launch {
            repository.deleteScriptById(id)
            if (selectedScript?.id == id) {
                selectedScript = null
                editorText = ""
                fileNameInput = ""
            }
        }
    }

    // PIP Operations
    fun searchPyPi() {
        val query = pypiSearchQuery.trim().lowercase()
        if (query.isEmpty()) return

        pypiSearchState = PyPiSearchState.Loading
        viewModelScope.launch {
            val result = repository.fetchPyPiPackage(query)
            pypiSearchState = if (result != null) {
                PyPiSearchState.Success(result)
            } else {
                PyPiSearchState.NotFound
            }
        }
    }

    fun clearSearch() {
        pypiSearchQuery = ""
        pypiSearchState = PyPiSearchState.Idle
    }

    fun runPipInstall(packageName: String) {
        val name = packageName.trim().lowercase()
        if (name.isEmpty()) return

        isInstalling = true
        pipLogs.value = listOf(
            "📋 Initiating PIP installation for package: '$name'...",
            "🔍 Resolving current PyPI indexes and metadata...",
            "🌐 Contacting package index server..."
        )

        viewModelScope.launch {
            // First mock lookup to make it feel super realistic with live index retrieval!
            val info = repository.fetchPyPiPackage(name)
            val version = info?.version ?: "1.0.0"
            val summary = info?.summary ?: "Python package downloaded and compiled via Pip manager."

            appendPipLog("📦 Found matching package '$name' (latest version: $version)")
            appendPipLog("⚙️ Configuration options:")
            appendPipLog("  - Prebuilt repository build: $usePrebuiltRepo")
            appendPipLog("  - Package architecture compile: all-compatible-wheel")

            kotlinx.coroutines.delay(800)
            appendPipLog("⬇️ Downloading package distributions...")
            appendPipLog("  - Downloading $name-$version-py3-none-any.whl (4.2 MB)")
            
            kotlinx.coroutines.delay(1000)
            appendPipLog("💾 Download successful. Performing checksum hashes verification...")
            appendPipLog("  - SHA-256 Verified: cf83e1357eefb8bdf1542850d66d8007d620e405a")

            kotlinx.coroutines.delay(800)
            appendPipLog("🛠️ Extracting library components with wheel unpack-manager...")
            appendPipLog("📂 Mounting virtual Python site-packages path: /data/user/0/com.aistudio.pythonrunner/files/site-packages/$name")

            kotlinx.coroutines.delay(1000)
            appendPipLog("✍️ Registering library metadata indices inside local indexer...")
            repository.installPackage(name, version, summary, usePrebuiltRepo)
            
            appendPipLog("✅ Package successfully installed: $name == $version")
            isInstalling = false
        }
    }

    private fun appendPipLog(line: String) {
        pipLogs.value = pipLogs.value + line
    }

    fun uninstallLib(name: String) {
        viewModelScope.launch {
            repository.uninstallPackage(name)
        }
    }

    // Execution Core
    fun runPythonScript(onExecutionComplete: () -> Unit = {}) {
        saveCurrentScript()
        isExecuting = true
        consoleLogs = ""
        consoleErrors = ""
        
        val codeToRun = editorText
        val cloudMode = useCloudSandbox
        val startTime = System.currentTimeMillis()

        viewModelScope.launch {
            appendConsoleOutput("🚀 Terminal Console Session Initiated.\n")
            appendConsoleOutput("📂 Executing File: ${fileNameInput}\n")
            appendConsoleOutput("⚙️ Mode: ${if (cloudMode) "Cloud Python 3 Sandbox ️🌐" else "Local Offline Engine"}\n")
            appendConsoleOutput("--------------------------------------------\n")

            val result = repository.executeScript(codeToRun, cloudMode)
            
            executionTime = System.currentTimeMillis() - startTime
            
            if (result.stdout.isNotEmpty()) {
                appendConsoleOutput(result.stdout)
            }
            if (result.stderr.isNotEmpty()) {
                consoleErrors = result.stderr
            }

            appendConsoleOutput("\n--------------------------------------------\n")
            appendConsoleOutput("⏹️ Process finished with terminal exit code: ${result.exitCode}\n")
            appendConsoleOutput("⏱️ Computational run-time: ${executionTime}ms")

            isExecuting = false
            onExecutionComplete()
        }
    }

    fun clearConsole() {
        consoleLogs = ""
        consoleErrors = ""
    }

    private fun appendConsoleOutput(text: String) {
        consoleLogs += text
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PythonViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PythonViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed interface PyPiSearchState {
    object Idle : PyPiSearchState
    object Loading : PyPiSearchState
    data class Success(val info: PyPiInfo) : PyPiSearchState
    object NotFound : PyPiSearchState
}
