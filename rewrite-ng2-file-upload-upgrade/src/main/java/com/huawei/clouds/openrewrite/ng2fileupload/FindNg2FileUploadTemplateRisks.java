package com.huawei.clouds.openrewrite.ng2fileupload;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mark exact ng2-file-upload template attributes and their Angular/behavior ownership. */
public final class FindNg2FileUploadTemplateRisks extends Recipe {
    private static final Pattern TAG = Pattern.compile("<[^!/?][^>]*>", Pattern.DOTALL);
    private static final Pattern SELECT = attribute("ng2FileSelect");
    private static final Pattern DROP = attribute("ng2FileDrop");
    private static final Pattern UPLOADER = attribute("uploader");
    private static final Pattern SELECT_EVENT = attribute("onFileSelected");
    private static final Pattern DROP_EVENTS = attribute("fileOver|onFileDrop");
    private static final String SCOPE =
            "ng2-file-upload 10 keeps this directive standalone:false; ensure FileUploadModule is imported by the owning NgModule or standalone Component/TestBed imports and verify lazy scope";

    @Override
    public String getDisplayName() {
        return "Find ng2-file-upload 10 template migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks exact file-select/drop selectors, uploader input, and output bindings for Angular scope, queue, event, validation, and security review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                return Ng2FileUploadSupport.template(visited.getSourcePath()) ? mark(visited) : visited;
            }
        };
    }

    private static PlainText mark(PlainText text) {
        String source = text.getText();
        List<Risk> risks = new ArrayList<>();
        Matcher tags = TAG.matcher(source);
        while (tags.find()) {
            if (insideComment(source, tags.start())) continue;
            String tag = tags.group();
            boolean select = SELECT.matcher(tag).find();
            boolean drop = DROP.matcher(tag).find();
            if (!select && !drop) continue;
            collect(tags.start(), tag, SELECT, SCOPE, risks);
            collect(tags.start(), tag, DROP, SCOPE, risks);
            collect(tags.start(), tag, UPLOADER,
                    "This uploader binding owns queue and transport state; verify initialization timing, destroy/recreate behavior, disabled/error UI, accepted files, and server-side validation", risks);
            if (select) collect(tags.start(), tag, SELECT_EVENT,
                    "onFileSelected emits browser File objects after queue insertion; verify multi-select/reset behavior, duplicate files, validation failures, sensitive metadata, and change detection", risks);
            if (drop) collect(tags.start(), tag, DROP_EVENTS,
                    "This drop output crosses drag/drop browser input; verify file-only payloads, nested targets, preventDefault behavior, duplicate drops, validation errors, and untrusted file metadata", risks);
        }
        risks.sort(Comparator.comparingInt(Risk::start).thenComparingInt(risk -> -risk.end));
        List<Risk> selected = new ArrayList<>();
        int end = -1;
        for (Risk risk : risks) {
            if (risk.start >= end) {
                selected.add(risk);
                end = risk.end;
            }
        }
        if (selected.isEmpty()) return text;
        List<PlainText.Snippet> snippets = new ArrayList<>();
        int cursor = 0;
        for (Risk risk : selected) {
            if (risk.start > cursor) snippets.add(snippet(source.substring(cursor, risk.start)));
            snippets.add(SearchResult.found(snippet(source.substring(risk.start, risk.end)), risk.message));
            cursor = risk.end;
        }
        if (cursor < source.length()) snippets.add(snippet(source.substring(cursor)));
        return text.withText("").withSnippets(snippets);
    }

    private static void collect(int offset, String tag, Pattern pattern, String message, List<Risk> risks) {
        Matcher matcher = pattern.matcher(tag);
        while (matcher.find()) risks.add(new Risk(offset + matcher.start(), offset + matcher.end(), message));
    }

    private static Pattern attribute(String names) {
        String syntax = "(?:\\[\\(" + names + "\\)\\]|\\[" + names + "\\]|\\(" + names + "\\)|" +
                        "bind-(?:" + names + ")|on-(?:" + names + ")|(?:" + names + "))";
        return Pattern.compile("(?<![\\w:-])" + syntax + "(?![\\w:-])" +
                               "(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?");
    }

    private static boolean insideComment(String source, int offset) {
        return source.lastIndexOf("<!--", offset) > source.lastIndexOf("-->", offset);
    }

    private static PlainText.Snippet snippet(String source) {
        return new PlainText.Snippet(Tree.randomId(), Markers.EMPTY, source);
    }

    private record Risk(int start, int end, String message) {
    }
}
