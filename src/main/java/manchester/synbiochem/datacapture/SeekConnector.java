package manchester.synbiochem.datacapture;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Class and bean responsible for managing the connections to SEEK.
 * 
 * @author Donal Fellows
 */
public class SeekConnector {
	static final Charset UTF8 = Charset.forName("UTF-8");
	private static final String XLINK = "http://www.w3.org/1999/xlink";

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

	@SuppressWarnings("serial")
	@XmlRootElement(name = "project")
	@XmlType(propOrder = {})
	public static class Project implements Serializable {
		@XmlElement
		public String name;
		@XmlElement
		public Integer id;
		@XmlElement(required = true)
		@XmlSchemaType(name = "anyUri")
		public URL url;

		public Project() {
		}
	}
}
