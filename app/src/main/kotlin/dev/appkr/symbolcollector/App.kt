package dev.appkr.symbolcollector

import de.m3y.kformat.Table
import de.m3y.kformat.table
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import kotlin.io.path.notExists

class SymbolCollector {
    fun collect(path: String): Collection<SymbolInfo> {
        val directoryPath = Paths.get(path)
        if (directoryPath.notExists()) {
            throw IllegalArgumentException("경로를 확인해주세요: $directoryPath")
        }

        val store = mutableListOf<SymbolInfo>()
        Files.walk(directoryPath)
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
                    if (symbolInfo.path.isBlank()) return@forEachIndexed

                    // 파일에 선언된 class 갯수만큼 매칭된다
                    val classMatcher = PATTERN_CLASS.matcher(lineContent)
                    if (classMatcher.matches()) {
                        symbolInfo.`class` = classMatcher.group("class")
                        return@forEachIndexed
                    }

                    // 결정: class에 포함되지 않는 method는 수집하지 않는다: e.g. top-level (extension) function
                    if (symbolInfo.path.isBlank()) return@forEachIndexed

                    // 파일에 선언된 메서드 갯수만큼 매칭될 것이다
                    val methodMatcher = PATTERN_METHOD.matcher(lineContent)
                    if (methodMatcher.matches()) {
                        symbolInfo.line = lineNo + 1
                        symbolInfo.method = methodMatcher.group("method")
                        store.add(symbolInfo.copy())
                        return@forEachIndexed // 모델 하나가 완성됐다
                    }
                }

            // package, class, method 모든 값을 초기화한다
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

fun main() {
    val collector = SymbolCollector()
    val allSymbols = collector.collect(System.getProperty("user.home") + "/path/to/yours")
    val render = table {
        header("package", "class", "method")

        allSymbols.forEach {
            row(it.`package`, it.`class`, it.method)
        }

        hints {
            alignment("package", Table.Hints.Alignment.LEFT)
            alignment("class", Table.Hints.Alignment.LEFT)
            alignment("method", Table.Hints.Alignment.LEFT)
            borderStyle = Table.BorderStyle.NONE // or NONE
        }
    }.render(StringBuilder())

    println(render)
}
