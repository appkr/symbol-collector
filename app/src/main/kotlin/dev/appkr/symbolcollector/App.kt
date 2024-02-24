package dev.appkr.symbolcollector

import de.m3y.kformat.Table
import de.m3y.kformat.table
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import kotlin.io.path.notExists
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Kotlin 프로젝트의 심볼을 수집하고 출력합니다")
        println("")
        println("  java -jar app.jar {path} {format}")
        println("  - path: 수집할 프로젝트의 절대 경로 e.g. \$HOME/path/to/project")
        println("  - format: 출력 형식 (table 또는 csv, 제출하지 않으면 table)")
        exitProcess(1)
    }

    val path = Paths.get(args[0])
    if (path.notExists()) {
        println("경로가 정확하지 않습니다: $path")
        exitProcess(1)
    }

    val format = args.getOrNull(1) ?: "table"
    if (format !in listOf("table", "csv")) {
        println("허용하지 않는 형식입니다: $format")
        exitProcess(1)
    }

    val collector = SymbolCollector()
    val symbols = collector.collect(path)

    val formatter = if (format == "csv") CsvFormatter() else TableFormatter()
    val output = formatter.format(symbols)

    println(output)
}

class SymbolCollector {
    fun collect(path: Path): Collection<SymbolInfo> {
        val store = mutableListOf<SymbolInfo>()
        Files.walk(path)
            .filter(Files::isRegularFile)
            .forEach { filePath ->
                // kotlin 파일이 아니면 건너뛴다
                if (!filePath.toString().endsWith(".kt")) return@forEach
                collect(filePath, store)
            }

        return store
    }

    /**
     * 파일 한 개를 처리한다: path, package, class, method 정보를 수집한다
     */
    private fun collect(filePath: Path, store: MutableList<SymbolInfo>) {
        File(filePath.toUri()).useLines { fileLines ->
            // 한 파일에도 여러 클래스, 여러 메서드가 존재할 수 있다
            var symbolInfo = SymbolInfo()
            fileLines
                .forEachIndexed { lineNo, lineContent ->
                    symbolInfo.path = filePath.toString()

                    // package는 파일당 한번만 매칭된다
                    // 가정: package는 파일 최상단에 선언한다
                    val packageMatcher = PATTERN_PACKAGE.matcher(lineContent)
                    if (packageMatcher.matches()) {
                        symbolInfo.`package` = packageMatcher.group("package")
                        return@forEachIndexed
                    }

                    // 가정: class 매칭 시점에 package는 이미 매칭되었다
                    if (symbolInfo.`package`.isBlank()) return@forEachIndexed

                    // 파일에 선언된 class 갯수만큼 매칭된다
                    val classMatcher = PATTERN_CLASS.matcher(lineContent)
                    if (classMatcher.matches()) {
                        symbolInfo.`class` = classMatcher.group("class")
                        return@forEachIndexed
                    }

                    // 결정: class에 포함되지 않는 method는 수집하지 않는다: e.g. top-level (extension) function
                    if (symbolInfo.`class`.isBlank()) return@forEachIndexed

                    // 파일에 선언된 메서드 갯수만큼 매칭된다
                    val methodMatcher = PATTERN_METHOD.matcher(lineContent)
                    if (methodMatcher.matches()) {
                        symbolInfo.line = lineNo + 1
                        symbolInfo.method = methodMatcher.group("method")
                        store.add(symbolInfo.copy())
                        return@forEachIndexed // SymbolInfo 모델 하나가 완성됐다
                    }
                }

            // SymbolInfo 모델의 모든 값을 초기화한다
            symbolInfo = SymbolInfo()
        }
    }

    companion object {
        private val PATTERN_PACKAGE = Pattern.compile("^package\\s+(?<package>[\\p{L}\\d_.]+)\$")
        private val PATTERN_CLASS =
            Pattern.compile("^((open\\s+|private\\s+|internal\\s+|data\\s+)?class|object|interface)\\s+(?<class>[\\p{L}\\d_]+)[<>\\s]?.*")
        private val PATTERN_METHOD =
            Pattern.compile("^\\s*(override\\s+)?((open\\s+|private\\s+|protected\\s+|internal\\s+|inline\\s+|operator\\s+)?.*fun)\\s+(?<method>[\\p{L}\\d_]+)\\s?.*")
    }
}

data class SymbolInfo(
    var path: String = "",
    var line: Int = 0,
    var `package`: String = "",
    var `class`: String = "",
    var method: String = "",
)

interface Formatter {
    fun format(symbols: Collection<SymbolInfo>): StringBuilder
}

class TableFormatter : Formatter {
    override fun format(symbols: Collection<SymbolInfo>): StringBuilder {
        return table {
            header("path", "package", "class", "method")

            symbols.forEach {
                row("${it.`path`}:${it.line}", it.`package`, it.`class`, it.method)
            }

            hints {
                alignment("path", Table.Hints.Alignment.LEFT)
                alignment("package", Table.Hints.Alignment.LEFT)
                alignment("class", Table.Hints.Alignment.LEFT)
                alignment("method", Table.Hints.Alignment.LEFT)
                borderStyle = Table.BorderStyle.NONE
            }
        }.render(StringBuilder())
    }
}

class CsvFormatter : Formatter {
    override fun format(symbols: Collection<SymbolInfo>): StringBuilder {
        val builder = StringBuilder()
        builder.append("path,package,class,method$CRLF")
        symbols.forEach {
            builder.append(
                listOf("${it.`path`}:${it.line}", it.`package`, it.`class`, it.method).joinToString(",") { it }
            ).append(CRLF)
        }

        return builder
    }

    companion object {
        private val CRLF = System.lineSeparator()
    }
}
