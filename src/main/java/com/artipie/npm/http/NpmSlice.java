/*
 * The MIT License (MIT) Copyright (c) 2020-2022 artipie.com
 * https://github.com/artipie/npm-adapter/LICENSE.txt
 */

package com.artipie.npm.http;

import com.artipie.asto.Storage;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.auth.Action;
import com.artipie.http.auth.Authentication;
import com.artipie.http.auth.BearerAuthSlice;
import com.artipie.http.auth.Permission;
import com.artipie.http.auth.Permissions;
import com.artipie.http.auth.TokenAuthentication;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rt.ByMethodsRule;
import com.artipie.http.rt.RtRule;
import com.artipie.http.rt.RtRulePath;
import com.artipie.http.rt.SliceRoute;
import com.artipie.http.slice.SliceDownload;
import com.artipie.http.slice.SliceSimple;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.reactivestreams.Publisher;

/**
 * NpmSlice is a http layer in npm adapter.
 *
 * @since 0.3
 * @todo #340:30min Implement `/npm` endpoint properly: for now `/npm` simply returns 200 OK
 *  status without any body. We need to figure out what information can (or should) be returned
 *  by registry on this request and add it. Here are several links that might be useful
 *  https://github.com/npm/cli
 *  https://github.com/npm/registry
 *  https://docs.npmjs.com/cli/v8
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle ClassFanOutComplexityCheck (500 lines)
 */
@SuppressWarnings("PMD.ExcessiveMethodLength")
public final class NpmSlice implements Slice {

    /**
     * Anonymous token auth for test purposes.
     */
    static final TokenAuthentication ANONYMOUS = tkn
        -> CompletableFuture.completedFuture(Optional.of(new Authentication.User("anonymous")));

    /**
     * Header name `npm-command`.
     */
    private static final String NPM_COMMAND = "npm-command";

    /**
     * Header name `referer`.
     */
    private static final String REFERER = "referer";

    /**
     * Route.
     */
    private final SliceRoute route;

    /**
     * Ctor with existing front and default parameters for free access.
     * @param base Base URL.
     * @param storage Storage for package
     */
    public NpmSlice(final URL base, final Storage storage) {
        this(base, storage, Permissions.FREE, NpmSlice.ANONYMOUS);
    }

    /**
     * Ctor.
     *
     * @param base Base URL.
     * @param storage Storage for package.
     * @param perms Access permissions.
     * @param auth Authentication.
     * @checkstyle ParameterNumberCheck (5 lines)
     */
    public NpmSlice(
        final URL base,
        final Storage storage,
        final Permissions perms,
        final TokenAuthentication auth) {
        this.route = new SliceRoute(
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.GET),
                    new RtRule.ByPath("/npm")
                ),
                new BearerAuthSlice(
                    new SliceSimple(new RsWithStatus(RsStatus.OK)),
                    auth,
                    new Permission.ByName(perms, Action.Standard.READ)
                )
            ),
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.PUT),
                    new RtRule.ByPath(AddDistTagsSlice.PTRN)
                ),
                new BearerAuthSlice(
                    new AddDistTagsSlice(storage),
                    auth,
                    new Permission.ByName(perms, Action.Standard.WRITE)
                )
            ),
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.DELETE),
                    new RtRule.ByPath(AddDistTagsSlice.PTRN)
                ),
                new BearerAuthSlice(
                    new DeleteDistTagsSlice(storage),
                    auth,
                    new Permission.ByName(perms, Action.Standard.WRITE)
                )
            ),
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.PUT),
                    new RtRule.Any(
                        new RtRule.ByHeader(NpmSlice.NPM_COMMAND, CliPublish.HEADER),
                        new RtRule.ByHeader(NpmSlice.REFERER, CliPublish.HEADER)
                    )
                ),
                new BearerAuthSlice(
                    new UploadSlice(new CliPublish(storage), storage),
                    auth,
                    new Permission.ByName(perms, Action.Standard.WRITE)
                )
            ),
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.PUT),
                    new RtRule.Any(
                        new RtRule.ByHeader(NpmSlice.NPM_COMMAND, DeprecateSlice.HEADER),
                        new RtRule.ByHeader(NpmSlice.REFERER, DeprecateSlice.HEADER)
                    )
                ),
                new BearerAuthSlice(
                    new DeprecateSlice(storage),
                    auth,
                    new Permission.ByName(perms, Action.Standard.WRITE)
                )
            ),
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.PUT),
                    new RtRule.Any(
                        new RtRule.ByHeader(NpmSlice.NPM_COMMAND, UnpublishPutSlice.HEADER),
                        new RtRule.ByHeader(NpmSlice.REFERER, UnpublishPutSlice.HEADER)
                    )
                ),
                new BearerAuthSlice(
                    new UnpublishPutSlice(storage),
                    auth,
                    new Permission.ByName(perms, Action.Standard.WRITE)
                )
            ),
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.PUT),
                    new RtRule.ByPath(CurlPublish.PTRN)
                ),
                new BearerAuthSlice(
                    new UploadSlice(new CurlPublish(storage), storage),
                    auth,
                    new Permission.ByName(perms, Action.Standard.WRITE)
                )
            ),
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.GET),
                    new RtRule.ByPath(".*/dist-tags$")
                ),
                new BearerAuthSlice(
                    new GetDistTagsSlice(storage),
                    auth,
                    new Permission.ByName(perms, Action.Standard.READ)
                )
            ),
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.GET),
                    new RtRule.ByPath(".*(?<!\\.tgz)$")
                ),
                new BearerAuthSlice(
                    new DownloadPackageSlice(base, storage),
                    auth,
                    new Permission.ByName(perms, Action.Standard.READ)
                )
            ),
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.GET),
                    new RtRule.ByPath(".*\\.tgz$")
                ),
                new BearerAuthSlice(
                    new SliceDownload(storage),
                    auth,
                    new Permission.ByName(perms, Action.Standard.READ)
                )
            ),
            new RtRulePath(
                new RtRule.All(
                    new ByMethodsRule(RqMethod.DELETE),
                    new RtRule.ByPath(UnpublishForceSlice.PTRN)
                ),
                new BearerAuthSlice(
                    new UnpublishForceSlice(storage),
                    auth,
                    new Permission.ByName(perms, Action.Standard.DELETE)
                )
            )
        );
    }

    @Override
    public Response response(
        final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        return this.route.response(line, headers, body);
    }
}
