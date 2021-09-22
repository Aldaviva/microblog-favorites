package org.glassfish.jersey.client.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.client.HttpUrlConnectorProvider.ConnectionFactory;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.internal.util.PropertiesHelper;
import org.glassfish.jersey.internal.util.collection.UnsafeValue;
import org.glassfish.jersey.internal.util.collection.Values;
import org.glassfish.jersey.message.internal.Statuses;

/**
 * Like {@link HttpUrlConnector}, but without broken multi-cookie serialization (; vs ,).
 * The only difference between these two classes is {@link #setOutboundHeaders(MultivaluedMap, HttpURLConnection)}.
 */
public class CustomHttpUrlConnector extends HttpUrlConnector {

	private static final Logger LOGGER = Logger.getLogger(HttpUrlConnector.class.getName());

	private static final String ALLOW_RESTRICTED_HEADERS_SYSTEM_PROPERTY = "sun.net.http.allowRestrictedHeaders";

	// The list of restricted headers is extracted from sun.net.www.protocol.http.HttpURLConnection
	private static final String[] restrictedHeaders = {
	    "Access-Control-Request-Headers",
	    "Access-Control-Request-Method",
	    "Connection", /* close is allowed */
	    "Content-Length",
	    "Content-Transfer-Encoding",
	    "Host",
	    "Keep-Alive",
	    "Origin",
	    "Trailer",
	    "Transfer-Encoding",
	    "Upgrade",
	    "Via"
	};
	private static final Set<String> restrictedHeaderSet = new HashSet<>(restrictedHeaders.length);

	static {
		for (final String headerName : restrictedHeaders) {
			restrictedHeaderSet.add(headerName.toLowerCase(Locale.ROOT));
		}
	}

	private final ConnectionFactory connectionFactory;
	private final boolean setMethodWorkaround;
	private final int chunkSize;
	private final boolean fixLengthStreaming;
	private final boolean isRestrictedHeaderPropertySet;

	private final ConnectorExtension<HttpURLConnection, IOException> connectorExtension = new HttpUrlExpect100ContinueConnectorExtension();

	public CustomHttpUrlConnector(
	    final Client client, final ConnectionFactory connectionFactory, final int chunkSize, final boolean fixLengthStreaming,
	    final boolean setMethodWorkaround) {

		super(client, connectionFactory, chunkSize, fixLengthStreaming, setMethodWorkaround);
		this.connectionFactory = connectionFactory;
		this.chunkSize = chunkSize;
		this.fixLengthStreaming = fixLengthStreaming;
		this.setMethodWorkaround = setMethodWorkaround;

		isRestrictedHeaderPropertySet = Boolean.valueOf(AccessController.doPrivileged(
		    PropertiesHelper.getSystemProperty(ALLOW_RESTRICTED_HEADERS_SYSTEM_PROPERTY, "false")));
	}

	/**
	 * Modified by Ben: multiple cookie values must be separated by semicolons, not commas.
	 * Source: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cookie
	 *     A list of name-value pairs in the form of <cookie-name>=<cookie-value>. Pairs in the list are separated by a semicolon and a space ('; ').
	 * Also evidenced by real sites rejecting cookies from {@code HttpUrlConnection}, like Twitter and Nixplay.
	 */
	private void setOutboundHeaders(final MultivaluedMap<String, String> headers, final HttpURLConnection uc) {
		boolean restrictedSent = false;
		for (final Map.Entry<String, List<String>> header : headers.entrySet()) {
			final String headerName = header.getKey();
			String headerValue;

			final List<String> headerValues = header.getValue();
			if (headerValues.size() == 1) {
				headerValue = headerValues.get(0);
				uc.setRequestProperty(headerName, headerValue);
			} else {
				final StringBuilder b = new StringBuilder();
				boolean add = false;
				final String headerValueSeparator = HttpHeaders.COOKIE.equalsIgnoreCase(headerName) ? "; " : ","; //Added by Ben
				for (final Object value : headerValues) {
					if (add) {
						b.append(headerValueSeparator);
					}
					add = true;
					b.append(value);
				}
				headerValue = b.toString();
				uc.setRequestProperty(headerName, headerValue);
			}
			// if (at least one) restricted header was added and the allowRestrictedHeaders
			if (!isRestrictedHeaderPropertySet && !restrictedSent) {
				if (isHeaderRestricted(headerName, headerValue)) {
					restrictedSent = true;
				}
			}
		}
		if (restrictedSent) {
			LOGGER.warning(LocalizationMessages.RESTRICTED_HEADER_POSSIBLY_IGNORED(ALLOW_RESTRICTED_HEADERS_SYSTEM_PROPERTY));
		}
	}

	@Override
	public ClientResponse apply(final ClientRequest request) {
		try {
			return _apply(request);
		} catch (final IOException ex) {
			throw new ProcessingException(ex);
		}
	}

	@Override
	public Future<?> apply(final ClientRequest request, final AsyncConnectorCallback callback) {

		try {
			callback.response(_apply(request));
		} catch (final IOException ex) {
			callback.failure(new ProcessingException(ex));
		} catch (final Throwable t) {
			callback.failure(t);
		}

		return CompletableFuture.completedFuture(null);
	}

