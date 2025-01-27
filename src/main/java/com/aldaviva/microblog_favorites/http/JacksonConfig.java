package com.aldaviva.microblog_favorites.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.internal.InternalProperties;
import org.glassfish.jersey.internal.util.PropertiesHelper;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.jackson.internal.FilteringJacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.JacksonFilteringFeature;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.base.JsonMappingExceptionMapper;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.base.JsonParseExceptionMapper;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.message.filtering.EntityFilteringFeature;

public abstract class JacksonConfig {

	private JacksonConfig() {
	}

	/**
	 * Like {@link JacksonFeature} but with a custom class instead of {@link JacksonJaxbJsonProvider}.
	 */
	public static class CustomJacksonFeature implements Feature {

		protected static final String JSON_FEATURE = CustomJacksonFeature.class.getSimpleName();

		private final Class<? extends JacksonJaxbJsonProvider> jacksonProviderClass;

		public CustomJacksonFeature() {
			this(CustomJacksonJaxbJsonProvider.class);
		}

		public CustomJacksonFeature(final Class<? extends JacksonJaxbJsonProvider> jacksonProviderClass) {
			this.jacksonProviderClass = jacksonProviderClass;
		}

		@Override
		public boolean configure(final FeatureContext context) {
			final Configuration config = context.getConfiguration();

			final String jsonFeature = CommonProperties.getValue(config.getProperties(), config.getRuntimeType(),
			    InternalProperties.JSON_FEATURE, JSON_FEATURE, String.class);
			// Other JSON providers registered.
			if (!JSON_FEATURE.equalsIgnoreCase(jsonFeature)) {
				return false;
			}

			// Disable other JSON providers.
			context.property(PropertiesHelper.getPropertyNameForRuntime(InternalProperties.JSON_FEATURE, config.getRuntimeType()), JSON_FEATURE);

			// Register Jackson.
			if (!config.isRegistered(jacksonProviderClass)) {
				// add the default Jackson exception mappers
				context.register(JsonParseExceptionMapper.class);
				context.register(JsonMappingExceptionMapper.class);

				if (EntityFilteringFeature.enabled(config)) {
					context.register(JacksonFilteringFeature.class);
					context.register(FilteringJacksonJaxbJsonProvider.class, MessageBodyReader.class, MessageBodyWriter.class);
				} else {
					context.register(jacksonProviderClass, MessageBodyReader.class, MessageBodyWriter.class);
				}
			}

			return true;
		}
	}

	public static class CustomJacksonJaxbJsonProvider extends JacksonJaxbJsonProvider {

		private final Set<MediaType> additionalMediaTypes = new HashSet<>();

		public CustomJacksonJaxbJsonProvider() {
			this(Collections.singleton(new MediaType("text", "plain", "UTF-8"))); //Nixplay returns JSON with "Content-Type: text/plain; charset=UTF-8"
		}

		public CustomJacksonJaxbJsonProvider(final Collection<MediaType> additionalMediaTypes) {
			this.additionalMediaTypes.addAll(additionalMediaTypes);
		}

		@Override
		protected boolean hasMatchingMediaType(final MediaType mediaType) {
			return super.hasMatchingMediaType(mediaType) || additionalMediaTypes.contains(mediaType);
		}

	}

	@Provider
	public static class CustomObjectMapperProvider implements ContextResolver<ObjectMapper> {

		public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

		static {
			OBJECT_MAPPER.registerModule(new JavaTimeModule());
			OBJECT_MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			OBJECT_MAPPER.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
		}

		@Override
		public ObjectMapper getContext(final Class<?> type) {
			return OBJECT_MAPPER;
		}
	}
}
