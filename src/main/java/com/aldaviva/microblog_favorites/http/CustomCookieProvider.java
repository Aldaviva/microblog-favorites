package com.aldaviva.microblog_favorites.http;

import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Cookie;
import org.glassfish.jersey.client.internal.CustomHttpUrlConnector;
import org.glassfish.jersey.internal.LocalizationMessages;
import org.glassfish.jersey.message.internal.CookieProvider;
import org.glassfish.jersey.message.internal.StringBuilderUtils;

/**
 * Registered using {@code src/main/resources/META-INF/services/org.glassfish.jersey.spi.HeaderDelegateProvider}.
 * Seems to work without this? maybe only comma-separated values from {@link CustomHttpUrlConnector} are needed.
 */
@Singleton
public class CustomCookieProvider extends CookieProvider {

	@Override
	public String toString(final Cookie cookie) {
		if (cookie == null) {
			throw new IllegalArgumentException(LocalizationMessages.COOKIE_IS_NULL());
		}

		final StringBuilder b = new StringBuilder();

		b.append(cookie.getName()).append('=');
		StringBuilderUtils.appendQuotedIfWhitespace(b, cookie.getValue());

		return b.toString();
	}

}
