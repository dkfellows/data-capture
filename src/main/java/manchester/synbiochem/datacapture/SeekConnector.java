package manchester.synbiochem.datacapture;

import java.io.Serializable;
import java.net.URL;
import java.nio.charset.Charset;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;

/**
 * Class and bean responsible for managing the connections to SEEK.
 * 
 * @author Donal Fellows
 */
public class SeekConnector {
	static final Charset UTF8 = Charset.forName("UTF-8");

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
	}
}
