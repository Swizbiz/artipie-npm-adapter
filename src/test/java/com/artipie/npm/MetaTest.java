/*
 * The MIT License (MIT) Copyright (c) 2020-2023 artipie.com
 * https://github.com/artipie/npm-adapter/LICENSE.txt
 */
package com.artipie.npm;

import com.artipie.npm.misc.DateTimeNowStr;
import com.artipie.npm.misc.JsonFromPublisher;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import wtf.g4s8.hamcrest.json.JsonHas;
import wtf.g4s8.hamcrest.json.JsonValueIs;

/**
 * Tests for {@link Meta}.
 *
 * @since 0.4.2
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class MetaTest {

    /**
     * Dist tags json attribute name.
     */
    private static final String DISTTAGS = "dist-tags";

    /**
     * Version json attribute name.
     */
    private static final String VERSIONS = "versions";

    /**
     * Latest tag name.
     */
    private static final String LATEST = "latest";

    /**
     * Alpha tag name.
     */
    private static final String ALPHA = "alpha";

    @Test
    public void canUpdateMetaDistTags() {
        final String time = Instant.now().toString();
        final String versone = "1.0.0";
        final String verstwo = "1.0.1";
        final Meta uploaded = new Meta(
            this.json(versone, true)
                .add(
                    "time", Json.createObjectBuilder()
                        .add("created", time)
                        .add("modified", time)
                        .add(versone, time)
                        .build()
                ).build()
        );
        final JsonObject updated = this.json(verstwo, true)
            .add(
                MetaTest.DISTTAGS, Json.createObjectBuilder()
                    .add(MetaTest.ALPHA, verstwo)
                    .build()
        ).build();
        MatcherAssert.assertThat(
            new JsonFromPublisher(
                uploaded.updatedMeta(updated).byteFlow()
            ).json()
            .toCompletableFuture().join(),
            new AllOf<>(
                Arrays.asList(
                    new JsonHas(
                        MetaTest.DISTTAGS,
                        new AllOf<>(
                            Arrays.asList(
                                new JsonHas(MetaTest.LATEST, new JsonValueIs(versone)),
                                new JsonHas(MetaTest.ALPHA, new JsonValueIs(verstwo))
                            )
                        )
                    ),
                    new JsonHas(
                        MetaTest.VERSIONS,
                        new AllOf<>(
                            Arrays.asList(
                                new JsonHas(versone, Matchers.any(JsonValue.class)),
                                new JsonHas(verstwo, Matchers.any(JsonValue.class))
                            )
                        )
                    )
                )
            )
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldContainTimeAfterUpdateMetadata(final boolean withreadme) {
        final String versone = "1.0.1";
        final String verstwo = "1.1.0";
        final JsonObject uploaded = this.json(versone, withreadme).build();
        final JsonObject updated = this.json(verstwo, true).build();
        final Meta first = new Meta(
            new NpmPublishJsonToMetaSkelethon(uploaded).skeleton()
        ).updatedMeta(uploaded);
        final Meta second = new Meta(
            new JsonFromPublisher(first.byteFlow()).json()
                .toCompletableFuture().join()
        ).updatedMeta(updated);
        MatcherAssert.assertThat(
            this.timeTags(second),
            Matchers.containsInAnyOrder("created", "modified", versone, verstwo)
        );
    }

    @Test
    void shouldContainLatestTagOnFirstUploadWithUserTag() {
        final String vers = "1.0.1";
        final String tag = "sometag";
        final JsonObject uploaded = this.json(vers, true)
            .add(MetaTest.DISTTAGS, Json.createObjectBuilder().add(tag, vers).build())
            .build();
        final Meta meta = new Meta(
            new NpmPublishJsonToMetaSkelethon(uploaded).skeleton()
        ).updatedMeta(uploaded);
        MatcherAssert.assertThat(
            new JsonFromPublisher(meta.byteFlow()).json()
                .toCompletableFuture().join()
                .getJsonObject(MetaTest.DISTTAGS)
                .keySet(),
            Matchers.containsInAnyOrder(tag, MetaTest.LATEST)
        );
    }

    @Test
    void shouldContainLatestTagOnFirstUploadWithoutUserTag() {
        final JsonObject uploaded = this.json("1.0.1", true).build();
        final Meta meta = new Meta(
            new NpmPublishJsonToMetaSkelethon(uploaded).skeleton()
        ).updatedMeta(uploaded);
        MatcherAssert.assertThat(
            new JsonFromPublisher(meta.byteFlow()).json()
                .toCompletableFuture().join()
                .getJsonObject(MetaTest.DISTTAGS)
                .keySet(),
            new IsEqual<>(Collections.singleton(MetaTest.LATEST))
        );
    }

    @Test
    void containsRequiredTimeFieldsWhenModifiedTagWasNotIncluded() {
        final String versone = "1.2.3";
        final String verstwo = "2.0.0";
        final JsonObject existed = this.json(versone, true)
            .add(
                "time",
                Json.createObjectBuilder()
                    .add("created", new DateTimeNowStr().value())
                    .add(versone, new DateTimeNowStr().value())
                    .build()
            ).build();
        final JsonObject updated = this.json(verstwo, true).build();
        MatcherAssert.assertThat(
            this.timeTags(new Meta(existed).updatedMeta(updated)),
            Matchers.containsInAnyOrder("created", "modified", versone, verstwo)
        );
    }

    private Set<String> timeTags(final Meta meta) {
        return new JsonFromPublisher(meta.byteFlow()).json()
            .toCompletableFuture().join()
            .getJsonObject("time")
            .keySet();
    }

    private JsonObjectBuilder json(final String version, final boolean readme) {
        final String proj = "@hello/simple-npm-project";
        final JsonObjectBuilder builder = Json.createObjectBuilder()
            .add("name", proj)
            .add("_id", proj)
            .add(
                MetaTest.DISTTAGS, Json.createObjectBuilder().add(MetaTest.LATEST, version)
            )
            .add(
                MetaTest.VERSIONS, Json.createObjectBuilder()
                .add(
                    version, Json.createObjectBuilder()
                    .add(
                        "dist", Json.createObjectBuilder()
                        .add(
                            "tarball",
                            String.format("http://localhost:8000/proj/-/proj-%s.tgz", version)
                        )
                    ).build()
                )
            );
        if (readme) {
            builder.add("readme", "Some text in readme");
        }
        return builder;
    }
}
