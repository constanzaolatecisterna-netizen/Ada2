package com.example.ai

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

object LocalUtilitySolver {
    private val spanishFormat = DecimalFormat(
        "#,##0.##",
        DecimalFormatSymbols(Locale("es", "ES")),
    )

    fun trySolve(rawText: String): String? {
        val norm = LocalCommandMatcher.normalize(rawText)
        if (norm.isEmpty()) return null

        solveCoinOrDice(norm)?.let { return it }
        solveUnitConversion(norm)?.let { return it }
        solvePercentage(norm)?.let { return it }
        solveSquareRootOrPower(norm)?.let { return it }
        solveArithmetic(norm)?.let { return it }
        return null
    }

    private fun solveCoinOrDice(norm: String): String? {
        if (norm.contains("cara o cruz") || norm.contains("cara o sello") || norm.contains("lanza una moneda") || norm.contains("tira una moneda")) {
            val side = if (Random.nextBoolean()) "cara" else "cruz"
            return "Salió $side."
        }
        if (norm.contains("tira un dado") || norm.contains("lanza un dado")) {
            val roll = Random.nextInt(1, 7)
            return "El dado cayó en $roll."
        }
        val rangeMatch = Regex("(?:elige|dime|saca)\\s+(?:un\\s+)?numero\\s+entre\\s+(\\d+)\\s+y\\s+(\\d+)").find(norm)
        if (rangeMatch != null) {
            val a = rangeMatch.groupValues[1].toIntOrNull() ?: return null
            val b = rangeMatch.groupValues[2].toIntOrNull() ?: return null
            val low = minOf(a, b)
            val high = maxOf(a, b)
            val chosen = Random.nextInt(low, high + 1)
            return "El número elegido entre $low y $high es $chosen."
        }
        return null
    }

