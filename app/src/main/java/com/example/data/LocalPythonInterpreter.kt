package com.example.data

import kotlin.math.*
import java.util.regex.Pattern

class LocalPythonInterpreter {

    fun execute(code: String, installedPackages: Set<String>): ExecutionResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        
        try {
            val lines = code.lines()
            val statements = parse(lines)
            val ctx = EvaluationContext()
            
            executeBlock(statements, ctx, stdout, stderr, installedPackages)
        } catch (e: Exception) {
            stderr.append("Parser Error: ${e.message}\n")
        }

        return ExecutionResult(
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            exitCode = if (stderr.isNotEmpty()) 1 else 0
        )
    }

    private fun getIndent(line: String): Int {
        var count = 0
        for (char in line) {
            if (char == ' ') count++
            else if (char == '\t') count += 4
            else break
        }
        return count
    }

    private fun collectBlock(lines: List<String>, startIndex: Int, headerIndent: Int): List<String> {
        val body = mutableListOf<String>()
        var j = startIndex
        while (j < lines.size) {
            val nextLine = lines[j]
            if (nextLine.trim().isEmpty()) {
                body.add(nextLine)
                j++
                continue
            }
            if (getIndent(nextLine) > headerIndent) {
                body.add(nextLine)
                j++
            } else {
                break
            }
        }
        return body
    }

    private fun parse(lines: List<String>): List<Statement> {
        val result = mutableListOf<Statement>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                i++
                continue
            }
            
            val indent = getIndent(line)
            
            // Function Definition
            if (trimmed.startsWith("def ") && trimmed.endsWith(":")) {
                val header = trimmed.removePrefix("def ").removeSuffix(":")
                val name = header.substringBefore("(").trim()
                val paramsStr = header.substringAfter("(").substringBefore(")")
                val params = if (paramsStr.trim().isEmpty()) emptyList() else paramsStr.split(",").map { it.trim() }
                
                val bodyLines = collectBlock(lines, i + 1, indent)
                i += bodyLines.size + 1
                result.add(Statement.Def(name, params, parse(bodyLines)))
                continue
            }
            
            // If Condition
            if (trimmed.startsWith("if ") && trimmed.endsWith(":")) {
                val condition = trimmed.removePrefix("if ").removeSuffix(":")
                val bodyLines = collectBlock(lines, i + 1, indent)
                i += bodyLines.size + 1
                
                val elifs = mutableListOf<Pair<String, List<Statement>>>()
                var elseBody: List<Statement>? = null
                
                while (i < lines.size) {
                    val nextLine = lines[i]
                    val nextTrimmed = nextLine.trim()
                    if (nextTrimmed.isEmpty()) {
                        i++
                        continue
                    }
                    if (getIndent(nextLine) == indent) {
                        if (nextTrimmed.startsWith("elif ") && nextTrimmed.endsWith(":")) {
                            val elifCond = nextTrimmed.removePrefix("elif ").removeSuffix(":")
                            val elifBodyLines = collectBlock(lines, i + 1, indent)
                            elifs.add(Pair(elifCond, parse(elifBodyLines)))
                            i += elifBodyLines.size + 1
                        } else if (nextTrimmed.startsWith("else:") || nextTrimmed.startsWith("else :")) {
                            val elseBodyLines = collectBlock(lines, i + 1, indent)
                            elseBody = parse(elseBodyLines)
                            i += elseBodyLines.size + 1
                            break
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
                result.add(Statement.If(condition, parse(bodyLines), elifs, elseBody))
                continue
            }
            
            // For Loop
            if (trimmed.startsWith("for ") && trimmed.endsWith(":")) {
                val header = trimmed.removePrefix("for ").removeSuffix(":")
                val loopVar = header.substringBefore(" in ").trim()
                val iterableExpr = header.substringAfter(" in ").trim()
                
                val bodyLines = collectBlock(lines, i + 1, indent)
                i += bodyLines.size + 1
                result.add(Statement.For(loopVar, iterableExpr, parse(bodyLines)))
                continue
            }
            
            // While Loop
            if (trimmed.startsWith("while ") && trimmed.endsWith(":")) {
                val cond = trimmed.removePrefix("while ").removeSuffix(":")
                val bodyLines = collectBlock(lines, i + 1, indent)
                i += bodyLines.size + 1
                result.add(Statement.While(cond, parse(bodyLines)))
                continue
            }
            
            // Imports
            if (trimmed.startsWith("import ")) {
                val mod = trimmed.removePrefix("import ").trim()
                result.add(Statement.Import(mod))
                i++
                continue
            }
            if (trimmed.startsWith("from ") && trimmed.contains(" import ")) {
                val parts = trimmed.removePrefix("from ").split(" import ")
                val mod = parts[0].trim()
                result.add(Statement.Import(mod))
                i++
                continue
            }
            if (trimmed.startsWith("print(") && trimmed.endsWith(")")) {
                val expr = trimmed.substring(6, trimmed.length - 1)
                result.add(Statement.Print(expr))
                i++
                continue
            }
            if (trimmed.startsWith("return")) {
                val expr = trimmed.removePrefix("return").trim().ifEmpty { null }
                result.add(Statement.Return(expr))
                i++
                continue
            }
            
            // Assignments
            if (trimmed.contains("=") && !trimmed.startsWith("if") && !trimmed.startsWith("for") && !trimmed.startsWith("while")) {
                val parts = trimmed.split("=", limit = 2)
                val left = parts[0].trim()
                val right = parts[1].trim()
                
                if (left.contains("[") && left.endsWith("]")) {
                    val target = left.substringBefore("[").trim()
                    val indexExpr = left.substringAfter("[").removeSuffix("]").trim()
                    result.add(Statement.Assignment(target, indexExpr, right))
                } else {
                    result.add(Statement.Assignment(left, null, right))
                }
                i++
                continue
            }
            
            // Method calls
            if (trimmed.contains(".") && trimmed.endsWith(")")) {
                val obj = trimmed.substringBefore(".")
                val rest = trimmed.substringAfter(".")
                val mName = rest.substringBefore("(")
                val args = rest.substringAfter("(").removeSuffix(")")
                result.add(Statement.MethodCall(obj, mName, args))
                i++
                continue
            }

            result.add(Statement.RawExpression(trimmed))
            i++
        }
        return result
    }

    private fun evaluate(expr: String, ctx: EvaluationContext): Any? {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "True" || trimmed == "true") return true
        if (trimmed == "False" || trimmed == "false") return false
        if (trimmed == "None") return null

        // String literals
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // List literal [1, 2, 3]
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            if (inner.isEmpty()) return ArrayList<Any>()
            val parts = splitExpressionList(inner)
            val list = ArrayList<Any>()
            for (part in parts) {
                val v = evaluate(part, ctx)
                if (v != null) list.add(v)
            }
            return list
        }

        // Dict literal {"a": 1, "b": 2}
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            val map = LinkedHashMap<String, Any>()
            if (inner.isEmpty()) return map
            val parts = splitExpressionList(inner)
            for (part in parts) {
                if (part.contains(":")) {
                    val keyStr = part.substringBefore(":").trim()
                    val valStr = part.substringAfter(":").trim()
                    val k = evaluate(keyStr, ctx)?.toString() ?: ""
                    val v = evaluate(valStr, ctx)
                    if (v != null) {
                        map[k] = v
                    }
                }
            }
            return map
        }

        // Math calculations
        if (hasMathAtoms(trimmed)) {
            return calculateMath(trimmed, ctx)
        }

        // String interpolation
        if (trimmed.startsWith("f\"") && trimmed.endsWith("\"")) {
            return interpolateFString(trimmed.substring(2, trimmed.length - 1), ctx)
        }

        // Function Calls
        if (trimmed.contains("(") && trimmed.endsWith(")")) {
            val name = trimmed.substringBefore("(").trim()
            val argsStr = trimmed.substringAfter("(").removeSuffix(")").trim()
            return executeCall(name, argsStr, ctx)
        }

        // Index lookup: list[idx] or dict[key]
        if (trimmed.contains("[") && trimmed.endsWith("]")) {
            val objName = trimmed.substringBefore("[").trim()
            val indexStr = trimmed.substringAfter("[").removeSuffix("]").trim()
            val obj = ctx.variables[objName]
            val index = evaluate(indexStr, ctx)
            if (obj is List<*>) {
                val idx = (index as? Number)?.toInt() ?: 0
                if (idx in obj.indices) return obj[idx]
            }
            if (obj is Map<*, *>) {
                return obj[index]
            }
        }

        // Variable lookup
        if (ctx.variables.containsKey(trimmed)) {
            return ctx.variables[trimmed]
        }

        // Dot properties
        if (trimmed.contains(".")) {
            val mod = trimmed.substringBefore(".")
            val prop = trimmed.substringAfter(".")
            if (ctx.importedModules.contains(mod)) {
                when (mod) {
                    "sys" -> {
                        if (prop == "version") return "3.11.2 (Local Interpreter Standard Mode)"
                        if (prop == "platform") return "android-local"
                    }
                    "math" -> {
                        if (prop == "pi" || prop == "PI") return PI
                        if (prop == "e" || prop == "E") return E
                    }
                }
            }
        }

        trimmed.toIntOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }

        return trimmed
    }

    private fun splitExpressionList(expr: String): List<String> {
        val res = mutableListOf<String>()
        var depth = 0
        var braceDepth = 0
        var current = StringBuilder()
        var inString = false
        var stringChar = '"'
        
        for (char in expr) {
            if (inString) {
                current.append(char)
                if (char == stringChar) {
                    inString = false
                }
            } else {
                when (char) {
                    '"', '\'' -> {
                        inString = true
                        stringChar = char
                        current.append(char)
                    }
                    '[', '(' -> {
                        depth++
                        current.append(char)
                    }
                    ']', ')' -> {
                        depth--
                        current.append(char)
                    }
                    '{' -> {
                        braceDepth++
                        current.append(char)
                    }
                    '}' -> {
                        braceDepth--
                        current.append(char)
                    }
                    ',' -> {
                        if (depth == 0 && braceDepth == 0) {
                            res.add(current.toString().trim())
                            current = StringBuilder()
                        } else {
                            current.append(char)
                        }
                    }
                    else -> current.append(char)
                }
            }
        }
        if (current.isNotEmpty()) {
            res.add(current.toString().trim())
        }
        return res
    }

    private fun interpolateFString(str: String, ctx: EvaluationContext): String {
        val builder = StringBuilder()
        var i = 0
        while (i < str.length) {
            if (str[i] == '{') {
                i++
                val inner = StringBuilder()
                while (i < str.length && str[i] != '}') {
                    inner.append(str[i])
                    i++
                }
                if (i < str.length) i++
                val evaluated = evaluate(inner.toString(), ctx)
                builder.append(evaluated ?: "")
            } else {
                builder.append(str[i])
                i++
            }
        }
        return builder.toString()
    }

    private fun executeCall(name: String, argsStr: String, ctx: EvaluationContext): Any? {
        val args = if (argsStr.isEmpty()) emptyList() else splitExpressionList(argsStr)
        
        when (name) {
            "range" -> {
                val list = ArrayList<Int>()
                if (args.size == 1) {
                    val limit = (evaluate(args[0], ctx) as? Number)?.toInt() ?: 0
                    for (v in 0 until limit) list.add(v)
                } else if (args.size == 2) {
                    val start = (evaluate(args[0], ctx) as? Number)?.toInt() ?: 0
                    val end = (evaluate(args[1], ctx) as? Number)?.toInt() ?: 0
                    for (v in start until end) list.add(v)
                }
                return list
            }
            "len" -> {
                if (args.isNotEmpty()) {
                    val obj = evaluate(args[0], ctx)
                    return when (obj) {
                        is List<*> -> obj.size
                        is Map<*, *> -> obj.size
                        is String -> obj.length
                        else -> 0
                    }
                }
                return 0
            }
            "str" -> {
                return if (args.isNotEmpty()) evaluate(args[0], ctx)?.toString() ?: "" else ""
            }
            "int" -> {
                if (args.isNotEmpty()) {
                    val evaluated = evaluate(args[0], ctx)
                    return (evaluated as? Number)?.toInt() ?: evaluated?.toString()?.toIntOrNull() ?: 0
                }
                return 0
            }
            "float" -> {
                if (args.isNotEmpty()) {
                    val evaluated = evaluate(args[0], ctx)
                    return (evaluated as? Number)?.toDouble() ?: evaluated?.toString()?.toDoubleOrNull() ?: 0.0
                }
                return 0.0
            }
            "round" -> {
                if (args.isNotEmpty()) {
                    val evaluated = evaluate(args[0], ctx)
                    return (evaluated as? Number)?.toDouble()?.let { round(it) } ?: 0.0
                }
                return 0.0
            }
        }

        if (name.contains(".")) {
            val mod = name.substringBefore(".")
            val func = name.substringAfter(".")
            if (ctx.importedModules.contains(mod)) {
                when (mod) {
                    "math" -> {
                        val firstArg = if (args.isNotEmpty()) (evaluate(args[0], ctx) as? Number)?.toDouble() ?: 0.0 else 0.0
                        return when (func) {
                            "sqrt" -> sqrt(firstArg)
                            "sin" -> sin(firstArg)
                            "cos" -> cos(firstArg)
                            "tan" -> tan(firstArg)
                            "log" -> log(firstArg, E)
                            "abs" -> abs(firstArg)
                            else -> 0.0
                        }
                    }
                    "requests" -> {
                        if (func == "get") {
                            val url = if (args.isNotEmpty()) evaluate(args[0], ctx)?.toString() ?: "" else ""
                            return RequestsResponse(url)
                        }
                    }
                    "numpy" -> {
                        if (func == "array") {
                            val listArg = if (args.isNotEmpty()) evaluate(args[0], ctx) as? List<*> else null
                            return NumpyArrayMock(listArg ?: emptyList<Any>())
                        }
                    }
                    "pandas" -> {
                        if (func == "DataFrame") {
                            val dictArg = if (args.isNotEmpty()) evaluate(args[0], ctx) as? Map<*, *> else null
                            return PandasDataFrameMock(dictArg ?: emptyMap<String, Any>())
                        }
                    }
                }
            }
        }

        val def = ctx.functions[name]
        if (def != null) {
            val childCtx = EvaluationContext()
            childCtx.variables.putAll(ctx.variables)
            childCtx.functions.putAll(ctx.functions)
            childCtx.importedModules.addAll(ctx.importedModules)

            for (idx in def.params.indices) {
                if (idx < args.size) {
                    val argVal = evaluate(args[idx], ctx)
                    if (argVal != null) childCtx.variables[def.params[idx]] = argVal
                }
            }
            val outStdout = StringBuilder()
            val outStderr = StringBuilder()
            val retVal = executeBlock(def.body, childCtx, outStdout, outStderr, emptySet())
            return retVal
        }

        return null
    }

    private fun executeBlock(
        statements: List<Statement>, 
        ctx: EvaluationContext, 
        stdout: StringBuilder, 
        stderr: StringBuilder, 
        installedPackages: Set<String>
    ): Any? {
        var ip = 0
        while (ip < statements.size) {
            val node = statements[ip]
            try {
                when (node) {
                    is Statement.Import -> {
                        val mod = node.module.trim().lowercase()
                        val standardLibs = setOf("sys", "math", "os", "datetime", "json", "random")
                        if (standardLibs.contains(mod)) {
                            ctx.importedModules.add(mod)
                        } else {
                            if (installedPackages.contains(mod)) {
                                ctx.importedModules.add(mod)
                                stdout.append("⚡ [Pip virtualenv]: Loaded Package module '$mod' successfully.\n")
                            } else {
                                stderr.append("ModuleNotFoundError: No package named '$mod' installed. Try installing it first in the Pip Tab!\n")
                                return null
                            }
                        }
                    }
                    is Statement.Print -> {
                        val evaluated = evaluate(node.expr, ctx)
                        stdout.append(evaluated ?: "").append("\n")
                    }
                    is Statement.Assignment -> {
                        val value = evaluate(node.expr, ctx)
                        if (value != null) {
                            if (node.indexExpr != null) {
                                val listObj = ctx.variables[node.target]
                                val idx = (evaluate(node.indexExpr, ctx) as? Number)?.toInt() ?: 0
                                if (listObj is ArrayList<*>) {
                                    if (idx in listObj.indices) {
                                        @Suppress("UNCHECKED_CAST") (listObj as MutableList<Any>)[idx] = value
                                    }
                                } else if (listObj is LinkedHashMap<*, *>) {
                                    val key = evaluate(node.indexExpr, ctx)?.toString() ?: ""
                                    @Suppress("UNCHECKED_CAST")
                                    (listObj as LinkedHashMap<String, Any>)[key] = value
                                }
                            } else {
                                ctx.variables[node.target] = value
                            }
                        }
                    }
                    is Statement.If -> {
                        val conditionTrue = evaluateCondition(node.condition, ctx)
                        if (conditionTrue) {
                            val retValue = executeBlock(node.body, ctx, stdout, stderr, installedPackages)
                            if (retValue != null) return retValue
                        } else {
                            var executedElif = false
                            for (elif in node.elifs) {
                                if (evaluateCondition(elif.first, ctx)) {
                                    val retValue = executeBlock(elif.second, ctx, stdout, stderr, installedPackages)
                                    if (retValue != null) return retValue
                                    executedElif = true
                                    break
                                }
                            }
                            if (!executedElif && node.elseBody != null) {
                                val retValue = executeBlock(node.elseBody, ctx, stdout, stderr, installedPackages)
                                if (retValue != null) return retValue
                            }
                        }
                    }
                    is Statement.For -> {
                        val rangeObj = evaluate(node.iterableExpr, ctx)
                        val itemsToIterate = when (rangeObj) {
                            is List<*> -> rangeObj
                            is String -> rangeObj.map { it.toString() }
                            is Map<*, *> -> rangeObj.keys.toList()
                            else -> emptyList<Any>()
                        }
                        
                        for (item in itemsToIterate) {
                            if (item != null) ctx.variables[node.loopVar] = item
                            val retValue = executeBlock(node.body, ctx, stdout, stderr, installedPackages)
                            if (retValue != null) return retValue
                        }
                    }
                    is Statement.While -> {
                        var counter = 0
                        while (evaluateCondition(node.condition, ctx)) {
                            counter++
                            if (counter > 1000) {
                                stderr.append("RuntimeError: Execution timed out (Safety limits exceeded > 1000 iterations)\n")
                                break
                            }
                            val retValue = executeBlock(node.body, ctx, stdout, stderr, installedPackages)
                            if (retValue != null) return retValue
                        }
                    }
                    is Statement.Def -> {
                        ctx.functions[node.name] = node
                    }
                    is Statement.Return -> {
                        return if (node.expr != null) evaluate(node.expr, ctx) else Unit
                    }
                    is Statement.MethodCall -> {
                        executeMethodCall(node.objectName, node.methodName, node.argsExpr, ctx, stdout)
                    }
                    is Statement.RawExpression -> {
                        evaluate(node.expr, ctx)
                    }
                }
            } catch (e: Exception) {
                stderr.append("RuntimeError: ${e.message} on execution statement '${node::class.simpleName}'\n")
                return null
            }
            ip++
        }
        return null
    }

    private fun executeMethodCall(
        objName: String, 
        method: String, 
        argsStr: String, 
        ctx: EvaluationContext,
        stdout: StringBuilder
    ) {
        val obj = ctx.variables[objName]
        val argObj = evaluate(argsStr, ctx)
        
        val list = obj as? MutableList<Any>
        if (list != null) {
            if (method == "append" && argObj != null) {
                list.add(argObj)
            } else if (method == "pop") {
                if (list.isNotEmpty()) list.removeAt(list.size - 1)
            } else if (method == "clear") {
                list.clear()
            }
        } else if (obj == "plt" || objName == "plt") {
            if (ctx.importedModules.contains("matplotlib") || ctx.importedModules.contains("plt")) {
                if (method == "plot") {
                    stdout.append("📈 [Matplotlib]: Drawing virtual chart plot lines. Applied metrics values: ($argsStr)\n")
                } else if (method == "show") {
                    stdout.append("🖼️ [Matplotlib Showcase Image Generated Successfully in client cache files]: /site-packages/matplotlib/render.png\n")
                }
            }
        } else if (obj is LinkedHashMap<*, *>) {
            if (method == "update" && argObj is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val targetMap = obj as? LinkedHashMap<Any, Any>
                if (targetMap != null) {
                    argObj.forEach { (k, v) ->
                        if (k != null && v != null) {
                            targetMap[k] = v
                        }
                    }
                }
            }
        }
    }

    private fun evaluateCondition(cond: String, ctx: EvaluationContext): Boolean {
        val trimmed = cond.trim()
        if (trimmed == "True" || trimmed == "true") return true
        if (trimmed == "False" || trimmed == "false") return false
        
        val ops = listOf("==", "!=", "<=", ">=", "<", ">")
        for (op in ops) {
            if (trimmed.contains(op)) {
                val leftStr = trimmed.substringBefore(op).trim()
                val rightStr = trimmed.substringAfter(op).trim()
                val left = evaluate(leftStr, ctx)
                val right = evaluate(rightStr, ctx)
                
                if (left is Number && right is Number) {
                    val lValue = left.toDouble()
                    val rValue = right.toDouble()
                    return when (op) {
                        "==" -> lValue == rValue
                        "!=" -> lValue != rValue
                        "<" -> lValue < rValue
                        ">" -> lValue > rValue
                        "<=" -> lValue <= rValue
                        ">=" -> lValue >= rValue
                        else -> false
                    }
                } else {
                    val lValue = left?.toString() ?: ""
                    val rValue = right?.toString() ?: ""
                    return when (op) {
                        "==" -> lValue == rValue
                        "!=" -> lValue != rValue
                        else -> false
                    }
                }
            }
        }
        
        val singleRes = evaluate(trimmed, ctx)
        if (singleRes is Boolean) return singleRes
        if (singleRes is Number) return singleRes.toDouble() != 0.0
        if (singleRes is String) return singleRes.isNotEmpty()
        if (singleRes is List<*>) return singleRes.isNotEmpty()
        return singleRes != null
    }

    private fun hasMathAtoms(expr: String): Boolean {
        val characters = setOf('+', '-', '*', '/', '%', '(', ')')
        return characters.any { expr.contains(it) } && !expr.startsWith("[") && !expr.startsWith("{") && !expr.contains("requests.") && !expr.contains("\"") && !expr.contains("'")
    }

    private fun calculateMath(expr: String, ctx: EvaluationContext): Double {
        var solvedStr = expr
        ctx.variables.forEach { (name, value) ->
            if (value is Number) {
                solvedStr = solvedStr.replace(Regex("\\b$name\\b"), value.toDouble().toString())
            }
        }
        
        solvedStr = solvedStr.replace(" ", "")
        
        return try {
            parseMathExpr(solvedStr)
        } catch (e: Exception) {
            0.0
        }
    }

    private fun parseMathExpr(expr: String): Double {
        val terms = expr.split(Regex("(?=[+-])|(?<=[+-])"))
        var total = 0.0
        var currentOp = "+"

        for (term in terms) {
            val t = term.trim()
            if (t == "+" || t == "-") {
                currentOp = t
            } else if (t.isNotEmpty()) {
                val factorVal = parseMathFactors(t)
                if (currentOp == "+") {
                    total += factorVal
                } else {
                    total -= factorVal
                }
            }
        }
        return total
    }

    private fun parseMathFactors(expr: String): Double {
        val factors = expr.split(Regex("(?=[*/%])|(?<=[*/%])"))
        var total = 1.0
        var currentOp = "*"

        for (factor in factors) {
            val f = factor.trim()
            if (f == "*" || f == "/" || f == "%") {
                currentOp = f
            } else if (f.isNotEmpty()) {
                val value = f.toDoubleOrNull() ?: 1.0
                if (currentOp == "*") {
                    total *= value
                } else if (currentOp == "/") {
                    if (value != 0.0) total /= value
                } else if (currentOp == "%") {
                    total %= value
                }
            }
        }
        return total
    }
}

