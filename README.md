## Kotlin Symbol Collector

Kotlin 프로젝트의 심볼을 수집하고 출력합니다.

우리는 이름 짓는데 많은 시간을 씁니다. 기존 프로젝트에서 사용한 이름을 분석하여, 이름에 사용한 명사와 동사를 분석하여, 이름을 짓는데 들이는 시간을 줄이고자 하는 목적으로 이 도구를 만들었습니다.

### 사용법

```shell
java -jar app.jar {path} {format}
```
- `path`: 수집할 프로젝트의 절대 경로 e.g. `$HOME/path/to/project`
- `format`: 출력 형식 (`table` 또는 `csv`, 제출하지 않으면 `table`)
