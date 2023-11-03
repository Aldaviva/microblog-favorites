package com.aldaviva.microblog_favorites.http;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.List;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.message.internal.CookieProvider;
import org.glassfish.jersey.message.internal.StringBuilderUtils;

/**
 * <p>Send cookies in a client request. A non-broken alternative to {@link CookieProvider}.</p>
 * 
 * <p>Register this with your Jersey {@link Client} or {@link ClientConfig} with
 * <blockquote><code>
 * clientConfig.register(UnfuckedCookieSerializer.class);
 * </code></blockquote></p>
 * 
 * Make sure that other filters which add cookies to requests run before this filter, otherwise they won't be serialized correctly.
 * You can do this by adding the annotation {@code @Priority(Priorities.AUTHENTICATION)} to the other filter.
 * 
 * <h2>Jersey default cookie serialization</h2>
 * 
 * <p>By default, Jersey will serialize cookies incorrectly:</p>
 * 
 * <blockquote><code>
 * Cookie: $Version=1;a=b,$Version=1;c=d
 * </code></blockquote>
 * 
 * This is incorrect, according to the <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cookie#directives">Mozilla Developer Network Web Docs</a>.
 * 
 * <h2>Correct cookie serialization</h2>
 * This is the way that MDN specifies cookies should be serialized. It's what web servers expect, and it's what this class emits.
 * 
 * <blockquote><code>
 * Cookie: a=b; c=d
 * </code></blockquote>
 */
@Priority(Priorities.HEADER_DECORATOR)
public class UnfuckedCookieSerializer implements ClientRequestFilter {

	@Override
	public void filter(final ClientRequestContext requestContext) throws IOException {
		final MultivaluedMap<String, Object> headers = requestContext.getHeaders();

		final List<Object> cookies = headers.get(HttpHeaders.COOKIE);
		if (cookies != null && !cookies.isEmpty()) {
			final StringBuilder headerValue = new StringBuilder();

			for (final Object item : cookies) {
				if (headerValue.length() != 0) {
					headerValue.append("; ");
				}

				if (item instanceof Cookie) {
					final Cookie cookie = (Cookie) item;

					headerValue.append(cookie.getName()).append('=');
					StringBuilderUtils.appendQuotedIfWhitespace(headerValue, cookie.getValue());
				} else {
					headerValue.append(item.toString());
				}

			}

			headers.putSingle(HttpHeaders.COOKIE, headerValue.toString());
		}
	}

}
