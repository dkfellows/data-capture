package manchester.synbiochem.datacapture;

import static java.net.Proxy.Type.HTTP;
import static java.util.Collections.emptyList;
import static java.util.Collections.sort;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.FOUND;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.fromStatusCode;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.io.IOUtils.readLines;

import javax.ws.rs.core.Response.Status;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class SeekConnector {
	private Log log = LogFactory.getLog(SeekConnector.class);
	private static final String SEEK = "http://www.sysmo-db.org/2010/xml/rest";
	private static final String XLINK = "http://www.w3.org/1999/xlink";
	private static DocumentBuilderFactory dbf;
	static {
		dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
	}

	private DocumentBuilder parser() throws ParserConfigurationException {
		DocumentBuilder builder = dbf.newDocumentBuilder();
		builder.setErrorHandler(new DefaultHandler() {
			@Override
			public void warning(SAXParseException exception)
					throws SAXException {
				log.warn(exception.getMessage());
			}

			@Override
			public void error(SAXParseException exception) throws SAXException {
				log.error(exception.getMessage(), exception);
			}
		});
		return builder;
	}

	@XmlRootElement(name = "user")
	@XmlType(propOrder = {})
	public static class User {
		@XmlElement
		public String name;
		@XmlElement
		public Integer id;
		@XmlElement(required = true)
		@XmlSchemaType(name = "anyUri")
		public URL url;

		public User() {
		}

		User(Node node) throws MalformedURLException, DOMException {
			Element person = (Element) node;
			url = new URL(person.getAttributeNS(XLINK, "href"));
			id = Integer.parseInt(person.getAttribute("id"));
			name = person.getAttributeNS(XLINK, "title");
		}
	}

	@XmlRootElement(name = "assay")
	@XmlType(propOrder = {})
	public static class Assay {
		@XmlElement
		public String name;
		@XmlElement
		public Integer id;
		@XmlElement(required = true)
		@XmlSchemaType(name = "anyUri")
		public URL url;

		public Assay() {
		}

		Assay(Node node) throws MalformedURLException, DOMException {
			Element assay = (Element) node;
			url = new URL(assay.getAttributeNS(XLINK, "href"));
			id = Integer.parseInt(assay.getAttribute("id"));
			name = assay.getAttributeNS(XLINK, "title");
		}
	}

	@Value("${seek.project:5}")
	private Integer projectID;
	@Value("${seek.institution:0}")
	private Integer institution;
	private URL seek;
	private String credentials;
	private Proxy proxy;

	@Value("${seek.url}")
	public void setSeekLocation(String location) throws MalformedURLException {
		seek = new URL(location);
	}

	@Value("${seek.credentials}")
	public void setSeekCredentials(String credentials) {
		if ("IGNORE".equals(credentials)) {
			this.credentials = null;
			return;
		}
		
		this.credentials = "Basic "
				+ encodeBase64String(credentials.getBytes(Charset.forName("UTF-8")));
	}

	@Value("${outgoing.proxy}")
	public void setProxy(String proxy) {
		if ("IGNORE".equals(proxy)) {
			this.proxy = null;
			return;
		}
		String[] split = proxy.split(":");
		int port = Integer.parseInt(split[1]);
		this.proxy = new Proxy(HTTP, new InetSocketAddress(split[0], port));
	}

	private HttpURLConnection connect(String suffix) throws IOException {
		log.debug("getting " + suffix);
		URL url = new URL(seek, suffix);
		URLConnection conn;
		if (proxy == null)
			conn = url.openConnection();
		else {
			conn = url.openConnection(proxy);
			log.debug("used proxy configuration " + proxy);
		}
		if (credentials != null) {
			conn.addRequestProperty("Authorization", credentials);
			log.debug("added SEEK credentials to request context");
		}
		return (HttpURLConnection) conn;
	}

	private Document get(String suffix) throws IOException, SAXException,
			ParserConfigurationException {
		DocumentBuilder builder = parser();
		HttpURLConnection conn = connect(suffix);
		conn.setInstanceFollowRedirects(true);
		try (InputStream is = conn.getInputStream()) {
			return builder.parse(is, new URL(seek, suffix).toString());
		}
	}

	public static final int USERS_CACHE_LIFETIME_MS = 1000000;
	private List<User> users;
	private long usersTimestamp;

	public User getUser(URL userURL) {
		URI userURI;
		try {
			userURI = userURL.toURI();
		} catch (URISyntaxException e) {
			throw new WebApplicationException(e, BAD_REQUEST);
		}
		for (User user : getUsers())
			try {
				if (user.url.toURI().equals(userURI))
					return user;
			} catch (URISyntaxException e) {
				log.warn("bad URI returned from SEEK; skipping to next", e);
			}
		throw new WebApplicationException("no such user", BAD_REQUEST);
	}

	public List<User> getUsers() {
		long now = System.currentTimeMillis();
		if (usersTimestamp + USERS_CACHE_LIFETIME_MS < now) {
			try {
				users = getUserList();
			} catch (SAXException | IOException | ParserConfigurationException e) {
				log.warn("failed to get user list due to " + e);
				if (users == null)
					users = emptyList();
			}
			usersTimestamp = now;
		}
		return users;
	}

	private static final Comparator<User> userComparator = new Comparator<User>() {
		@Override
		public int compare(User o1, User o2) {
			return o1.name.compareTo(o2.name);
		}
	};
	private static final Comparator<Assay> assayComparator = new Comparator<Assay>() {
		@Override
		public int compare(Assay o1, Assay o2) {
			return o1.id - o2.id;
		}
	};

	protected List<User> getUserList() throws SAXException, IOException,
			ParserConfigurationException {
		Document d = get("/people.xml?page=all");
		Element items = (Element) d.getDocumentElement()
				.getElementsByTagNameNS(SEEK, "items").item(0);
		NodeList personElements = items.getElementsByTagNameNS(SEEK, "person");
		List<User> users = new ArrayList<>();
		for (int i = 0; i < personElements.getLength(); i++)
			users.add(new User(personElements.item(i)));
		sort(users, userComparator);
		return users;
	}

	public Assay getAssay(URL assayURL) {
		URI assayURI;
		try {
			assayURI = assayURL.toURI();
		} catch (URISyntaxException e) {
			throw new WebApplicationException(e, BAD_REQUEST);
		}
		for (Assay assay : getAssays())
			try {
				if (assay.url.toURI().equals(assayURI))
					return assay;
			} catch (URISyntaxException e) {
				log.warn("bad URI returned from SEEK; skipping to next", e);
			}
		throw new WebApplicationException("no such assay", BAD_REQUEST);
	}

	private List<Assay> cachedAssays = Collections.emptyList();
	public List<Assay> getAssays() {
		List<Assay> assays = new ArrayList<>();
		try {
			Document d = get("/assays.xml?page=all");
			Element items = (Element) d.getDocumentElement()
					.getElementsByTagNameNS(SEEK, "items").item(0);
			NodeList assayElements = items
					.getElementsByTagNameNS(SEEK, "assay");
			log.debug("found " + assayElements.getLength() + " assays");
			for (int i = 0; i < assayElements.getLength(); i++)
				assays.add(new Assay(assayElements.item(i)));
			sort(assays, assayComparator);
		} catch (IOException | SAXException | ParserConfigurationException e) {
			log.warn("falling back to old assay list due to " + e);
			return cachedAssays;
		}
		cachedAssays = assays;
		return assays;
	}

	private class Form {
		private static final String N = "\r\n";
		private static final String PREFIX = "------FormToken";
		private static final String CD = N
				+ "Content-Disposition: form-data; name=\"";
		private final StringBuilder b = new StringBuilder();
		private final String token;
		private byte[] bytes;

		public Form(String content) {
			String token;
			int tokenid = 10203040;
			do {
				token = PREFIX + (tokenid++);
			} while (content.indexOf(token) >= 0);
			this.token = token;
		}

		public void addField(String field) {
			addField(field, "");
		}

		public void addField(String field, Object value) {
			b.append(token).append(CD).append(field).append("\"" + N + N)
					.append(value).append(N);
		}

		public void addContent(String field, String name, String type,
				String content) {
			b.append(token).append(CD).append(field).append("\"; filename=\"")
					.append(name).append("\"" + N + N).append(content)
					.append(N);
		}

		public void build() {
			bytes = b.append(token).append("--" + N).toString()
					.getBytes(Charset.forName("UTF-8"));
		}

		public String contentType() {
			return "multipart/form-data; boundary=" + token;
		}

		public String length() {
			assert bytes != null : "form must be built before use";
			return Integer.toString(bytes.length);
		}

		public byte[] content() {
			assert bytes != null : "form must be built before use";
			return bytes;
		}
	}

	//TODO linkFileAsset(User user, Assay assay, String name,
	//                   String title, String type, URL content)
	public URL uploadFileAsset(User user, Assay assay, String name,
			String title, String type, String content) throws IOException {
		Form form = new Form(content);
		form.addField("utf8", "\u2713"); // ✓
		// b.addField("authenticity_token", "????");//TODO do we need this?
		form.addField("data_file[parent_name]");
		form.addField("data_file[is_with_sample]");
		form.addContent("content_blob[data]", name, type, content);
		form.addField("content_blob[data_url]");
		form.addField("content_blob[original_filename]");
		form.addField("url_checked", "false");
		form.addField("data_file[title]", title);
		form.addField("description");
		form.addField("possible_data_file_project_ids", projectID);
		// b.addField("data_file[project_ids][]");//WTF?!
		form.addField("data_file[project_ids][]", projectID);
		form.addField("sharing[permissions][contributor_types]", "[]");
		form.addField("sharing[permissions][values]", "{}");
		form.addField("sharing[access_type_0]", "0");
		form.addField("sharing[sharing_scope]", "2");// TODO hardcoded?
		form.addField("sharing[your_proj_access_type]", "2");// TODO hardcoded?
		form.addField("sharing[access_type_2]", "1");
		form.addField("sharing[access_type_4]", "2");
		form.addField("proj_project[select]");
		form.addField("proj_access_type_select", "0");
		form.addField("individual_people_access_type_select", "0");
		form.addField("tag_list");
		form.addField("attribution-typeahead");
		form.addField("attributions", "[]");
		form.addField("creator-typeahead");
		form.addField("creators", "[[\"" + user.name + "\"," + user.id + "]]");
		form.addField("adv_project_id");
		form.addField("adv_institution_id", institution);
		form.addField("data_file[other_creators]");
		form.addField("possible_publications", "0");
		form.addField("possible_assays", "1");// TODO hardcoded?
		form.addField("assay_ids[]", assay.id);
		form.addField("assay_relationship_type", "0"); // ?
		form.addField("possible_data_file_event_ids", "0");// ?
		form.addField("data_file[event_ids][]");
		form.addField("possible_data_file_sample_ids", "0");
		form.addField("data_file[sample_ids][]");
		form.build();

		try {
			HttpURLConnection c = connect("/data_files");
			try {
				c.setDoOutput(true);
				c.setRequestMethod("POST");
				c.setRequestProperty("Content-Type", form.contentType());
				c.setRequestProperty("Content-Length", form.length());
				c.connect();
				c.getOutputStream().write(form.content());
				switch (fromStatusCode(c.getResponseCode())) {
				case CREATED:
				case FOUND:
					return new URL(seek, c.getHeaderField("Location"));
				default:
					for (String line : readLines(c.getErrorStream())) {
						log.error("problem in file upload: " + line);
						break;
					}
					throw new WebApplicationException("upload failed",
							INTERNAL_SERVER_ERROR);
				}
			} finally {
				c.disconnect();
			}
		} catch (IOException e) {
			throw new WebApplicationException("HTTP error", e);
		}
	}

/*
	------WebKitFormBoundary9wdQWYBoXKHFO6nC
	Content-Disposition: form-data; name="authenticity_token"

	BASE64-ENCODED-TOKEN
*/
}