class EvaluationContext(
    val variables: MutableMap<String, Any> = mutableMapOf(),
    val functions: MutableMap<String, Statement.Def> = mutableMapOf(),
    val importedModules: MutableSet<String> = mutableSetOf()
)

sealed interface Statement {
    data class Print(val expr: String) : Statement
    data class Assignment(val target: String, val indexExpr: String?, val expr: String) : Statement
    data class If(val condition: String, val body: List<Statement>, val elifs: List<Pair<String, List<Statement>>>, val elseBody: List<Statement>?) : Statement
    data class For(val loopVar: String, val iterableExpr: String, val body: List<Statement>) : Statement
    data class While(val condition: String, val body: List<Statement>) : Statement
    data class Def(val name: String, val params: List<String>, val body: List<Statement>) : Statement
    data class Import(val module: String) : Statement
    data class MethodCall(val objectName: String, val methodName: String, val argsExpr: String) : Statement
    data class Return(val expr: String?) : Statement
    data class RawExpression(val expr: String) : Statement
}

class RequestsResponse(val url: String) {
    val status_code = 200
    val text = "{\"status\": \"success\", \"message\": \"Successfully retrieved offline virtual mock query datasets.\", \"rates\": {\"USD\": 96730.0, \"EUR\": 89120.0}}"
    fun json(): Map<String, Any> {
        val map = LinkedHashMap<String, Any>()
        map["status"] = "success"
        map["message"] = "Local database packages emulator loaded successfully."
        val rates = LinkedHashMap<String, Any>()
        rates["USD"] = 96730.0
        rates["EUR"] = 89120.0
        map["rates"] = rates
        return map
    }
}

