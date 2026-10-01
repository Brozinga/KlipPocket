package ru.ytkab0bp.beamklipper.provider

// Document ids of the files provider: "instance:<id>:<path relative to the
// instance's public directory>".
object DocumentIds {
    private const val PREFIX = "instance:"

    data class Parsed(val instanceId: String, val relativePath: String)

    fun format(instanceId: String?, rootPath: String, filePath: String): String {
        val path = when {
            rootPath == filePath -> ""
            rootPath.endsWith("/") -> filePath.substring(rootPath.length)
            else -> filePath.substring(rootPath.length + 1)
        }
        return "$PREFIX$instanceId:$path"
    }

    fun parse(docId: String): Parsed? {
        if (!docId.startsWith(PREFIX)) return null
        val rest = docId.substring(PREFIX.length)
        val i = rest.indexOf(':')
        if (i == -1) return null
        return Parsed(rest.substring(0, i), rest.substring(i + 1))
    }
}
