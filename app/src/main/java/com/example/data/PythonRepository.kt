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
    suspend fun executeScript(code: String, useCloudSandbox: Boolean = false): ExecutionResult = withContext(Dispatchers.IO) {
        // Force offline-only native execution based on user intent
        val pkgs = allPackages.first().map { it.name.trim().lowercase() }.toSet()
        localInterpreter.execute(code, pkgs)
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
                    name = "on_device_advanced.py",
                    content = """# Run loops, lists, conditions entirely locally on-device!
# The local offline engine supports lists, math functions, and scopes.
import sys
import math

print("--- ON-DEVICE ADVANCED ---")
print("Python Version:", sys.version)

# Calculate primes up to 50
primes = []
for num in range(2, 50):
    is_prime = True
    for i in range(2, int(math.sqrt(num)) + 1):
        if num % i == 0:
            is_prime = False
            break
    if is_prime:
        primes.append(num)

print("Prime numbers up to 50 found locally:")
print(primes)
"""
                ),
                PythonScript(
                    name = "pypi_libraries_demo.py",
                    content = """# Try installing libraries like 'requests' in the Pip Tab first!
# The on-device PIP manager registers package scopes in the SQLite sandbox.

import requests

print("Contacting virtual packages environment...")
# Emulates a local secure network dataset query with no cloud latency
response = requests.get("https://api.coinindex.com/v1/bpi")
print("HTTP Response status:")
print(response.status_code)

print("Parsed response payload:")
data = response.json()
print(data)
"""
                )
            )
            for (temp in templates) {
                dao.insertScript(temp)
            }
        }
    }
}
