/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/npm-adapter/LICENSE.txt
 */
package com.artipie.npm.proxy.http;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.http.Headers;
import com.artipie.http.headers.ContentType;
import com.artipie.http.rs.RsFull;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.StandardRs;
import com.artipie.http.slice.SliceSimple;
import com.artipie.npm.TgzArchive;
import com.artipie.npm.misc.NextSafeAvailablePort;
import com.artipie.npm.proxy.NpmProxy;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.nio.charset.StandardCharsets;
import javax.json.Json;
import javax.json.JsonObject;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test cases for {@link DownloadAssetSlice}.
 * @since 0.9
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings({"PMD.AvoidUsingHardCodedIP", "PMD.AvoidDuplicateLiterals"})
final class DownloadAssetSliceTest {
    /**
     * Vertx.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * TgzArchive path.
     */
    private static final String TGZ =
        "@hello/simple-npm-project/-/@hello/simple-npm-project-1.0.1.tgz";

    /**
     * Server port.
     */
    private int port;

    @BeforeEach
    void setUp() {
        this.port = new NextSafeAvailablePort().value();
    }

    @AfterAll
    static void tearDown() {
        DownloadAssetSliceTest.VERTX.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/ctx"})
    void obtainsFromStorage(final String pathprefix) {
        final Storage storage = new InMemoryStorage();
        this.saveFilesToStorage(storage);
        final AssetPath path = new AssetPath(pathprefix.replaceFirst("/", ""));
        try (
            VertxSliceServer server = new VertxSliceServer(
                DownloadAssetSliceTest.VERTX,
                new DownloadAssetSlice(
                    new NpmProxy(
                        storage,
                        new SliceSimple(StandardRs.NOT_FOUND)
                    ),
                    path
                ),
                this.port
            )
        ) {
            this.performRequestAndChecks(pathprefix, server);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/ctx"})
    void obtainsFromRemote(final String pathprefix) {
        final AssetPath path = new AssetPath(pathprefix.replaceFirst("/", ""));
        try (
            VertxSliceServer server = new VertxSliceServer(
                DownloadAssetSliceTest.VERTX,
                new DownloadAssetSlice(
                    new NpmProxy(
                        new InMemoryStorage(),
                        new SliceSimple(
                            new RsFull(
                                RsStatus.OK,
                                new Headers.From(new ContentType("tgz")),
                                new Content.From(
                                    new TestResource(
                                        String.format("storage/%s", DownloadAssetSliceTest.TGZ)
                                    ).asBytes()
                                )
                            )
                        )
                    ),
                    path
                ),
                this.port
            )
        ) {
            this.performRequestAndChecks(pathprefix, server);
        }
    }

    private void performRequestAndChecks(final String pathprefix, final VertxSliceServer server) {
        server.start();
        final String url = String.format(
            "http://127.0.0.1:%d%s/%s", this.port, pathprefix, DownloadAssetSliceTest.TGZ
        );
        final WebClient client = WebClient.create(DownloadAssetSliceTest.VERTX);
        final String tgzcontent = client.getAbs(url)
            .rxSend().blockingGet()
            .bodyAsString(StandardCharsets.ISO_8859_1.name());
        final JsonObject json = new TgzArchive(tgzcontent, false).packageJson().blockingGet();
        MatcherAssert.assertThat(
            "Name is parsed properly from package.json",
            json.getJsonString("name").getString(),
            new IsEqual<>("@hello/simple-npm-project")
        );
        MatcherAssert.assertThat(
            "Version is parsed properly from package.json",
            json.getJsonString("version").getString(),
            new IsEqual<>("1.0.1")
        );
    }

    /**
     * Save files to storage from test resources.
     * @param storage Storage
     */
    private void saveFilesToStorage(final Storage storage) {
        storage.save(
            new Key.From(DownloadAssetSliceTest.TGZ),
            new Content.From(
                new TestResource(
                    String.format("storage/%s", DownloadAssetSliceTest.TGZ)
                ).asBytes()
            )
        ).join();
        storage.save(
            new Key.From(
                String.format("%s.meta", DownloadAssetSliceTest.TGZ)
            ),
            new Content.From(
                Json.createObjectBuilder()
                    .add("last-modified", "2020-05-13T16:30:30+01:00")
                    .build()
                    .toString()
                    .getBytes()
            )
        ).join();
    }
}
