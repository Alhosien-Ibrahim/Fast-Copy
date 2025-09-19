package com.example.fastcopy.components

class LineManager {
    var lines: List<String> = emptyList()
    var index: Int = 0

    val progress: Float
        get() = if (lines.isEmpty()) 0f else (index + 1).toFloat() / lines.size

    val statusText: String
        get() = if (lines.isEmpty()) "لا توجد بيانات" else "📄 ${index + 1} من ${lines.size}"

    fun loadLines(input: String) {
        lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
        index = 0
    }

    fun currentLine(): String? = lines.getOrNull(index)

    fun next(): String? {
        if (index < lines.lastIndex) {
            index++
        }
        return currentLine()
    }

    fun previous(): String? {
        if (index > 0) {
            index--
        }
        return currentLine()
    }

    fun copyByNumber(num: Int): String? {
        val targetIndex = num - 1
        if (targetIndex in lines.indices) {
            index = targetIndex
        }
        return currentLine()
    }

    fun copyAll(): String = lines.joinToString("\n")

    fun reset() {
        lines = emptyList()
        index = 0
    }
}