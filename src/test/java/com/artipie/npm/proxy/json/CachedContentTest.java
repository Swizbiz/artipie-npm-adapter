/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/npm-adapter/LICENSE.txt
 */
package com.artipie.npm.proxy.json;

import com.artipie.asto.test.TestResource;
import java.io.IOException;
import java.io.StringReader;
import javax.json.Json;
import javax.json.JsonObject;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Cached package content test.
 *
 * @since 0.1
 */
public class CachedContentTest {
    @Test
    public void getsValue() throws IOException {
        final String original = new String(
            new TestResource("json/original.json").asBytes()
        );
        final String transformed = new CachedContent(original, "asdas").value();
        final JsonObject json = Json.createReader(new StringReader(transformed)).readObject();
        MatcherAssert.assertThat(
            json.getJsonObject("versions").getJsonObject("1.0.0")
                .getJsonObject("dist").getString("tarball"),
            new IsEqual<>("/asdas/-/asdas-1.0.0.tgz")
        );
    }
}
