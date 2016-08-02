package manchester.synbiochem.datacapture;

import static java.net.Proxy.Type.HTTP;
import static java.util.Collections.emptyList;
import static java.util.Collections.sort;
import static java.util.regex.Pattern.compile;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.fromStatusCode;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.io.IOUtils.readLines;

import javax.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
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
	static final Charset UTF8 = Charset.forName("UTF-8");
	private Log log = LogFactory.getLog(SeekConnector.class);
	private static final String SEEK = "http://www.sysmo-db.org/2010/xml/rest";
	private static final String XLINK = "http://www.w3.org/1999/xlink";
	private static final String DC = "http://purl.org/dc/elements/1.1/";
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

	@SuppressWarnings("serial")
	@XmlRootElement(name = "user")
	@XmlType(propOrder = {})
	public static class User implements Serializable {
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

	@SuppressWarnings("serial")
	@XmlRootElement(name = "assay")
	@XmlType(propOrder = {})
	public static class Assay implements Serializable {
		@XmlElement
		public String name;
		@XmlElement
		public Integer id;
		@XmlElement(required = true)
		@XmlSchemaType(name = "anyUri")
		public URL url;
		@XmlElement(name = "project-name")
		String projectName;
		@XmlElement(name = "project-url")
		@XmlSchemaType(name = "anyUri")
		public URL projectUrl;
		@XmlElement(name = "investigation-name")
		String investigationName;
		@XmlElement(name = "investigation-url")
		@XmlSchemaType(name = "anyUri")
		public URL investigationUrl;
		@XmlElement(name = "study-name")
		String studyName;
		@XmlElement(name = "study-url")
		@XmlSchemaType(name = "anyUri")
		public URL studyUrl;

		public Assay() {
		}

		Assay(Node node) throws MalformedURLException, DOMException {
			Element assay = (Element) node;
			url = new URL(assay.getAttributeNS(XLINK, "href"));
			id = Integer.parseInt(assay.getAttribute("id"));
			name = assay.getAttributeNS(XLINK, "title");
		}
	}

	@SuppressWarnings("serial")
	@XmlRootElement(name = "study")
	@XmlType(propOrder = {})
	public static class Study implements Serializable {
		@XmlElement
		public String name;
		@XmlElement
		public Integer id;
		@XmlElement(required = true)
		@XmlSchemaType(name = "anyUri")
		public URL url;
		@XmlElement(name = "project-name")
		String projectName;
		@XmlElement(name = "project-url")
		@XmlSchemaType(name = "anyUri")
		public URL projectUrl;
		@XmlElement(name = "investigation-name")
		String investigationName;
		@XmlElement(name = "investigation-url")
		@XmlSchemaType(name = "anyUri")
		public URL investigationUrl;

		public Study() {
		}

		Study(Node node) throws MalformedURLException, DOMException {
			Element study = (Element) node;
			url = new URL(study.getAttributeNS(XLINK, "href"));
			id = Integer.parseInt(study.getAttribute("id"));
			name = study.getAttributeNS(XLINK, "title");
		}
	}

	@Value("${seek.project:5}")
	private Integer projectID;
	@Value("${seek.institution:0}")
	private Integer institution;
	@Value("${seek.license:CC-BY-4.0}")
	private String license;
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
				+ encodeBase64String(credentials.getBytes(UTF8));
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
		URI userURI = asURI(userURL);
		for (User user : getUsers())
			try {
				if (asURI(user.url).equals(userURI))
					return user;
			} catch (WebApplicationException e) {
				log.warn("bad URI returned from SEEK; skipping to next", e);
			}
		throw new WebApplicationException("no such user", BAD_REQUEST);
	}

	public List<User> getUsers() {
		long now = System.currentTimeMillis();
		if (usersTimestamp + USERS_CACHE_LIFETIME_MS < now) {
			log.info("filling users cache");
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
	private static final Comparator<Study> studyComparator = new Comparator<Study>() {
		@Override
		public int compare(Study o1, Study o2) {
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

	private URI asURI(URL url) {
		try {
			String s = url.toURI().toString();
			switch (seek.getProtocol()) {
			case "http":
				s = s.replaceFirst("(?i)^https:", "http:");
				break;
			case "https":
				s = s.replaceFirst("(?i)^http:", "https:");
				break;
			}
			return new URI(s);
		} catch (URISyntaxException e) {
			throw new WebApplicationException(e, BAD_REQUEST);
		}
	}

	public Assay getAssay(URL assayURL) {
		URI assayURI = asURI(assayURL);
		for (Assay assay : getAssays())
			try {
				if (asURI(assay.url).equals(assayURI))
					return assay;
			} catch (WebApplicationException e) {
				log.warn("bad URI returned from SEEK; skipping to next", e);
			}
		throw new WebApplicationException("no such assay", BAD_REQUEST);
	}

	public Study getStudy(URL studyURL) {
		URI studyURI = asURI(studyURL);
		for (Study study : getStudies())
			try {
				if (asURI(study.url).equals(studyURI))
					return study;
			} catch (WebApplicationException e) {
				log.warn("bad URI returned from SEEK; skipping to next", e);
			}
		throw new WebApplicationException("no such study", BAD_REQUEST);
	}

	private static List<Element> getElements(Element parent, String namespace,
			String... localName) {
		List<Element> children = Collections.singletonList(parent);
		for (String n : localName) {
			NodeList nl = children.get(0).getElementsByTagNameNS(namespace, n);
			children = new ArrayList<>(nl.getLength());
			for (int i = 0; i < nl.getLength(); i++)
				children.add((Element) nl.item(i));
		}
		return children;
	}

	private static final String DEFAULT_PROJECT = "http://synbiochem.fairdomhub.org/projects/5";

	private void addExtra(Assay a) {
		Element doc;
		try {
			log.info("populating extra structure for " + a.url);
			String u = a.url.toString().replaceAll("^.*//[^/]*/", "");
			doc = get(u + ".xml").getDocumentElement();
		} catch (Exception e) {
			log.warn("problem retrieving information from assay " + a.url, e);
			return;
		}
		try {
			for (Element inv : getElements(doc, SEEK, "associated",
					"investigations", "investigation")) {
				a.investigationName = inv.getAttributeNS(XLINK, "title");
				a.investigationUrl = new URL(inv.getAttributeNS(XLINK, "href"));
				break;
			}
			for (Element std : getElements(doc, SEEK, "associated", "studies",
					"study")) {
				a.studyName = std.getAttributeNS(XLINK, "title");
				a.studyUrl = new URL(std.getAttributeNS(XLINK, "href"));
				break;
			}
			for (Element prj : getElements(doc, SEEK, "associated", "projects",
					"project")) {
				String name = prj.getAttributeNS(XLINK, "title");
				if (!name.equalsIgnoreCase("synbiochem")) {
					a.projectName = name;
					a.projectUrl = new URL(prj.getAttributeNS(XLINK, "href"));
					break;
				}
			}
		} catch (Exception e) {
			log.warn("problem when filling in information from assay " + a.url, e);
		}
		if (a.investigationName == null)
			a.investigationName = "UNKNOWN";
		if (a.studyName == null)
			a.studyName = "UNKNOWN";
		if (a.projectName == null) {
			a.projectName = "SynBioChem";
			try {
				a.projectUrl = new URL(DEFAULT_PROJECT);
			} catch (MalformedURLException e) {
				// Should be unreachable
			}
		}
	}

	private void addExtra(Study s) {
		Element doc;
		try {
			log.info("populating extra structure for " + s.url);
			String u = s.url.toString().replaceAll("^.*//[^/]*/", "");
			doc = get(u + ".xml").getDocumentElement();
		} catch (Exception e) {
			log.warn("problem retrieving information from assay " + s.url, e);
			return;
		}
		try {
			for (Element inv : getElements(doc, SEEK, "associated",
					"investigations", "investigation")) {
				s.investigationName = inv.getAttributeNS(XLINK, "title");
				s.investigationUrl = new URL(inv.getAttributeNS(XLINK, "href"));
				break;
			}
			for (Element prj : getElements(doc, SEEK, "associated", "projects",
					"project")) {
				String name = prj.getAttributeNS(XLINK, "title");
				if (!name.equalsIgnoreCase("synbiochem")) {
					s.projectName = name;
					s.projectUrl = new URL(prj.getAttributeNS(XLINK, "href"));
					break;
				}
			}
		} catch (Exception e) {
			log.warn("problem when filling in information from assay " + s.url, e);
		}
		if (s.investigationName == null)
			s.investigationName = "UNKNOWN";
		if (s.projectName == null) {
			s.projectName = "SynBioChem";
			try {
				s.projectUrl = new URL(DEFAULT_PROJECT);
			} catch (MalformedURLException e) {
				// Should be unreachable
			}
		}
	}

	private static int CACHE_TIME = 150 * 1000; // 2.5 minutes
	private Long assayCacheTimestamp;
	private List<Assay> cachedAssays = Collections.emptyList();

	public List<Assay> getAssays() {
		long now = System.currentTimeMillis();
		if (assayCacheTimestamp != null && assayCacheTimestamp + CACHE_TIME > now) {
			return cachedAssays;
		}
		log.info("filling assays cache");
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
		} catch (IOException | SAXException | ParserConfigurationException e) {
			log.warn("falling back to old assay list due to " + e);
			return cachedAssays;
		}
		for (Assay assay : assays)
			addExtra(assay);
		sort(assays, assayComparator);
		cachedAssays = assays;
		assayCacheTimestamp = now;
		return assays;
	}

	private Long studyCacheTimestamp;
	private List<Study> cachedStudies = Collections.emptyList();

	public List<Study> getStudies() {
		long now = System.currentTimeMillis();
		if (studyCacheTimestamp != null && studyCacheTimestamp + CACHE_TIME > now) {
			return cachedStudies;
		}
		log.info("filling studies cache");
		List<Study> studies = new ArrayList<>();
		try {
			Document d = get("/studies.xml?page=all");
			Element items = (Element) d.getDocumentElement()
					.getElementsByTagNameNS(SEEK, "items").item(0);
			NodeList studyElements = items
					.getElementsByTagNameNS(SEEK, "study");
			log.debug("found " + studyElements.getLength() + " studies");
			for (int i = 0; i < studyElements.getLength(); i++)
				studies.add(new Study(studyElements.item(i)));
		} catch (IOException | SAXException | ParserConfigurationException e) {
			log.warn("falling back to old study list due to " + e);
			return cachedStudies;
		}
		for (Study study : studies)
			addExtra(study);
		sort(studies, studyComparator);
		cachedStudies = studies;
		studyCacheTimestamp = now;
		return studies;
	}

	private String getInstitutionName() {
		try {
			Element d = get("/institutions/" + institution + ".xml")
					.getDocumentElement();
			return d.getElementsByTagNameNS(DC, "title").item(0)
					.getTextContent();
		} catch (IOException | SAXException | ParserConfigurationException e) {
			log.error("unexpected problem when fetching institution info", e);
			return institution + " (unsafe)";
		}
	}

	@PostConstruct
	private void firstFetch() {
		// write this information into the log, deliberately
		log.info("there are " + getUsers().size() + " users");
		log.info("there are " + getStudies().size() + " studies");
		log.info("there are " + getAssays().size() + " assays");
		log.info("institution is set to " + getInstitutionName());
	}

	private String getAuthToken() throws IOException {
		HttpURLConnection c = connect("/data_files/new");
		c.connect();
		// We're parsing HTML with regexps! Watch out, Tony the Pony!
		Pattern p = compile("<meta\\s+content=\"(.+?)\"\\s+name=\"csrf-token\"\\s*/>");
		try (InputStream is = c.getInputStream()) {
			for (String s : IOUtils.readLines(is)) {
				Matcher m = p.matcher(s);
				if (m.find())
					return m.group(1);
			}
		}
		throw new IOException("no authenticity token found");
	}

	private static Status postForm(HttpURLConnection c, MultipartFormData form)
			throws IOException {
		c.setInstanceFollowRedirects(false);
		c.setDoOutput(true);
		c.setRequestMethod("POST");
		c.setRequestProperty("Content-Type", form.contentType());
		c.setRequestProperty("Content-Length", form.length());
		c.connect();
		try (OutputStream os = c.getOutputStream()) {
			os.write(form.content());
		}
		return fromStatusCode(c.getResponseCode());
	}

	private void readErrorFromConnection(HttpURLConnection c, String message,
			String logPrefix) throws IOException {
		InputStream errors = c.getErrorStream();
		if (errors != null) {
			for (String line : readLines(errors))
				log.error(logPrefix + ": " + line);
			errors.close();
		}
		throw new WebApplicationException(String.format(message,
				c.getResponseCode(), c.getResponseMessage()),
				INTERNAL_SERVER_ERROR);
	}

	public URL createExperimentalAssay(User user, Study study, 
			String description, String title) {
		try {
			MultipartFormData form = makeAssayCreateForm(user, study, title,
					description, JERM_EXPERIMENT);
			log.info("creating experimental assay with title " + title);
			HttpURLConnection c = connect("/assays");
			try {
				switch (postForm(c, form)) {
				case CREATED:
				case FOUND:
					/*
					 * Invalidate the assay cache; we know there's something new
					 * not in it.
					 */
					assayCacheTimestamp = null;
					URL url = new URL(seek, c.getHeaderField("Location"));
					log.info("assay created with location " + url);
					return url;
				default:
					readErrorFromConnection(c, "problem in form post",
							"write to SEEK failed with code %d: %s");
				case OK:
					return null;
				}
			} finally {
				c.disconnect();
			}
		} catch (IOException e) {
			throw new WebApplicationException("HTTP error", e);
		}
	}

	public URL linkFileAsset(User user, Assay assay, String description,
			String title, URI location) {
		try {
			MultipartFormData form = makeFileLinkForm(user, assay, location,
					description, title);
			log.info("creating linked asset with title '" + title
					+ "' from originating location " + location);
			HttpURLConnection c = connect("/data_files");
			try {
				switch (postForm(c, form)) {
				case CREATED:
				case FOUND:
					URL url = new URL(seek, c.getHeaderField("Location"));
					log.info("linked asset at " + url);
					return url;
				default:
					readErrorFromConnection(c, "problem in file link",
							"link failed with code %d: %s");
				case OK:
					return null;
				}
			} finally {
				c.disconnect();
			}
		} catch (IOException e) {
			throw new WebApplicationException("HTTP error", e);
		}
	}

	public URL uploadFileAsset(User user, Assay assay, String name,
			String description, String title, String type, String content) {
		try {
			MultipartFormData form = makeFileUploadForm(user, assay, name,
					description, title, type, content);
			log.info("creating uploaded asset with title '" + title
					+ "' and filename " + name);
			HttpURLConnection c = connect("/data_files");
			try {
				switch (postForm(c, form)) {
				case CREATED:
				case FOUND:
					URL url = new URL(seek, c.getHeaderField("Location"));
					log.info("uploaded asset at " + url);
					return url;
				default:
					readErrorFromConnection(c, "problem in file upload",
							"upload failed with code %d: %s");
				case OK:
					return null;
				}
			} finally {
				c.disconnect();
			}
		} catch (IOException e) {
			throw new WebApplicationException("HTTP error", e);
		}
	}

	private void addAuthToForm(MultipartFormData form) throws IOException {
		form.addField("utf8", "\u2713"); // ✓
		form.addField("authenticity_token", getAuthToken());
	}

	private void addCreatorsToForm(MultipartFormData form, User... users) {
		StringBuilder sb = new StringBuilder("[");

		String sep = "";
		for (User user : users){
			sb.append(sep).append("[\"").append(user.name).append("\",")
					.append(user.id).append("]");
			sep = ",";
		}
		sb.append("]");

		form.addField("creator-typeahead");
		form.addField("creators", sb);
	}

	private void addPermissionsToForm(MultipartFormData form) {
		form.addField("sharing[permissions][contributor_types]", "[]");
		form.addField("sharing[permissions][values]", "{}");
		form.addField("sharing[access_type_0]", "0");
		form.addField("sharing[sharing_scope]", "2");
		form.addField("sharing[your_proj_access_type]", "4");
		form.addField("sharing[access_type_2]", "1");
		form.addField("sharing[access_type_4]", "1");
		form.addField("proj_project[select]");
		form.addField("proj_access_type_select", "0");
		form.addField("individual_people_access_type_select", "0");
	}

	private static final String JERM_EXPERIMENT = "http://www.mygrid.org.uk/ontology/JERMOntology#Experimental_assay_type";
	private static final String JERM_TECH = "http://www.mygrid.org.uk/ontology/JERMOntology#Technology_type";
	private static final int ASSAY_CLASS_ID = 1;// hardcoded?

	private MultipartFormData makeAssayCreateForm(User user, Study study,
			String title, String description, String assayType) throws IOException {
		MultipartFormData form = new MultipartFormData("1234567890");
		addAuthToForm(form);
		form.addField("assay[create_from_asset]");
		form.addField("assay[title]", title);
		form.addField("assay[description]", description);
		form.addField("assay[study_id]", study.id);
		form.addField("assay[assay_class_id]", ASSAY_CLASS_ID);
		form.addField("assay[assay_type_uri]", assayType);
		form.addField("assay[technology_type_uri]", JERM_TECH);
		form.addField("possible_organisms", 0);
		form.addField("culture_growth", "Not specified");// ?
		addPermissionsToForm(form);
		form.addField("tag_list");
		addCreatorsToForm(form, user);
		form.addField("adv_project_id");
		form.addField("adv_institution_id", institution);
		form.addField("assay[other_creators]");
		form.addField("possible_sops", 0);
		form.addField("possible_publications", POSSIBLE_PUBLICATIONS);
		form.build();
		return form;
	}

	private static final int POSSIBLE_PUBLICATIONS = 0;// TODO hardcoded?
	private static final int ASSAY_RELATIONSHIP_TYPE = 0;// TODO hardcoded?
	private static final int POSSIBLE_DATA_FILE_EVENT_IDS = 0;// TODO hardcoded?

	private MultipartFormData makeFileUploadForm(User user, Assay assay,
			String name, String description, String title, String type,
			String content) throws IOException {
		MultipartFormData form = new MultipartFormData(content);
		addAuthToForm(form);
		form.addField("data_file[parent_name]");
		form.addField("data_file[is_with_sample]");
		form.addContent("content_blobs[][data]", name, type, content);
		form.addField("content_blobs[][data_url]");
		form.addField("content_blobs[][original_filename]");
		form.addField("url_checked", false);
		form.addField("data_file[title]", title);
		form.addField("data_file[description]", description);
		form.addField("possible_data_file_project_ids", projectID);
		form.addField("data_file[project_ids][]");
		form.addField("data_file[project_ids][]", projectID);
		form.addField("data_file[license]", license);
		addPermissionsToForm(form);
		form.addField("tag_list");
		form.addField("attribution-typeahead");
		form.addField("attributions", "[]");
		addCreatorsToForm(form, user);
		form.addField("adv_project_id");
		form.addField("adv_institution_id", institution);
		form.addField("data_file[other_creators]");
		form.addField("possible_publications", POSSIBLE_PUBLICATIONS);
		form.addField("possible_assays", assay.id);
		form.addField("assay_ids[]", assay.id);
		form.addField("assay_relationship_type", ASSAY_RELATIONSHIP_TYPE);
		form.addField("possible_data_file_event_ids", POSSIBLE_DATA_FILE_EVENT_IDS);
		form.addField("data_file[event_ids][]");
		form.build();
		return form;
	}

	private MultipartFormData makeFileLinkForm(User user, Assay assay,
			URI location, String description, String title) throws IOException {
		MultipartFormData form = new MultipartFormData("12345678901234567890");
		addAuthToForm(form);
		form.addField("data_file[parent_name]");
		form.addField("data_file[is_with_sample]");
		form.addContent("content_blobs[][data]", "", "application/octet-stream", "");
		form.addField("content_blobs[][data_url]", location);
		String name = location.getPath().replaceFirst(".*/", "");
		form.addField("content_blobs[][original_filename]", name);
		form.addField("url_checked", true);
		form.addField("data_file[title]", title);
		form.addField("data_file[description]", description);
		form.addField("possible_data_file_project_ids", projectID);
		form.addField("data_file[project_ids][]");
		form.addField("data_file[project_ids][]", projectID);
		form.addField("data_file[license]", license);
		addPermissionsToForm(form);
		form.addField("tag_list");
		form.addField("attribution-typeahead");
		form.addField("attributions", "[]");
		addCreatorsToForm(form, user);
		form.addField("adv_project_id");
		form.addField("adv_institution_id", institution);
		form.addField("data_file[other_creators]");
		form.addField("possible_publications", POSSIBLE_PUBLICATIONS);
		form.addField("possible_assays", assay.id);
		form.addField("assay_ids[]", assay.id);
		form.addField("assay_relationship_type", ASSAY_RELATIONSHIP_TYPE);
		form.addField("possible_data_file_event_ids", POSSIBLE_DATA_FILE_EVENT_IDS);
		form.addField("data_file[event_ids][]");
		form.build();
		return form;
	}
}