	private ClientResponse _apply(final ClientRequest request) throws IOException {
		final HttpURLConnection uc;

		uc = this.connectionFactory.getConnection(request.getUri().toURL());
		uc.setDoInput(true);

		final String httpMethod = request.getMethod();
		if (request.resolveProperty(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, setMethodWorkaround)) {
			setRequestMethodViaJreBugWorkaround(uc, httpMethod);
		} else {
			uc.setRequestMethod(httpMethod);
		}

		uc.setInstanceFollowRedirects(request.resolveProperty(ClientProperties.FOLLOW_REDIRECTS, true));

		uc.setConnectTimeout(request.resolveProperty(ClientProperties.CONNECT_TIMEOUT, uc.getConnectTimeout()));

		uc.setReadTimeout(request.resolveProperty(ClientProperties.READ_TIMEOUT, uc.getReadTimeout()));

		secureConnection(request.getClient(), uc);

		final Object entity = request.getEntity();
		Exception storedException = null;
		try {
			if (entity != null) {
				final RequestEntityProcessing entityProcessing = request.resolveProperty(
				    ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.class);

				final long length = request.getLengthLong();

				if (entityProcessing == null || entityProcessing != RequestEntityProcessing.BUFFERED) {
					if (fixLengthStreaming && length > 0) {
						uc.setFixedLengthStreamingMode(length);
					} else if (entityProcessing == RequestEntityProcessing.CHUNKED) {
						uc.setChunkedStreamingMode(chunkSize);
					}
				}
				uc.setDoOutput(true);

				if ("GET".equalsIgnoreCase(httpMethod)) {
					final Logger logger = Logger.getLogger(HttpUrlConnector.class.getName());
					if (logger.isLoggable(Level.INFO)) {
						logger.log(Level.INFO, LocalizationMessages.HTTPURLCONNECTION_REPLACES_GET_WITH_ENTITY());
					}
				}

				processExtentions(request, uc);

				request.setStreamProvider(contentLength -> {
					setOutboundHeaders(request.getStringHeaders(), uc);
					return uc.getOutputStream();
				});
				request.writeEntity();

			} else {
				setOutboundHeaders(request.getStringHeaders(), uc);
			}
		} catch (final IOException ioe) {
			storedException = handleException(request, ioe, uc);
		}

		final int code = uc.getResponseCode();
		final String reasonPhrase = uc.getResponseMessage();
		final Response.StatusType status = reasonPhrase == null ? Statuses.from(code) : Statuses.from(code, reasonPhrase);

		URI resolvedRequestUri = null;
		try {
			resolvedRequestUri = uc.getURL().toURI();
		} catch (final URISyntaxException e) {
			// if there is already an exception stored, the stored exception is what matters most
			if (storedException == null) {
				storedException = e;
			} else {
				storedException.addSuppressed(e);
			}
		}

		final ClientResponse responseContext = new ClientResponse(status, request, resolvedRequestUri);
		responseContext.headers(
		    uc.getHeaderFields()
		        .entrySet()
		        .stream()
		        .filter(stringListEntry -> stringListEntry.getKey() != null)
		        .collect(Collectors.toMap(Map.Entry::getKey,
		            Map.Entry::getValue)));

		try {
			final InputStream inputStream = getInputStream(uc);
			responseContext.setEntityStream(inputStream);
		} catch (final IOException ioe) {
			// allow at least a partial response in a ResponseProcessingException
			if (storedException == null) {
				storedException = ioe;
			} else {
				storedException.addSuppressed(ioe);
			}
		}

		if (storedException != null) {
			throw new ClientResponseProcessingException(responseContext, storedException);
		}

		return responseContext;
	}

	private void processExtentions(final ClientRequest request, final HttpURLConnection uc) {
		connectorExtension.invoke(request, uc);
	}

