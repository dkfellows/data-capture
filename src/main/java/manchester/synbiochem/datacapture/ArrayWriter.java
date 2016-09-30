package manchester.synbiochem.datacapture;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.Charset;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;

import org.json.JSONArray;

public class ArrayWriter implements MessageBodyWriter<JSONArray> {
	private static final MediaType JSON = new MediaType("application", "json");
	private static final Charset UTF8 = Charset.forName("UTF-8");

	@Override
	public boolean isWriteable(Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType) {
		if (!type.isAssignableFrom(JSONArray.class))
			return false;
		return mediaType.isCompatible(JSON);
	}

	@Override
	public long getSize(JSONArray t, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType) {
		return -1;
	}

	@Override
	public void writeTo(JSONArray t, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> httpHeaders,
			OutputStream entityStream) throws IOException,
			WebApplicationException {
		String data = t.toString();
		try (OutputStreamWriter osw = new OutputStreamWriter(entityStream, UTF8)) {
			osw.write(data);
		}
	}
}
