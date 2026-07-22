package com.huawei.clouds.openrewrite.i18next;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.json.tree.JsonKey;

/** Converts only conflict-free English JSON v3 singular/_plural pairs to v4 one/other keys. */
public final class MigrateEnglishV3PluralJson extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate deterministic English i18next JSON v3 plurals";
    }

    @Override
    public String getDescription() {
        return "Renames a conflict-free English locale pair key/key_plural to key_one/key_other at every " +
               "namespace depth; non-English, numeric, incomplete, conflicting, and non-locale JSON remains unchanged.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean english;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                boolean previous = english;
                english = I18nextLocaleSupport.isEnglishResource(document.getSourcePath());
                Json.Document visited = english ? super.visitDocument(document, ctx) : document;
                english = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                if (!english) {
                    return visited;
                }
                String key = I18nextManifestSupport.key(visited);
                String base = key.endsWith("_plural") ? key.substring(0, key.length() - "_plural".length()) : key;
                if (base.isEmpty() || !safePair(base)) {
                    return visited;
                }
                if (key.equals(base)) {
                    return visited.withKey(renameKey(visited.getKey(), base + "_one"));
                }
                if (key.equals(base + "_plural")) {
                    return visited.withKey(renameKey(visited.getKey(), base + "_other"));
                }
                return visited;
            }

            private boolean safePair(String base) {
                Json.JsonObject object = getCursor().firstEnclosing(Json.JsonObject.class);
                if (object == null) {
                    return false;
                }
                Json.Member singular = member(object, base);
                Json.Member plural = member(object, base + "_plural");
                return singular != null && plural != null && scalarMessage(singular) && scalarMessage(plural) &&
                       member(object, base + "_one") == null && member(object, base + "_other") == null;
            }

            private Json.Member member(Json.JsonObject object, String key) {
                return object.getMembers().stream()
                        .filter(Json.Member.class::isInstance)
                        .map(Json.Member.class::cast)
                        .filter(candidate -> key.equals(I18nextManifestSupport.key(candidate)))
                        .findFirst().orElse(null);
            }

            private boolean scalarMessage(Json.Member member) {
                return member.getValue() instanceof Json.Literal literal && literal.getValue() instanceof String;
            }
        };
    }

    private static JsonKey renameKey(JsonKey key, String replacement) {
        if (key instanceof Json.Identifier identifier) {
            return identifier.withName(replacement);
        }
        if (key instanceof Json.Literal literal) {
            String quote = literal.getSource().startsWith("'") ? "'" : "\"";
            return literal.withValue(replacement).withSource(quote + replacement + quote);
        }
        return key;
    }
}
