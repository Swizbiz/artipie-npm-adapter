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
import com.artipie.npm.RandomFreePort;
import com.artipie.npm.proxy.NpmProxy;
import com.artipie.vertx.VertxSliceServer;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import javax.json.Json;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test cases for {@link DownloadPackageSlice}.
 * @since 0.9
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @todo 239#30min Fix download meta for empty prefix.
 *  Test for downloading meta hangs for some reason when empty prefix
 *  is passed. It is necessary to find out why it happens and add
 *  empty prefix to params of method DownloadPackageSliceTest#downloadMetaWorks.
 */
@SuppressWarnings({"PMD.AvoidUsingHardCodedIP", "PMD.AvoidDuplicateLiterals"})
final class DownloadPackageSliceTest {
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

    @BeforeEach
    void setUp() throws InterruptedException, ExecutionException, IOException {
        final Storage storage = new InMemoryStorage();
        this.saveFilesToStorage(storage);
        this.port = new RandomFreePort().value();
        this.npm = new NpmProxy(
            URI.create(String.format("http://127.0.0.1:%d", this.port)),
            DownloadPackageSliceTest.VERTX,
            storage
        );
    }

    @AfterAll
    static void tearDown() {
        DownloadPackageSliceTest.VERTX.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/ctx"})
    void downloadMetaWorks(final String pathprefix) {
        final PackagePath path = new PackagePath(
            pathprefix.replaceFirst("/", "")
        );
        try (
            VertxSliceServer server = new VertxSliceServer(
                DownloadPackageSliceTest.VERTX,
                new DownloadPackageSlice(this.npm, path),
                this.port
            )
        ) {
            server.start();
            final String url = String.format(
                "http://127.0.0.1:%d%s/@hello/simple-npm-project",
                this.port,
                pathprefix
            );
            final WebClient client = WebClient.create(DownloadPackageSliceTest.VERTX);
            final JsonObject json = client.getAbs(url).rxSend().blockingGet().body().toJsonObject();
            MatcherAssert.assertThat(
                json.getJsonObject("versions").getJsonObject("1.0.1")
                    .getJsonObject("dist").getString("tarball"),
                new IsEqual<>(
                    String.format(
                        "%s/-/@hello/simple-npm-project-1.0.1.tgz",
                        url
                    )
                )
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
        final String metajsonpath =
            "@hello/simple-npm-project/meta.json";
        storage.save(
            new Key.From(metajsonpath),
            new Content.From(
                new TestResource(
                    String.format("storage/%s", metajsonpath)
                ).asBytes()
            )
        ).get();
        storage.save(
            new Key.From("@hello", "simple-npm-project", "meta.meta"),
            new Content.From(
                Json.createObjectBuilder()
                    .add("last-modified", "2020-05-13T16:30:30+01:00")
                    .add("last-refreshed", "2020-05-13T16:30:30+01:00")
                    .build()
                    .toString()
                    .getBytes()
            )
        ).get();
    }
}
