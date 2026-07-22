# Fastjson2 to Jackson migration recipe

将使用 `com.alibaba.fastjson2` 的 Java 工程自动迁移到 Jackson 2.x 的 OpenRewrite 配方。

主配方：

```text
com.huawei.clouds.openrewrite.fastjson2.MigrateFastjson2ToJackson
```

## 覆盖范围

| Fastjson2 | 迁移结果 |
| --- | --- |
| `JSON.toJSONString` / `JSON.toJSONBytes` | `JacksonJson.toJson` / `toJsonBytes` |
| `JSON.parseObject` / `JSON.parseArray` | Jackson 反序列化与树模型 facade |
| `TypeReference<T>` | Jackson `TypeReference<T>` |
| `JSONObject` / `JSONArray` | Jackson `ObjectNode` / `ArrayNode` |
| 常用 `get*`、`put`、`add`、`remove`、`toJavaObject`、`toJavaList` | 等价的 `JacksonJson` 调用 |
| `@JSONField(name/format/serialize/deserialize)` | `@JsonProperty` / `@JsonFormat` / `@JsonIgnore` |
| `com.alibaba.fastjson2:fastjson2` | `com.fasterxml.jackson.core:jackson-databind:2.22.x` |

需要保持 Fastjson2 无 checked exception 调用方式时，配方会按源码模块生成：

```text
src/main/java/com/huawei/clouds/openrewrite/fastjson2/JacksonJson.java
```

## 安全行为

只有确认源码中的 Fastjson2 用法都已覆盖时，配方才删除 Fastjson2 依赖。存在下列高级用法时会保留依赖，供后续人工处理：

- `JSONReader.Feature`、`JSONWriter.Feature`、Filter
- `JSONPath`、自定义 reader/writer、AutoType
- `@JSONType` 及 `@JSONField` 的高级属性

迁移后建议运行工程完整测试，并搜索剩余引用：

```bash
rg 'com\.alibaba\.fastjson2|JSONReader\.Feature|JSONWriter\.Feature|JSONPath' src
```

## Maven 使用方式

先在本工程根目录安装配方：

```bash
mvn clean install
```

然后在待迁移工程运行 dry run：

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.44.0:dryRun \
  -Drewrite.recipeArtifactCoordinates=com.huawei.clouds.openrewrite:rewrite-fastjson2-to-jackson:1.0.0-SNAPSHOT \
  -Drewrite.activeRecipes=com.huawei.clouds.openrewrite.fastjson2.MigrateFastjson2ToJackson
```

确认 patch 后，将 `dryRun` 改为 `run`。

## Gradle 使用方式

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    rewrite 'com.huawei.clouds.openrewrite:rewrite-fastjson2-to-jackson:1.0.0-SNAPSHOT'
}

rewrite {
    activeRecipe 'com.huawei.clouds.openrewrite.fastjson2.MigrateFastjson2ToJackson'
}
```

## 开发验证

```bash
mvn -pl rewrite-fastjson2-to-jackson -am clean verify
```

要求 JDK 17+ 和 Maven 3.8+。
