package com.huawei.clouds.openrewrite.hikaricp;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.xml.XmlIsoVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Set;

/** Marks explicit Maven Java baselines that cannot load the HikariCP 6.3.3 bytecode. */
public final class FindHikariJavaBaseline extends Recipe {
    private static final Set<String> JAVA_PROPERTIES = Set.of(
            "java.version", "maven.compiler.release", "maven.compiler.source", "maven.compiler.target"
    );
    private static final Set<String> UNSUPPORTED = Set.of("1.8", "8", "9", "10");
    static final String MESSAGE =
            "HikariCP 6.3.3 requires Java 11 or newer; upgrade the build toolchain and runtime together";

    @Override
    public String getDisplayName() {
        return "Find unsupported HikariCP 6 Java baselines";
    }

    @Override
    public String getDescription() {
        return "Mark exact Maven Java version properties that select Java 8, 9, or 10, with the HikariCP 6.3.3 runtime requirement.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) || !(tree instanceof Xml.Document document) ||
                    !UpgradeSelectedHikariCPDependency.isProjectPath(source.getSourcePath()) ||
                    source.getSourcePath().getFileName() == null ||
                    !"pom.xml".equals(source.getSourcePath().getFileName().toString())) {
                    return tree;
                }
                if (!containsEligibleHikariDependency(document, ctx)) {
                    return document;
                }
                return new XmlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Xml.CharData visitCharData(Xml.CharData charData, ExecutionContext executionContext) {
                        Xml.CharData c = super.visitCharData(charData, executionContext);
                        Cursor tagCursor = getCursor().getParentTreeCursor();
                        if (tagCursor == null || !(tagCursor.getValue() instanceof Xml.Tag property) ||
                            !isMavenPropertiesChild(tagCursor, property) || !JAVA_PROPERTIES.contains(property.getName()) ||
                            !UNSUPPORTED.contains(c.getText().trim())) {
                            return c;
                        }
                        return SearchResult.found(c, MESSAGE);
                    }
                }.visitNonNull(document, ctx);
            }
        };
    }

    private static boolean containsEligibleHikariDependency(Xml.Document document, ExecutionContext ctx) {
        final boolean[] found = {false};
        new XmlIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext executionContext) {
                if (UpgradeSelectedHikariCPDependency.isEligibleHikariDependency(getCursor(), tag)) {
                    found[0] = true;
                }
                return found[0] ? tag : super.visitTag(tag, executionContext);
            }
        }.visit(document, ctx);
        return found[0];
    }

    private static boolean isMavenPropertiesChild(Cursor cursor, Xml.Tag tag) {
        Cursor parent = cursor.getParentTreeCursor();
        if (parent == null) {
            return false;
        }
        Cursor grandparent = parent.getParentTreeCursor();
        if (grandparent == null) {
            return false;
        }
        return parent.getValue() instanceof Xml.Tag parentTag && "properties".equals(parentTag.getName()) &&
               !"properties".equals(tag.getName()) && grandparent.getValue() instanceof Xml.Tag owner &&
               ("project".equals(owner.getName()) || "profile".equals(owner.getName()));
    }
}
