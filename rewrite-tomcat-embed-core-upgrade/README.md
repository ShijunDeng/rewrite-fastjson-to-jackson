# Tomcat Embed Core 10.1.57 migration recipes

This module migrates `org.apache.tomcat.embed:tomcat-embed-core` to `10.1.57`. It is deliberately more than a version edit: deterministic Servlet/EL and listener-configuration incompatibilities are rewritten, while decisions whose correct answer depends on traffic, security policy, native libraries, cluster topology, or custom Tomcat internals are left in place with precise OpenRewrite markers.

## Recipes

| Recipe | Mode | What it does |
| --- | --- | --- |
| `com.huawei.clouds.openrewrite.tomcatembedcore.FindTomcatEmbedCoreBranchTransitionRisks` | MARK | Preserves the Tomcat 9 namespace boundary and reports Tomcat 11 sources as conflicting with the supplied 10.1 target; Tomcat 11 versions are never edited. |
| `com.huawei.clouds.openrewrite.tomcatembedcore.UpgradeTomcatEmbedCoreTo10_1_57` | AUTO/MARK | Strict approved-version dependency upgrade plus the branch-transition guard. |
| `com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCore101Java` | AUTO | Rewrites documented direct-equivalent Servlet 5 calls and `MethodExpression.isParmetersProvided()`; reorders the removed `ServletContext.log(Exception,String)` overload. |
| `com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcat9JakartaNamespaces` | AUTO | Migrates type-attributed `javax.servlet`/`javax.el` Java source to Jakarta, while refusing to manufacture removed Servlet 6 types. |
| `com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCore101Configuration` | AUTO | Removes six obsolete `JreMemoryLeakPreventionListener` attributes whose Java-8 leak workarounds disappeared on the Java-11 baseline. |
| `com.huawei.clouds.openrewrite.tomcatembedcore.FindTomcatEmbedCoreBuildRisks` | MARK | Marks unresolved/external versions, variants, Java below 11 and Tomcat Embed family misalignment. |
| `com.huawei.clouds.openrewrite.tomcatembedcore.FindTomcatEmbedCoreJavaRisks` | MARK | Marks removed Servlet APIs, obsolete overrides, internal/ APR APIs, cookie assumptions and case-insensitive HTTP-method logic. |
| `com.huawei.clouds.openrewrite.tomcatembedcore.FindTomcatEmbedCoreConfigurationRisks` | MARK | Marks parameter-limit, URI, APR, DIGEST, ETag, cluster and descriptor decisions. |
| `com.huawei.clouds.openrewrite.tomcatembedcore.FindTomcatEmbedCoreResourceRisks` | MARK | Marks `META-INF/services`, manifest and configuration strings that still name Javax Servlet/EL contracts. |
| `com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCoreTo10_1_57` | RECOMMENDED | Guards branch transitions, applies deterministic AUTO changes, then reports the remaining build/source/configuration decisions. |

`SearchResult` comments are review findings, not edits that claim to solve the marked issue. Run the recommended recipe, review every marker, add deployment-specific changes and tests, then remove or suppress findings only with evidence.

## Strict remediation boundary

The latest highest-priority remediation list supplied by the project owner supersedes the workbook's truncated display. These 38 versions are approved AUTO inputs:

```text
10.1.15, 10.1.16, 10.1.20, 10.1.25, 10.1.28, 10.1.35, 10.1.36,
10.1.40, 10.1.41, 10.1.42, 10.1.43, 10.1.44, 10.1.46, 10.1.47,
10.1.48, 10.1.49, 10.1.52, 10.1.54,
9.0.54, 9.0.69, 9.0.71, 9.0.75, 9.0.82, 9.0.83, 9.0.86, 9.0.87,
9.0.91, 9.0.96, 9.0.98, 9.0.102, 9.0.104, 9.0.105, 9.0.106,
9.0.107, 9.0.108, 9.0.111, 9.0.115, 9.0.117
```

All resolve to `10.1.57`. The same list also contained `11.0.18` and `11.0.21`; those rows conflict with the supplied 10.1 target because this programme permits upgrades only. They are not a 11→10.1 migration path: both values are deliberately excluded from AUTO, remain byte-for-byte unchanged, and receive a target-conflict marker until an approved Tomcat 11 target is supplied. The formerly visible `10.0.27` value is not in the superseding list and is now a strict NOOP.

AUTO ownership rules are also strict:

- Maven: direct root/profile dependencies and dependency management; a property is changed only when it has exactly one visible owner and every reference owned by that property is this exact dependency version.
- Gradle Groovy/Kotlin: literal or supported map notation in the root `dependencies` block only.
- Classifiers, non-JAR types, ranges, dynamic versions, version catalogs, platforms/BOM owners, interpolation, nested `subprojects`/`allprojects`, plugin/buildscript dependencies and generated/cache trees are not guessed.
- A higher major/minor source is never rewritten to a lower target, even if a data row requests it.

