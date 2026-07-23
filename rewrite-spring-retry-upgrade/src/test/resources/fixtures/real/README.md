# Fixed real-repository fixtures

Each fixture is reduced to the smallest compilable shape that preserves the upstream migration boundary. Identifiers and relevant statements are retained; unrelated implementation is removed.

| Fixture | Fixed upstream | Source path | License | Covered boundary |
|---|---|---|---|---|
| `netflix-genie-retryable.java` | `Netflix/genie@923ea15f963849b3594e1403c4a47ea8c80ac151` | `genie-web/src/main/java/com/netflix/genie/web/services/impl/ArchivedJobServiceImpl.java` | Apache-2.0 | `@Retryable(include=...)` plus initialization-time retry/backoff expressions |
| `oneops-listener.java` | `oneops/oneops@54780ad3de35a285f3d00baeb9be49e54f47619e` | `controller/src/main/java/com/oneops/controller/cms/CMSClient.java` | Apache-2.0 | nested `RetryListenerSupport`, listener registration and `onError`/`close` callbacks |
| `spring-retry-builder.java` | `spring-projects/spring-retry@f1012127f6084800ef5d3b8f8f2bc3b51c53997a` (`v2.0.13`) | `src/test/java/org/springframework/retry/support/RetryTemplateBuilderTests.java` | Apache-2.0 | deprecated `withinMillis(long)` delegating to `withTimeout(long)` |

The test suite executes the module recipes against all three fixtures; they are not decorative samples.
