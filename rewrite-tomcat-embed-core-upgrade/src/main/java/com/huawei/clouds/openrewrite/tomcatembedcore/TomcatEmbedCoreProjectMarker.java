package com.huawei.clouds.openrewrite.tomcatembedcore;

import org.openrewrite.marker.Marker;

import java.util.UUID;

/** Carries the exact pre-upgrade Tomcat source version to later project-scoped recipes. */
final class TomcatEmbedCoreProjectMarker implements Marker {
    private final UUID id;
    private final String sourceVersion;

    TomcatEmbedCoreProjectMarker(UUID id, String sourceVersion) {
        this.id = id;
        this.sourceVersion = sourceVersion;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public TomcatEmbedCoreProjectMarker withId(UUID id) {
        return new TomcatEmbedCoreProjectMarker(id, sourceVersion);
    }

    String getSourceVersion() {
        return sourceVersion;
    }
}
