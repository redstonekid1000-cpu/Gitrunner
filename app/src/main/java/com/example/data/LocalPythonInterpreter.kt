package com.example.data

import kotlin.math.*

class LocalPythonInterpreter {

    fun execute(code: String): ExecutionResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val variables = mutableMapOf<String, Any>()

        val lines = code.lines()
        var lineIndex = 0

        try {
            while (lineIndex < lines.size) {
                val rawLine = lines[lineIndex]
                val trimmed = rawLine.trim()

                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    lineIndex++
                    continue
                }

                // Standard print: print(...)
                if (trimmed.startsWith("print(") && trimmed.endsWith(")")) {
                    val inner = trimmed.substring(6, trimmed.length - 1).trim()
                    val output = evaluateText(inner, variables)
                    stdout.append(output).append("\n")
                }
                // Variable assignment: name = expression
                else if (trimmed.contains("=") && !trimmed.startsWith("if") && !trimmed.startsWith("for") && !trimmed.startsWith("def") && !trimmed.startsWith("while")) {
                    val parts = trimmed.split("=", limit = 2)
                    val varName = parts[0].trim()
                    val expr = parts[1].trim()

                    if (isValidVariableName(varName)) {
                        val value = evaluateExpression(expr, variables)
                        variables[varName] = value
                    } else {
                        stderr.append("SyntaxError: invalid variable name '$varName' on line ${lineIndex + 1}\n")
                    }
                }
                // Custom Warning for more advanced constructs structure so that we don't crash
                else if (trimmed.startsWith("def ") || trimmed.startsWith("class ") || trimmed.startsWith("import ") || trimmed.startsWith("from ") || trimmed.startsWith("for ") || trimmed.startsWith("while ") || trimmed.startsWith("if ")) {
                    stdout.append("💡 Offline mode ran: found advanced statement '${trimmed.take(15)}...'\n")
                    stdout.append("⚠️ Offline Interpreter runs basic variables & print statements.\n")
                    stdout.append("🌐 For complete Python 3 support, pip libraries & loops, toggle 'Cloud Sandbox' execution mode!\n\n")
                    lineIndex++
                    continue
                } else {
                    // Unknown or unsupported offline line
                    // Don't error out hard, print warning
                    stdout.append("💡 Offline note: parsed instruction: '$trimmed'\n")
                }

                lineIndex++
            }
        } catch (e: Exception) {
            stderr.append("RuntimeError: ${e.message} on line ${lineIndex + 1}\n")
        }

        return ExecutionResult(
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            exitCode = if (stderr.isNotEmpty()) 1 else 0
        )
    }

    private fun isValidVariableName(name: String): Boolean {
        if (name.isEmpty()) return false
        val first = name[0]
        if (!first.isLetter() && first != '_') return false
        return name.all { it.isLetterOrDigit() || it == '_' }
    }

    private fun evaluateText(expr: String, variables: Map<String, Any>): String {
        // Simple double quote/single quote text extractor
        if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
            return expr.substring(1, expr.length - 1)
        }

        // Concatenation: e.g. "Answer: " + str(x) or "Answer: " + x
        if (expr.contains("+")) {
            val parts = expr.split("+")
            return parts.joinToString("") { evaluateText(it.trim(), variables) }
        }

        // Variable lookup
        if (variables.containsKey(expr)) {
            return variables[expr].toString()
        }

        // Check if raw digit / boolean
        return when {
            expr == "True" -> "True"
            expr == "False" -> "False"
            else -> {
                try {
                    val res = evaluateExpression(expr, variables)
                    res.toString()
                } catch (e: Exception) {
                    expr // Fallback to printing raw content
                }
            }
        }
    }

    private fun evaluateExpression(expr: String, variables: Map<String, Any>): Any {
        val trimmed = expr.trim()
        if (trimmed == "True") return true
        if (trimmed == "False") return false

        // Check for string literal
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // Numeric literal
        trimmed.toIntOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }

        // Variable lookup
        variables[trimmed]?.let { return it }

        // Simple arithmetic evaluation (only handles local adding/subtracting for simple variables/digits)
        if (trimmed.contains("+") || trimmed.contains("-") || trimmed.contains("*") || trimmed.contains("/")) {
            // Very simple math parser
            return try {
                parseSimpleMath(trimmed, variables)
            } catch (e: Exception) {
                trimmed
            }
        }

        return trimmed
    }

    private fun parseSimpleMath(expr: String, variables: Map<String, Any>): Double {
        // Resolve variable names in expr
        var resolvedExpr = expr
        variables.forEach { (name, value) ->
            resolvedExpr = resolvedExpr.replace(name, value.toString())
        }

        // Run basic evaluation of + or - or * or /
        // Split by + and -
        val terms = resolvedExpr.split(Regex("(?=[+-])|(?<=[+-])"))
        var total = 0.0
        var currentOp = "+"

        for (term in terms) {
            val t = term.trim()
            if (t == "+" || t == "-") {
                currentOp = t
            } else if (t.isNotEmpty()) {
                // Check if it has internal multiplication or division
                val factorVal = parseFactors(t)
                if (currentOp == "+") {
                    total += factorVal
                } else {
                    total -= factorVal
                }
            }
        }
        return total
    }

    private fun parseFactors(expr: String): Double {
        val factors = expr.split(Regex("(?=[*/])|(?<=[*/])"))
        var total = 1.0
        var currentOp = "*"

        for (factor in factors) {
            val f = factor.trim()
            if (f == "*" || f == "/") {
                currentOp = f
            } else if (f.isNotEmpty()) {
                val value = f.toDoubleOrNull() ?: 0.0
                if (currentOp == "*") {
                    total *= value
                } else {
                    if (value != 0.0) {
                        total /= value
                    }
                }
            }
        }
        return total
    }
}

data class ExecutionResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)
