package com.aldaviva.microblog_favorites.http;

import jakarta.ws.rs.client.Client;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.glassfish.jersey.client.internal.CustomHttpUrlConnector;
import org.glassfish.jersey.client.spi.Connector;

public class CustomHttpUrlConnectorProvider extends HttpUrlConnectorProvider {

	@Override
	protected Connector createHttpUrlConnector(
	    final Client client, final ConnectionFactory connectionFactory, final int chunkSize, final boolean fixLengthStreaming, final boolean setMethodWorkaround) {

		return new CustomHttpUrlConnector(client, connectionFactory, chunkSize, fixLengthStreaming, setMethodWorkaround);
	}

}