    private fun solvePercentage(norm: String): String? {
        val pctMatch = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:%|por ciento)\\s+de\\s+(\\d+(?:[.,]\\d+)?)").find(norm)
            ?: return null
        val pct = parseNum(pctMatch.groupValues[1]) ?: return null
        val base = parseNum(pctMatch.groupValues[2]) ?: return null
        val result = (pct / 100.0) * base
        return "El ${fmt(pct)}% de ${fmt(base)} es ${fmt(result)}."
    }

    private fun solveSquareRootOrPower(norm: String): String? {
        val sqrtMatch = Regex("raiz\\s+(?:cuadrada\\s+)?de\\s+(\\d+(?:[.,]\\d+)?)").find(norm)
        if (sqrtMatch != null) {
            val value = parseNum(sqrtMatch.groupValues[1]) ?: return null
            return "La raíz cuadrada de ${fmt(value)} es ${fmt(sqrt(value))}."
        }
        val powMatch = Regex("(\\d+(?:[.,]\\d+)?)\\s+(?:al cuadrado|elevado a 2|elevado al cuadrado)").find(norm)
        if (powMatch != null) {
            val value = parseNum(powMatch.groupValues[1]) ?: return null
            return "${fmt(value)} al cuadrado es ${fmt(value.pow(2))}."
        }
        return null
    }

    private fun solveUnitConversion(norm: String): String? {
        val tempCtoF = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:grados\\s+)?(?:celsius|centigrados|c)\\s+(?:a|en)\\s+(?:fahrenheit|f)\\b").find(norm)
            ?: Regex("(?:fahrenheit|f)\\s+(?:son|es)\\s+(\\d+(?:[.,]\\d+)?)\\s*(?:grados\\s+)?(?:celsius|centigrados|c)").find(norm)
        if (tempCtoF != null) {
            val c = parseNum(tempCtoF.groupValues[1]) ?: return null
            val f = c * 9.0 / 5.0 + 32.0
            return "${fmt(c)} grados Celsius equivalen a ${fmt(f)} grados Fahrenheit."
        }

        val tempFtoC = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:grados\\s+)?(?:fahrenheit|f)\\s+(?:a|en)\\s+(?:celsius|centigrados|c)\\b").find(norm)
            ?: Regex("(?:celsius|centigrados|c)\\s+(?:son|es)\\s+(\\d+(?:[.,]\\d+)?)\\s*(?:grados\\s+)?(?:fahrenheit|f)").find(norm)
        if (tempFtoC != null) {
            val f = parseNum(tempFtoC.groupValues[1]) ?: return null
            val c = (f - 32.0) * 5.0 / 9.0
            return "${fmt(f)} grados Fahrenheit equivalen a ${fmt(c)} grados Celsius."
        }

        val convMatch = Regex(
            "(?:cuantos|cuantas|cuanto|convierte|convertir|pasa|pasar)?\\s*(\\d+(?:[.,]\\d+)?)\\s+" +
                "(km|kilometro|kilometros|milla|millas|m|metro|metros|cm|centimetro|centimetros|pulgada|pulgadas|" +
                "kg|kilo|kilos|kilogramo|kilogramos|g|gramo|gramos|libra|libras|hora|horas|minuto|minutos|segundo|segundos)" +
                "\\s+(?:a|en)\\s+" +
                "(km|kilometro|kilometros|milla|millas|m|metro|metros|cm|centimetro|centimetros|pulgada|pulgadas|" +
                "kg|kilo|kilos|kilogramo|kilogramos|g|gramo|gramos|libra|libras|hora|horas|minuto|minutos|segundo|segundos)\\b"
        ).find(norm) ?: return null

        val qty = parseNum(convMatch.groupValues[1]) ?: return null
        val from = canonicalUnit(convMatch.groupValues[2]) ?: return null
        val to = canonicalUnit(convMatch.groupValues[3]) ?: return null

        val converted = convertUnits(qty, from, to) ?: return null
        return "${fmt(qty)} ${unitLabel(from)} equivalen a ${fmt(converted)} ${unitLabel(to)}."
    }

    private fun solveArithmetic(norm: String): String? {
        val cleaned = norm
            .replace(Regex("^(?:cuanto es|calcula|resuelve|dime cuanto es|resultado de)\\s+"), "")
            .replace(" multiplicado por ", " * ")
            .replace(" dividido entre ", " / ")
            .replace(" dividido por ", " / ")
            .replace(" dividido en ", " / ")
            .replace(" entre ", " / ")
            .replace(" por ", " * ")
            .replace(" mas ", " + ")
            .replace(" menos ", " - ")
            .replace(" x ", " * ")
            .replace(",", ".")
            .trim()

        // Allow only numbers, operators, parentheses, spaces, decimal points
        if (!cleaned.matches(Regex("[0-9.+\\-*/()\\s]+"))) return null
        if (!cleaned.contains(Regex("[+\\-*/]"))) return null

        val value = evalExpression(cleaned) ?: return null
        if (value.isNaN() || value.isInfinite()) return "No se puede dividir por cero."
        return "El resultado es ${fmt(value)}."
    }

    private fun evalExpression(expr: String): Double? {
        return try {
            // Recursive descent parser supporting +, -, *, / and parentheses
            val parser = object {
                var idx = 0

                fun ws() {
                    while (idx < expr.length && expr[idx].isWhitespace()) idx++
                }

                fun parseExpr(): Double? {
                    var left = parseTerm() ?: return null
                    while (true) {
                        ws()
                        if (idx >= expr.length) break
                        val op = expr[idx]
                        if (op != '+' && op != '-') break
                        idx++
                        val right = parseTerm() ?: return null
                        left = if (op == '+') left + right else left - right
                    }
                    return left
                }

                fun parseTerm(): Double? {
                    var left = parseAtom() ?: return null
                    while (true) {
                        ws()
                        if (idx >= expr.length) break
                        val op = expr[idx]
                        if (op != '*' && op != '/') break
                        idx++
                        val right = parseAtom() ?: return null
                        left = if (op == '*') left * right else {
                            if (right == 0.0) return Double.NaN else left / right
                        }
                    }
                    return left
                }

                fun parseAtom(): Double? {
                    ws()
                    if (idx >= expr.length) return null
                    if (expr[idx] == '+') {
                        idx++
                        return parseAtom()
                    }
                    if (expr[idx] == '-') {
                        idx++
                        return parseAtom()?.let { -it }
                    }
                    if (expr[idx] == '(') {
                        idx++
                        val inner = parseExpr() ?: return null
                        ws()
                        if (idx < expr.length && expr[idx] == ')') idx++
                        return inner
                    }
                    val start = idx
                    while (idx < expr.length && (expr[idx].isDigit() || expr[idx] == '.')) idx++
                    if (start == idx) return null
                    return expr.substring(start, idx).toDoubleOrNull()
                }
            }
            val result = parser.parseExpr()
            parser.ws()
            if (parser.idx == expr.length) result else null
        } catch (_: Exception) {
            null
        }
    }

    private fun canonicalUnit(raw: String): String? = when (raw) {
        "km", "kilometro", "kilometros" -> "km"
        "milla", "millas" -> "mi"
        "m", "metro", "metros" -> "m"
        "cm", "centimetro", "centimetros" -> "cm"
        "pulgada", "pulgadas" -> "in"
        "kg", "kilo", "kilos", "kilogramo", "kilogramos" -> "kg"
        "g", "gramo", "gramos" -> "g"
        "libra", "libras" -> "lb"
        "hora", "horas" -> "h"
        "minuto", "minutos" -> "min"
        "segundo", "segundos" -> "s"
        else -> null
    }

    private fun convertUnits(qty: Double, from: String, to: String): Double? {
        if (from == to) return qty
        val lengthMeters = mapOf("km" to 1000.0, "m" to 1.0, "cm" to 0.01, "mi" to 1609.344, "in" to 0.0254)
        if (from in lengthMeters && to in lengthMeters) {
            return qty * lengthMeters.getValue(from) / lengthMeters.getValue(to)
        }
        val massGrams = mapOf("kg" to 1000.0, "g" to 1.0, "lb" to 453.59237)
        if (from in massGrams && to in massGrams) {
            return qty * massGrams.getValue(from) / massGrams.getValue(to)
        }
        val timeSeconds = mapOf("h" to 3600.0, "min" to 60.0, "s" to 1.0)
        if (from in timeSeconds && to in timeSeconds) {
            return qty * timeSeconds.getValue(from) / timeSeconds.getValue(to)
        }
        return null
    }

    private fun unitLabel(unit: String): String = when (unit) {
        "km" -> "kilómetros"
        "mi" -> "millas"
        "m" -> "metros"
        "cm" -> "centímetros"
        "in" -> "pulgadas"
        "kg" -> "kilogramos"
        "g" -> "gramos"
        "lb" -> "libras"
        "h" -> "horas"
        "min" -> "minutos"
        "s" -> "segundos"
        else -> unit
    }

    private fun parseNum(s: String): Double? = s.replace(",", ".").toDoubleOrNull()

    private fun fmt(v: Double): String = spanishFormat.format(v)
}