class NumpyArrayMock(val data: List<*>) {
    fun mean(): Double {
        val nums = data.filterIsInstance<Number>().map { it.toDouble() }
        return if (nums.isEmpty()) 0.0 else nums.average()
    }
    fun sum(): Double {
        return data.filterIsInstance<Number>().sumOf { it.toDouble() }
    }
    fun max(): Double {
        return data.filterIsInstance<Number>().maxOfOrNull { it.toDouble() } ?: 0.0
    }
    override fun toString(): String {
        return "array($data)"
    }
}

class PandasDataFrameMock(val dict: Map<*, *>) {
    fun head(): String {
        val builder = StringBuilder()
        builder.append("   Idx |")
        dict.keys.forEach { k -> builder.append(" %-10s |".format(k.toString())) }
        builder.append("\n------" + "------------".repeat(dict.size) + "\n")
        
        var sizeMax = 0
        dict.values.forEach {
            if (it is List<*>) sizeMax = maxOf(sizeMax, it.size)
        }
        if (sizeMax == 0) sizeMax = 1

        for (i in 0 until minOf(sizeMax, 5)) {
            builder.append("   %3d |".format(i))
            dict.keys.forEach { k ->
                val list = dict[k] as? List<*>
                val value = list?.getOrNull(i)?.toString() ?: ""
                builder.append(" %-10.10s |".format(value))
            }
            builder.append("\n")
        }
        return builder.toString()
    }
    override fun toString(): String {
        return "DataFrame columns=[${dict.keys.joinToString()}] size=${dict.values.firstOrNull()?.let { (it as? List<*>)?.size } ?: 0}"
    }
}

data class ExecutionResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)
