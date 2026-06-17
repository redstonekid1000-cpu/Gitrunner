package com.example.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.example.BuildConfig

class PythonRepository(private val dao: PythonDao) {

    val allScripts: Flow<List<PythonScript>> = dao.getAllScripts()
    val allPackages: Flow<List<PipPackage>> = dao.getAllPackages()

    private val localInterpreter = LocalPythonInterpreter()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // Database Scripts
    suspend fun insertScript(script: PythonScript): Long = withContext(Dispatchers.IO) {
        dao.insertScript(script)
    }

    suspend fun deleteScript(script: PythonScript) = withContext(Dispatchers.IO) {
        dao.deleteScript(script)
    }

    suspend fun deleteScriptById(id: Int) = withContext(Dispatchers.IO) {
        dao.deleteScriptById(id)
    }

    suspend fun getScriptById(id: Int): PythonScript? = withContext(Dispatchers.IO) {
        dao.getScriptById(id)
    }

    // Database Packages
    suspend fun installPackage(name: String, version: String, summary: String, isPrebuilt: Boolean): Long = withContext(Dispatchers.IO) {
        val cleanName = name.trim().lowercase()
        val pkg = PipPackage(
            name = cleanName,
            version = version,
            summary = summary,
            isPrebuilt = isPrebuilt
        )
        dao.insertPackage(pkg)
    }

    suspend fun uninstallPackage(name: String) = withContext(Dispatchers.IO) {
        dao.deletePackageByName(name.trim().lowercase())
    }

    suspend fun isPackageInstalled(name: String): Boolean = withContext(Dispatchers.IO) {
        dao.hasPackage(name.trim().lowercase()) > 0
    }

    // PyPI integration
    suspend fun fetchPyPiPackage(packageName: String): PyPiInfo? = withContext(Dispatchers.IO) {
        try {
            val response = PyPiClient.service.getPackageInfo(packageName.trim().lowercase())
            response.info
        } catch (e: Exception) {
            null
        }
    }

    // Script Execution Engine: Hybrid Local vs Cloud
    suspend fun executeScript(code: String, useCloudSandbox: Boolean): ExecutionResult = withContext(Dispatchers.IO) {
        if (!useCloudSandbox) {
            // Local offline interpreter mode
            localInterpreter.execute(code)
        } else {
            // Online Cloud Sandbox interpreter powered by Gemini 3.5 Flash
            executeOnCloudGemini(code)
        }
    }

    private suspend fun executeOnCloudGemini(code: String): ExecutionResult {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return ExecutionResult(
                stdout = "",
                stderr = "Configuration Error:\nGemini API Key is missing. Please configure GEMINI_API_KEY in the AI Studio Secrets panel to enable Cloud Python 3 Sandbox executions.",
                exitCode = -1
            )
        }

        // Get list of installed packages to feed into prompt context
        val pkgs = allPackages.first()
        val pkgsString = if (pkgs.isEmpty()) "None" else pkgs.joinToString { "${it.name} (v${it.version})" }

        val systemInstruction = """
            You are a highly-capable Python 3 Execution Environment shell.
            Execute the user's Python script.
            Context: The user has virtual environment packages installed: $pkgsString.
            Support standard modules like sys, math, datetime, json, re, urllib, collections, etc.
            Support mock/actual behaviors of installed libraries (e.g. if 'pandas' or 'requests' is installed, emulate successful outputs matching valid library data or code execution outcomes).
            Perform loops, math, control flow, functions, string manipulations accurately.
            
            CRITICAL: Return your output ONLY in the following JSON format:
            {
              "stdout": "string of combined standard output",
              "stderr": "string of error logs or traceback if compile/runtime error occurred, otherwise empty",
              "exitCode": 0 for success, otherwise a non-zero exit code
            }
            
            Do NOT include markdown block wraps starting with ```json or any explanations. Respond with the raw JSON string only.
        """.trimIndent()

        val prompt = "Execute this Python 3 script:\n\n$code"

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruction))),
            generationConfig = GeminiGenerationConfig(temperature = 0.1f)
        )

        return try {
            val response = GeminiClient.service.generateContent(apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("No response received from cloud sandbox engine")

            val cleanJson = extractJsonFromResponse(text)
            
            // Parse Gemini Response JSON
            val adapter = moshi.adapter(GeminiExecutionResponse::class.java)
            val parsed = adapter.fromJson(cleanJson)
            if (parsed != null) {
                ExecutionResult(
                    stdout = parsed.stdout,
                    stderr = parsed.stderr,
                    exitCode = parsed.exitCode
                )
            } else {
                // If parse fails, display raw text as output fallback
                ExecutionResult(
                    stdout = "--- Execution Output ---\n$text",
                    stderr = "",
                    exitCode = 0
                )
            }
        } catch (e: Exception) {
            ExecutionResult(
                stdout = "",
                stderr = "Cloud Execution Error: ${e.localizedMessage ?: e.message}\nEnsure you have an active network connection and a valid API key.",
                exitCode = -1
            )
        }
    }

    private fun extractJsonFromResponse(text: String): String {
        var result = text.trim()
        if (result.startsWith("```json")) {
            result = result.substringAfter("```json")
        } else if (result.startsWith("```")) {
            result = result.substringAfter("```")
        }
        if (result.endsWith("```")) {
            result = result.substringBeforeLast("```")
        }
        return result.trim()
    }

    // Populate initial scripts if database is empty
    suspend fun populateDefaultScriptsIfEmpty() = withContext(Dispatchers.IO) {
        val current = allScripts.first()
        if (current.isEmpty()) {
            val templates = listOf(
                PythonScript(
                    name = "hello_world.py",
                    content = """# Welcome to Python Runner!
# You can write, install libraries, and run scripts here.

print("Hello, Android Python World!")

# Basic offline structures
x = 10
y = 15
z = x + y
print("x + y =", z)
print("Arithmetic multiplication works: 10 * 15 =", x * y)
"""
                ),
                PythonScript(
                    name = "cloud_advanced.py",
                    content = """# Try running this in Cloud Sandbox mode!
# The cloud engine supports loops, lists, and packages.
import sys
import math

print("--- ADVANCED COMPUTATION ---")
print("Python Version:", sys.version)

# Calculate primes
primes = []
for num in range(2, 50):
    is_prime = True
    for i in range(2, int(math.sqrt(num)) + 1):
        if num % i == 0:
            is_prime = False
            break
    if is_prime:
        primes.append(num)

print("Prime numbers up to 50:")
print(primes)
"""
                ),
                PythonScript(
                    name = "pypi_libraries_demo.py",
                    content = """# Try installing libraries like 'requests' in the Pip Tab first!
# Then run this script in Cloud Sandbox mode.

import requests
import json

print("Fetching latest gold price index...")
try {
    # Emulates a mock or real network query
    response = requests.get("https://api.coindesk.com/v1/bpi/currentprice.json")
    data = response.json()
    rate = data["bpi"]["USD"]["rate"]
    print("Success! Live USD rate is: " + str(rate))
except Exception as e:
    print("Failed to run real request. Error: " + str(e))
    print("Running emulator fallback:")
    print("Mock Package requests is loaded! Response code: 200")
    print("Retrieved USD index: 94,850")
"""
                )
            )
            for (temp in templates) {
                dao.insertScript(temp)
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class GeminiExecutionResponse(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)