## Implemented incompatibility handling

### Deterministic AUTO changes

| Old source/configuration | New source/configuration | Reason the edit is safe |
| --- | --- | --- |
| Type-attributed `javax.servlet.*` / `javax.el.*` Java types | `jakarta.servlet.*` / `jakarta.el.*` | Tomcat 9→10 is the Java EE→Jakarta EE namespace transition; comments and string literals are not rewritten. |
| `HttpServletRequest.isRequestedSessionIdFromUrl()` | `isRequestedSessionIdFromURL()` | The removed method was the deprecated spelling of the same API. |
| `HttpServletResponse.encodeUrl(s)` | `encodeURL(s)` | Direct non-deprecated equivalent. |
| `HttpServletResponse.encodeRedirectUrl(s)` | `encodeRedirectURL(s)` | Direct non-deprecated equivalent. |
| `HttpSession.getValue(k)` | `getAttribute(k)` | Servlet compatibility delegate. |
| `HttpSession.putValue(k,v)` | `setAttribute(k,v)` | Servlet compatibility delegate with the same binding-listener contract. |
| `HttpSession.removeValue(k)` | `removeAttribute(k)` | Servlet compatibility delegate. |
| `ServletContext.log(exception,message)` | `log(message,exception)` | Servlet 5 exposed both overloads; Servlet 6 removed the deprecated argument order. |
| `MethodExpression.isParmetersProvided()` | `isParametersProvided()` | Correctly-spelled method already existed and the typo was removed by EL 5. |
| Six removed `JreMemoryLeakPreventionListener` XML attributes | Attribute removed | Their setters and Java-8 workarounds were removed; there is no replacement setting on Java 11. |

Rewrites require OpenRewrite type attribution. A same-named business method is never rewritten. Obsolete interface implementations are marked instead of blindly renamed because a class normally already implements the replacement method and a rename could create a duplicate declaration.

For Tomcat 9 source, a compilation unit using `SingleThreadModel`, `HttpSessionContext`, or `HttpUtils` is intentionally not namespace-rewritten. Those types do not exist in Servlet 6; the risk recipe marks them, the developer replaces the behavior, and a later recipe cycle can safely migrate the rest of that unit. This prevents an apparently successful edit from creating nonexistent `jakarta.*` types.

### Decisions kept as MARK

- Java 11 is a hard runtime baseline; AUTO cannot know every CI image, test worker, container base image or launch script.
- Tomcat 9→10.1 crosses Servlet 4→6 and Java EE→Jakarta EE. The Java namespace is automated where type-safe; explicit `javax.servlet`/`javax.el` dependencies, descriptors, service providers, reflection strings, JSP/WebSocket libraries and framework integrations are marked for coordinated migration.
- Servlet 6 removed other deprecated types and methods (`SingleThreadModel`, `HttpUtils`, `HttpSessionContext`, `getValueNames`, the two-argument `setStatus`, old `UnavailableException` state, and more). Their replacements depend on data shape or error policy.
- Tomcat's `org.apache.catalina`, `org.apache.coyote` and `org.apache.tomcat` internals are broadly but not binary compatible. Custom Valves, Realms, Connectors, class loaders and embedded-container extensions require JavaDoc/API review.
- The APR connector and most legacy JNI surface were removed. NIO/NIO2 selection plus OpenSSL/Tomcat Native is an operational migration, not a class-name substitution.
- Connector `maxParameterCount` default fell from 10,000 to 1,000 in 10.1.8. Restoring 10,000 automatically would erase a security control.
- URI decoding/normalization was clarified and later tightened, including NULL-byte rejection in 10.1.55. Encoded separators and proxy normalization require integration tests.
- HTTP methods are compared case-sensitively from 10.1.47. Case-insensitive application branches are marked.
- Strong default-servlet ETags changed SHA-1→SHA-256 in 10.1.46 when enabled; cache identity changes are expected.
- DIGEST authentication in 10.1.57 requires a valid RFC 7616 `qop`; algorithm/client/credential policy cannot be inferred.
- `EncryptInterceptor` changed its wire data in 10.1.56. An upgrade crossing that boundary requires a full-cluster stop/restart; rolling mixed versions fail.
- Tomcat Embed sibling modules must resolve to the same release; BOM/catalog/parent owners remain explicit work.
- A Servlet 4/5 deployment descriptor is marked for schema review. A Servlet 6.1 descriptor is also marked because it indicates that the supplied 10.1 target is invalid for a Tomcat 11 source; it is never silently lowered.

## Usage

Build and test this module:

```bash
mvn -f rewrite-tomcat-embed-core-upgrade/pom.xml clean verify
```

The module currently executes 367 tests with zero failures. They include every approved source literal in direct Maven, exclusive-property Maven, Gradle Groovy and Gradle Kotlin forms; both blocked Tomcat 11 values; real-repository fixtures; positive, negative, lookalike, owner, overload, generated-path, marker-survival, aggregate-order and two-cycle idempotence cases.

