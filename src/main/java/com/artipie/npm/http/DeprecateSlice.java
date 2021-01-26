/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 artipie.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.npm.http;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rs.StandardRs;
import com.artipie.npm.PackageNameFromUrl;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonPatchBuilder;
import org.reactivestreams.Publisher;

/**
 * Slice to handle `npm deprecate` command requests.
 * @since 0.8
 */
public final class DeprecateSlice implements Slice {

    /**
     * Patter for `referer` header value.
     */
    static final Pattern HEADER = Pattern.compile("deprecate.*");

    /**
     * Abstract storage.
     */
    private final Storage storage;

    /**
     * Ctor.
     * @param storage Abstract storage
     */
    public DeprecateSlice(final Storage storage) {
        this.storage = storage;
    }

    @Override
    public Response response(
        final String line,
        final Iterable<Map.Entry<String, String>> iterable,
        final Publisher<ByteBuffer> publisher
    ) {
        final String pkg = new PackageNameFromUrl(line).value();
        final Key key = new Key.From(pkg, "meta.json");
        return new AsyncResponse(
            this.storage.exists(key).thenCompose(
                exists -> {
                    final CompletionStage<Response> res;
                    if (exists) {
                        res = new PublisherAs(publisher).asciiString()
                            .thenApply(str -> Json.createReader(new StringReader(str)).readObject())
                            .thenApply(json -> json.getJsonObject("versions"))
                            .thenCombine(
                                this.storage.value(key).thenCompose(
                                    pub -> new PublisherAs(pub).asciiString()
                                ).thenApply(
                                    str -> Json.createReader(new StringReader(str)).readObject()
                                ),
                                (body, meta) -> DeprecateSlice.deprecate(body, meta).toString()
                            ).thenCompose(
                                str -> this.storage.save(
                                    key, new Content.From(str.getBytes(StandardCharsets.UTF_8))
                                )
                            )
                            .thenApply(nothing -> StandardRs.OK);
                    } else {
                        res = CompletableFuture.completedFuture(StandardRs.NOT_FOUND);
                    }
                    return res;
                }
            )
        );
    }

    /**
     * Adds tag deprecated from request body to meta.json.
     * @param versions Versions json
     * @param meta Meta json from storage
     * @return Meta json with added deprecate tags
     */
    private static JsonObject deprecate(final JsonObject versions, final JsonObject meta) {
        final JsonPatchBuilder res = Json.createPatchBuilder();
        final String field = "deprecated";
        for (final String version : versions.keySet()) {
            if (versions.getJsonObject(version).containsKey(field)) {
                res.add(
                    String.format("/versions/%s/deprecated", version),
                    versions.getJsonObject(version).getString(field)
                );
            }
        }
        return res.build().apply(meta);
    }
}
