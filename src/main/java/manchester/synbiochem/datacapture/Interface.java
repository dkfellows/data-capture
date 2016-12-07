package manchester.synbiochem.datacapture;

import static manchester.synbiochem.datacapture.Constants.JSON;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

public interface Interface {
	/** Common elements of API URI path names. */
	interface Paths {
		String ROOT = "/";
		String USERS = "users";
		String PROJECTS = "projects";
		String DIR = "dir";
		String TREE = "tree";
		String TASKS = "tasks";
	}

	static final Charset UTF8 = Charset.forName("UTF-8");
	@GET
	@Path(Paths.ROOT)
	@Produces("text/plain")
	String getStatus();

	@GET
	@Path(Paths.ROOT)
	@Produces(JSON)
	Description describe(@Context UriInfo ui);

	@GET
	@Path(Paths.USERS)
	@Produces(JSON)
	UserList users();

	@GET
	@Path(Paths.PROJECTS)
	@Produces(JSON)
	ProjectList projects();

	@GET
	@Path(Paths.DIR)
	@Produces(JSON)
	DirectoryList dirs(@Context UriInfo ui);

	@GET
	@Path(Paths.DIR + "/{dir:.+}")
	@Produces(JSON)
	Response dirs(@PathParam("dir") String dir, @Context UriInfo ui);

	@GET
	@Path(Paths.TREE)
	@Produces(JSON)
	Response tree(@QueryParam("id") @DefaultValue("#") String id,
			@Context UriInfo ui);

	@GET
	@Path(Paths.TASKS)
	@Produces(JSON)
	ArchiveTaskList tasks(@Context UriInfo ui);

	@GET
	@Path(Paths.TASKS + "/{id}")
	@Produces(JSON)
	Response task(@PathParam("id") String id);

	@POST
	@Path(Paths.TASKS)
	@Consumes(JSON)
	@Produces(JSON)
	Response createTask(ArchiveTask proposedTask, @Context UriInfo ui);

	@DELETE
	@Path(Paths.TASKS + "/{id}")
	@Produces(JSON)
	Response deleteTask(@PathParam("id") String id, @Context UriInfo ui);

	@XmlRootElement(name = "description")
	class Description {
		@XmlElement
		URI users;
		@XmlElement
		URI assays;
		@XmlElement
		URI directories;
		@XmlElement
		URI tasks;
	}

	@XmlRootElement(name = "users")
	@XmlSeeAlso(User.class)
	class UserList {
		@XmlElement(name = "user")
		public List<User> users = new ArrayList<>();
	}

	@XmlRootElement(name = "studies")
	@XmlSeeAlso(Study.class)
	class StudyList {
		@XmlElement(name = "study")
		public List<Study> studies = new ArrayList<>();
	}

	@XmlRootElement(name = "assays")
	@XmlSeeAlso(Assay.class)
	class AssayList {
		@XmlElement(name = "assay")
		public List<Assay> assays = new ArrayList<>();
	}

	@XmlRootElement(name = "projects")
	@XmlSeeAlso(Project.class)
	class ProjectList {
		@XmlElement(name = "project")
		public List<Project> projects = new ArrayList<>();
	}

	@XmlRootElement(name = "directories")
	class DirectoryList {
		@XmlElement(name = "directory-entry")
		public List<DirectoryEntry> dirs = new ArrayList<>();
	}

	@XmlType
	class DirectoryEntry {
		private static final SimpleDateFormat ISO8601;
		static {
			ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
		}
		public DirectoryEntry() {}
		DirectoryEntry(String d) {
			File f = new File(d);
			name = f.getName();
			if (f.exists()) {
				synchronized(ISO8601) {
					modTime = ISO8601.format(new Date(f.lastModified()));
				}
				if (f.isDirectory())
					type = "directory";
				else if (f.isFile())
					type = "file";
			}
			id = "dir_" + md5Hex(f.getAbsolutePath());
		}
		DirectoryEntry(File f, UriBuilder ub) {
			name = f.getName();
			if (f.exists()) {
				synchronized(ISO8601) {
					modTime = ISO8601.format(new Date(f.lastModified()));
				}
				if (f.isDirectory())
					type = "directory";
				else if (f.isFile())
					type = "file";
			}
			id = "dir_" + md5Hex(f.getAbsolutePath());
			uri = ub.clone().path("{name}").build(f.getName()).toString();
		}
		DirectoryEntry(File f, File base, UriBuilder ub) {
			name = f.getName();
			String path = f.getAbsolutePath();
			if (f.exists()) {
				synchronized(ISO8601) {
					modTime = ISO8601.format(new Date(f.lastModified()));
				}
				if (f.isDirectory())
					type = "directory";
				else if (f.isFile())
					type = "file";
			}
			id = "dir_" + md5Hex(path);
			// Build the URI correctly. THIS IS SNEAKY CODE!
			String nm = path.substring(
					base.getAbsolutePath().length() - base.getName().length());
			String[] bits = nm.split(File.separator); // assume separator length = 1
			StringBuilder pathPattern = new StringBuilder();
			String sep = "";
			for (int i = 0; i < bits.length; i++) {
				pathPattern.append(sep).append("{bit").append(i).append("}");
				sep = "/"; // URL separator, not file separator
			}
			uri = ub.clone().path(pathPattern.toString())
					.build((Object[]) bits).toString();
		}
		@XmlElement(name = "modification-time")
		public String modTime;
		@XmlElement
		public String name;
		@XmlElement
		public String id;
		@XmlElement
		public String type;
		@XmlElement
		public String uri;
	}

	@XmlRootElement(name = "tasks")
	@XmlType
	class ArchiveTaskList {
		@XmlElement(name = "task")
		public List<ArchiveTask> tasks = new ArrayList<>();
	}

	@XmlRootElement(name = "task")
	@XmlType
	class ArchiveTask {
		@XmlElement
		public String id;
		@XmlElement
		public String status;
		@XmlElement
		public Double progress;
		@XmlElement
		public URI url;
		@XmlElement(name = "start-time")
		@XmlSchemaType(name = "dateTime")
		public String startTime;
		@XmlElement(name = "end-time")
		@XmlSchemaType(name = "dateTime")
		public String endTime;
		@XmlElement
		public User submitter;
		@XmlElement
		public Assay assay;
		@XmlElement
		public Study study;
		@XmlElement
		public List<DirectoryEntry> directory = new ArrayList<>();
		@XmlElement(name = "created-asset")
		public URI createdAsset;
		@XmlElement(name = "created-openbis-experiment")
		public URI createdExperiment;
		@XmlElement
		public Project project;
		@XmlElement
		public String notes;

		void validate() throws BadRequestException {
			if (submitter == null)
				throw new BadRequestException("no user specified");
			if (submitter.url == null)
				throw new BadRequestException("no user url");

			if (project == null)
				throw new BadRequestException("no project specified");
			if (project.url == null)
				throw new BadRequestException("no project url");

			if (directory == null)
				throw new BadRequestException("bad directory");
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
}

class Constants {
	static final String JSON = "application/json";
}