After publishing the recipe artifact on the OpenRewrite runtime classpath, activate:

```text
com.huawei.clouds.openrewrite.tomcatembedcore.MigrateTomcatEmbedCoreTo10_1_57
```

Run it on a clean branch, inspect the diff and every `~~(...)~~>` marker, compile with Java 11+ and Tomcat 10.1.57, then exercise HTTP parsing, session/authentication, TLS/native, cache, proxy and cluster tests. AUTO intentionally does not wait for a remote CI run.

## Evidence and fixtures

Primary fixed sources used for the specification and tests:

- [Tomcat 10 migration guide](https://tomcat.apache.org/migration-10.html): the Tomcat 9→10 `javax.*`→`jakarta.*` transition and Servlet 4→5 boundary.
- [Tomcat 10.1 migration guide](https://tomcat.apache.org/migration-10.1.html): Java 11, Servlet 6/EL 5 removals, internal-API warning, APR removal, `maxParameterCount`, cache-header and `EncryptInterceptor` changes.
- [Tomcat 10.1.57 changelog](https://tomcat.apache.org/tomcat-10.1-doc/changelog.html): target release plus DIGEST `qop`, URI NULL-byte rejection, case-sensitive HTTP methods and strong-ETag digest changes.
- [`tomcat-embed-core` 9.0.117 source tag](https://github.com/apache/tomcat/tree/f892e52577feef83aff57d34c2b4be61a5a68524), [10.0.27 source tag](https://github.com/apache/tomcat/tree/ca8720d41f3be917dc3fcdd03fcca8d3152a13fb), and [10.1.57 source tag](https://github.com/apache/tomcat/tree/5da21b1c24a6443bca5c10dc80a69a76042ca337): namespace, API and configuration comparisons. Tag commits are pinned so later default-branch changes cannot alter the evidence.
- [Jakarta Servlet 6.0 specification](https://jakarta.ee/specifications/servlet/6.0/): normative target API.

Reduced real-repository fixtures retain the relevant expression and are pinned to immutable commits:

- [`jfinal/jfinal` `JsonRequest`](https://github.com/jfinal/jfinal/blob/a0e9e8b99dc793bcf0cd40ca7feba005ba0c5349/src/main/java/com/jfinal/core/paragetter/JsonRequest.java) supplies an `isRequestedSessionIdFromUrl()` wrapper shape.
- [`yona-projects/yona` `PlayServletResponse`](https://github.com/yona-projects/yona/blob/60a5ac40689fc36ee5b55eddedd345fc34878190/app/utils/PlayServletResponse.java) supplies both removed response override shapes; the recipe marks the declarations instead of creating duplicate replacements.
- [`Jahia/jahia` `ServletContextWrapper`](https://github.com/Jahia/jahia/blob/5e201521576ec5814b58321845915c3a984892d8/bundles/jahiamodule-extender/src/main/java/org/jahia/bundles/extender/jahiamodules/jsp/ServletContextWrapper.java) supplies the legacy `ServletContext.log(Exception,String)` delegate call and obsolete override shape.
- Apache Tomcat's own 10.0.27 [`ResponseFacade`](https://github.com/apache/tomcat/blob/ca8720d41f3be917dc3fcdd03fcca8d3152a13fb/java/org/apache/catalina/connector/ResponseFacade.java) and [`RequestFacade`](https://github.com/apache/tomcat/blob/ca8720d41f3be917dc3fcdd03fcca8d3152a13fb/java/org/apache/catalina/connector/RequestFacade.java) establish the compatibility-delegate relationships.

Tests follow OpenRewrite's pinned [`RewriteTest`/cycle assertions](https://github.com/openrewrite/rewrite/blob/fb933bdb74f2f4dc10ec79387e29aa8f5a8a9503/rewrite-test/src/main/java/org/openrewrite/test/RewriteTest.java), [`ChangePackageTest`](https://github.com/openrewrite/rewrite/blob/fb933bdb74f2f4dc10ec79387e29aa8f5a8a9503/rewrite-java-test/src/test/java/org/openrewrite/java/ChangePackageTest.java), and [`SearchResult` marker](https://github.com/openrewrite/rewrite/blob/fb933bdb74f2f4dc10ec79387e29aa8f5a8a9503/rewrite-java/src/main/java/org/openrewrite/java/search/UsesType.java) patterns. Each fixture test identifies any namespace-only reduction in its comment.

## Known limits

The recipe does not infer effective versions from remote parents/BOMs/catalogs, edit external files, choose security limits, select a native/TLS architecture, coordinate a cluster shutdown, or prove runtime compatibility of Tomcat internals. It never treats the two conflicting Tomcat 11 rows as upgrade candidates for a 10.1 target. Those boundaries are visible MARK/NOOP behavior and are covered by exact-whitelist, target-conflict, negative, ownership, generated-source, type-attribution, overload, lookalike, idempotence and aggregate-parity tests.
