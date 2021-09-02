/*
 * The MIT License (MIT) Copyright (c) 2020-2021 artipie.com
 * https://github.com/artipie/npm-adapter/LICENSE.txt
 */
package com.artipie.npm.proxy.json;

import com.artipie.asto.test.TestResource;
import java.io.StringReader;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonObject;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.StringStartsWith;
import org.junit.jupiter.api.Test;

/**
 * Client package content test.
 *
 * @since 0.1
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class ClientContentTest {
    @Test
    public void getsValue() {
        final String url = "http://localhost";
        final String cached = new String(
            new TestResource("json/cached.json").asBytes()
        );
        final String transformed = new ClientContent(cached, url).value();
        final JsonObject json = Json.createReader(new StringReader(transformed)).readObject();
        final Set<String> vrsns = json.getJsonObject("versions").keySet();
        MatcherAssert.assertThat(
            "Could not find asset references",
            vrsns.isEmpty(),
            new IsEqual<>(false)
        );
        for (final String vers: vrsns) {
            MatcherAssert.assertThat(
                json.getJsonObject("versions").getJsonObject(vers)
                    .getJsonObject("dist").getString("tarball"),
                new StringStartsWith(url)
            );
        }
    }
}
