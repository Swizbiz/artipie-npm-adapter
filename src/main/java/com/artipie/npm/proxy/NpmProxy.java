/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/npm-adapter/LICENSE.txt
 */
package com.artipie.npm.proxy;

import com.artipie.asto.Storage;
import com.artipie.asto.rx.RxStorageWrapper;
import com.artipie.npm.proxy.model.NpmAsset;
import com.artipie.npm.proxy.model.NpmPackage;
import io.reactivex.Maybe;
import io.vertx.reactivex.core.Vertx;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;

/**
 * NPM Proxy.
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (200 lines)
 */
public class NpmProxy {
    /**
     * The Vertx instance.
     */
    private final Vertx vertx;

    /**
     * The storage.
     */
    private final NpmProxyStorage storage;

    /**
     * Remote repository client.
     */
    private final NpmRemote remote;

    /**
     * Ctor.
     * @param remote Uri remote
     * @param vertx Vertx instance
     * @param storage Adapter storage
     */
    public NpmProxy(final URI remote, final Vertx vertx, final Storage storage) {
        this(
            vertx,
            new RxNpmProxyStorage(new RxStorageWrapper(storage)),
            new HttpNpmRemote(remote, vertx)
        );
    }

    /**
     * Default-scoped ctor (for tests).
     * @param vertx Vertx instance
     * @param storage NPM storage
     * @param remote Remote repository client
     * @checkstyle ParameterNumberCheck (10 lines)
     */
    NpmProxy(
        final Vertx vertx,
        final NpmProxyStorage storage,
        final NpmRemote remote) {
        this.vertx = vertx;
        this.storage = storage;
        this.remote = remote;
    }

    /**
     * Retrieve package metadata.
     * @param name Package name
     * @return Package metadata (cached or downloaded from remote repository)
     * @checkstyle ReturnCountCheck (15 lines)
     */
    public Maybe<NpmPackage> getPackage(final String name) {
        return this.storage.getPackage(name).flatMap(
            pkg -> this.remotePackage(name).switchIfEmpty(Maybe.just(pkg))
        ).switchIfEmpty(Maybe.defer(() -> this.remotePackage(name)));
    }

    /**
     * Retrieve asset.
     * @param path Asset path
     * @return Asset data (cached or downloaded from remote repository)
     */
    public Maybe<NpmAsset> getAsset(final String path) {
        return this.storage.getAsset(path).switchIfEmpty(
            Maybe.defer(
                () -> this.vertx.fileSystem().rxCreateTempFile("npm-asset-", ".tmp")
                    .flatMapMaybe(
                        tmp -> this.remote.loadAsset(path, Paths.get(tmp)).flatMap(
                            asset -> this.storage.save(asset)
                                .andThen(Maybe.defer(() -> this.storage.getAsset(path)))
                                .doOnTerminate(() -> this.vertx.fileSystem().rxDelete(tmp))
                        )
                    )
            )
        );
    }

    /**
     * Close NPM Proxy adapter and underlying remote client.
     * @throws IOException when underlying remote client fails to close
     */
    public void close() throws IOException {
        this.remote.close();
    }

    /**
     * Get package from remote repository and save it to storage.
     * @param name Package name
     * @return Npm Package
     */
    private Maybe<NpmPackage> remotePackage(final String name) {
        final Maybe<NpmPackage> res;
        final Maybe<NpmPackage> pckg = this.remote.loadPackage(name);
        if (pckg == null) {
            res = Maybe.empty();
        } else {
            res = pckg.flatMap(
                pkg -> this.storage.save(pkg).andThen(Maybe.just(pkg))
            );
        }
        return res;
    }
}
