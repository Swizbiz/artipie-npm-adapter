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
import com.artipie.npm.TgzArchive;
import com.artipie.npm.misc.NextSafeAvailablePort;
import com.artipie.npm.proxy.NpmProxy;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
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
     * NPM Proxy.
     */
    private NpmProxy npm;

    /**
     * Server port.
     */
    private int port;

    /**
     * TgzArchive path.
     */
    private String tgzpath;

    @BeforeEach
    void setUp() throws InterruptedException, ExecutionException {
        final Storage storage = new InMemoryStorage();
        this.tgzpath =
            "@hello/simple-npm-project/-/@hello/simple-npm-project-1.0.1.tgz";
        this.saveFilesToStorage(storage);
        this.port = new NextSafeAvailablePort().value();
        this.npm = new NpmProxy(
            URI.create(String.format("http://127.0.0.1:%d", this.port)),
            DownloadAssetSliceTest.VERTX,
            storage
        );
    }

    @AfterAll
    static void tearDown() {
        DownloadAssetSliceTest.VERTX.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/ctx"})
    void downloadMetaWorks(final String pathprefix) {
        final AssetPath path = new AssetPath(
            pathprefix.replaceFirst("/", "")
        );
        try (
            VertxSliceServer server = new VertxSliceServer(
                DownloadAssetSliceTest.VERTX,
                new DownloadAssetSlice(this.npm, path),
                this.port
            )
        ) {
            server.start();
            final String url = String.format(
                "http://127.0.0.1:%d%s/%s",
                this.port,
                pathprefix,
                this.tgzpath
            );
            final WebClient client = WebClient.create(DownloadAssetSliceTest.VERTX);
            final String tgzcontent = client.getAbs(url)
                .rxSend().blockingGet()
                .bodyAsString(StandardCharsets.ISO_8859_1.name());
            final JsonObject json = new TgzArchive(tgzcontent, false)
                .packageJson()
                .blockingGet();
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
    }

    /**
     * Save files to storage from test resources.
     * @param storage Storage
     * @throws InterruptedException If interrupts
     * @throws ExecutionException If execution fails
     */
    private void saveFilesToStorage(final Storage storage)
        throws InterruptedException, ExecutionException {
        storage.save(
            new Key.From(this.tgzpath),
            new Content.From(
                new TestResource(
                    String.format("storage/%s", this.tgzpath)
                ).asBytes()
            )
        ).get();
        storage.save(
            new Key.From(
                String.format("%s.meta", this.tgzpath)
            ),
            new Content.From(
                Json.createObjectBuilder()
                    .add("last-modified", "2020-05-13T16:30:30+01:00")
                    .build()
                    .toString()
                    .getBytes()
            )
        ).get();
    }
}
