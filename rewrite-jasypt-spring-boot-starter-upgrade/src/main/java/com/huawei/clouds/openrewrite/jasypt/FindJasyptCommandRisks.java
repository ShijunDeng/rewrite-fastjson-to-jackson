package com.huawei.clouds.openrewrite.jasypt;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.Locale;

/** Marks Jasypt secrets and plaintext-producing operations in deployment/test scripts. */
public final class FindJasyptCommandRisks extends Recipe {
    @Override
    public String getDisplayName() {
        return "Find Jasypt CLI and script risks";
    }

    @Override
    public String getDescription() {
        return "Mark command-line/system-property password exposure, committed environment assignments, and decrypt/reencrypt operations in executable or deployment text files.";
    }

    @Override
    public PlainTextVisitor<ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText t = (PlainText) super.visitText(text, ctx);
                if (!scriptLike(t)) {
                    return t;
                }
                String source = t.getText();
                if (source.contains("-Djasypt.encryptor.password=") ||
                    source.contains("--jasypt.encryptor.password=")) {
                    return SearchResult.found(t,
                            "Password supplied on a command line may leak through process listings, shell history, CI logs, and diagnostics; use masked secret injection");
                }
                if (source.matches("(?s).*JASYPT_ENCRYPTOR_PASSWORD\\s*=\\s*[^$\\s][^\\r\\n]*.*")) {
                    return SearchResult.found(t,
                            "Tracked script assigns a concrete Jasypt password; replace it with a secret-store reference and rotate the value");
                }
                if (source.contains("jasypt:decrypt") || source.contains("jasypt:reencrypt")) {
                    return SearchResult.found(t,
                            "Jasypt decrypt/reencrypt command handles plaintext and old/new keys; isolate output, disable command echo, and audit temporary files/logs");
                }
                return t;
            }
        };
    }

    private static boolean scriptLike(PlainText text) {
        if (!JasyptVersions.isProjectPath(text.getSourcePath())) {
            return false;
        }
        String path = text.getSourcePath().toString().toLowerCase(Locale.ROOT);
        return path.endsWith(".sh") || path.endsWith(".bash") || path.endsWith(".zsh") ||
               path.endsWith(".ps1") || path.endsWith(".cmd") || path.endsWith(".bat") ||
               path.endsWith("dockerfile") || path.contains("/.github/workflows/") ||
               path.startsWith(".github/workflows/");
    }
}