	/**
	 * Workaround for a bug in {@code HttpURLConnection.setRequestMethod(String)}
	 * The implementation of Sun/Oracle is throwing a {@code ProtocolException}
	 * when the method is not in the list of the HTTP/1.1 default methods.
	 * This means that to use e.g. {@code PROPFIND} and others, we must apply this workaround.
	 * <p/>
	 * See issue http://java.net/jira/browse/JERSEY-639
	 */
	private static void setRequestMethodViaJreBugWorkaround(
	    final HttpURLConnection httpURLConnection,
	    final String method) {
		try {
			httpURLConnection.setRequestMethod(method); // Check whether we are running on a buggy JRE
		} catch (final ProtocolException pe) {
			try {
				AccessController
				    .doPrivileged(new PrivilegedExceptionAction<Object>() {
					    @Override
					    public Object run() throws NoSuchFieldException,
					        IllegalAccessException {
						    try {
							    httpURLConnection.setRequestMethod(method);
							    // Check whether we are running on a buggy
							    // JRE
						    } catch (final ProtocolException pe) {
							    Class<?> connectionClass = httpURLConnection
							        .getClass();
							    try {
								    final Field delegateField = connectionClass.getDeclaredField("delegate");
								    delegateField.setAccessible(true);

								    final HttpURLConnection delegateConnection = (HttpURLConnection) delegateField.get(httpURLConnection);
								    setRequestMethodViaJreBugWorkaround(delegateConnection, method);
							    } catch (final NoSuchFieldException e) {
								    // Ignore for now, keep going
							    } catch (IllegalArgumentException | IllegalAccessException e) {
								    throw new RuntimeException(e);
							    }
							    try {
								    Field methodField;
								    while (connectionClass != null) {
									    try {
										    methodField = connectionClass
										        .getDeclaredField("method");
									    } catch (final NoSuchFieldException e) {
										    connectionClass = connectionClass
										        .getSuperclass();
										    continue;
									    }
									    methodField.setAccessible(true);
									    methodField.set(httpURLConnection, method);
									    break;
								    }
							    } catch (final Exception e) {
								    throw new RuntimeException(e);
							    }
						    }
						    return null;
					    }
				    });
			} catch (final PrivilegedActionException e) {
				final Throwable cause = e.getCause();
				if (cause instanceof RuntimeException) {
					throw (RuntimeException) cause;
				} else {
					throw new RuntimeException(cause);
				}
			}
		}
	}

	private IOException handleException(final ClientRequest request, final IOException ex, final HttpURLConnection uc) throws IOException {
		if (connectorExtension.handleException(request, uc, ex)) {
			return null;
		}
		if (uc.getResponseCode() == -1) {
			throw ex;
		} else {
			return ex;
		}
	}

	private static InputStream getInputStream(final HttpURLConnection uc) throws IOException {
		return new InputStream() {
			private final UnsafeValue<InputStream, IOException> in = Values.lazy(new UnsafeValue<InputStream, IOException>() {
				@Override
				public InputStream get() throws IOException {
					if (uc.getResponseCode() < Response.Status.BAD_REQUEST.getStatusCode()) {
						return uc.getInputStream();
					} else {
						final InputStream ein = uc.getErrorStream();
						return (ein != null) ? ein : new ByteArrayInputStream(new byte[0]);
					}
				}
			});

			private volatile boolean closed = false;

			/**
			 * The motivation for this method is to straighten up a behaviour of {@link sun.net.www.http.KeepAliveStream} which
			 * is used here as a backing {@link InputStream}. The problem is that its access methods (e.g., {@link
			 * sun.net.www.http.KeepAliveStream#read()}) do not throw {@link IOException} if the stream is closed. This behaviour
			 * contradicts with {@link InputStream} contract.
			 * <p/>
			 * This is a part of fix of JERSEY-2878
			 * <p/>
			 * Note that {@link java.io.FilterInputStream} also changes the contract of
			 * {@link java.io.FilterInputStream#read(byte[], int, int)} as it doesn't state that closed stream causes an {@link
			 * IOException} which might be questionable. Nevertheless, our contract is {@link InputStream} and as such, the
			 * stream we're offering must comply with it.
			 *
			 * @throws IOException when the stream is closed.
			 */
			private void throwIOExceptionIfClosed() throws IOException {
				if (closed) {
					throw new IOException("Stream closed");
				}
			}

			@Override
			public int read() throws IOException {
				final int result = in.get().read();
				throwIOExceptionIfClosed();
				return result;
			}

			@Override
			public int read(final byte[] b) throws IOException {
				final int result = in.get().read(b);
				throwIOExceptionIfClosed();
				return result;
			}

			@Override
			public int read(final byte[] b, final int off, final int len) throws IOException {
				final int result = in.get().read(b, off, len);
				throwIOExceptionIfClosed();
				return result;
			}

			@Override
			public long skip(final long n) throws IOException {
				final long result = in.get().skip(n);
				throwIOExceptionIfClosed();
				return result;
			}

			@Override
			public int available() throws IOException {
				final int result = in.get().available();
				throwIOExceptionIfClosed();
				return result;
			}

			@Override
			public void close() throws IOException {
				try {
					in.get().close();
				} finally {
					closed = true;
				}
			}

			@Override
			public void mark(final int readLimit) {
				try {
					in.get().mark(readLimit);
				} catch (final IOException e) {
					throw new IllegalStateException("Unable to retrieve the underlying input stream.", e);
				}
			}

			@Override
			public void reset() throws IOException {
				in.get().reset();
				throwIOExceptionIfClosed();
			}

			@Override
			public boolean markSupported() {
				try {
					return in.get().markSupported();
				} catch (final IOException e) {
					throw new IllegalStateException("Unable to retrieve the underlying input stream.", e);
				}
			}
		};
	}

	private boolean isHeaderRestricted(String name, final String value) {
		name = name.toLowerCase(Locale.ROOT);
		return name.startsWith("sec-")
		    || restrictedHeaderSet.contains(name)
		        && !("connection".equalsIgnoreCase(name) && "close".equalsIgnoreCase(value));
	}
}
