package manchester.synbiochem.datacapture;

import static manchester.synbiochem.datacapture.Constants.JSON;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.Project;
import manchester.synbiochem.datacapture.SeekConnector.Study;
import manchester.synbiochem.datacapture.SeekConnector.User;

public interface Interface {
	@GET
	@Path("/")
	@Produces("text/plain")
	String getStatus();

	@GET
	@Path("/")
	@Produces(JSON)
	Description describe(@Context UriInfo ui);

	@GET
	@Path("users")
	@Produces(JSON)
	UserList users();

	@GET
	@Path("projects")
	@Produces(JSON)
	ProjectList projects();

	@GET
	@Path("studies")
	@Produces(JSON)
	StudyList studies();
	
	@GET
	@Path("assays")
	@Produces(JSON)
	AssayList assays();

	@GET
	@Path("dir")
	@Produces(JSON)
	DirectoryList dirs();

	@GET
	@Path("dir/{dir:.+}")
	@Produces(JSON)
	Response dirs(@PathParam("dir") String dir, @Context UriInfo ui);

	@GET
	@Path("tasks")
	@Produces(JSON)
	ArchiveTaskList tasks(@Context UriInfo ui);

	@GET
	@Path("tasks/{id}")
	@Produces(JSON)
	Response task(@PathParam("id") String id);

	@POST
	@Path("tasks")
	@Consumes(JSON)
	@Produces(JSON)
	Response createTask(ArchiveTask proposedTask, @Context UriInfo ui);

	@DELETE
	@Path("tasks/{id}")
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
	@XmlSeeAlso(SeekConnector.User.class)
	class UserList {
		@XmlElement(name = "user")
		public List<SeekConnector.User> users = new ArrayList<>();
	}

	@XmlRootElement(name = "studies")
	@XmlSeeAlso(SeekConnector.Study.class)
	class StudyList {
		@XmlElement(name = "study")
		public List<SeekConnector.Study> studies = new ArrayList<>();
	}

	@XmlRootElement(name = "assays")
	@XmlSeeAlso(SeekConnector.Assay.class)
	class AssayList {
		@XmlElement(name = "assay")
		public List<SeekConnector.Assay> assays = new ArrayList<>();
	}

	@XmlRootElement(name = "projects")
	@XmlSeeAlso(SeekConnector.Project.class)
	class ProjectList {
		@XmlElement(name = "project")
		public List<SeekConnector.Project> projects = new ArrayList<>();
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
			name = d;
			File f = new File(d);
			if (f.exists()) {
				synchronized(ISO8601) {
					modTime = ISO8601.format(new Date(f.lastModified()));
				}
				if (f.isDirectory())
					type = "directory";
				else if (f.isFile())
					type = "file";
			}
			id = "dir_" + md5Hex(d);
		}
		DirectoryEntry(File f, UriBuilder ub) {
			name = f.getAbsolutePath();
			if (f.exists()) {
				synchronized(ISO8601) {
					modTime = ISO8601.format(new Date(f.lastModified()));
				}
				if (f.isDirectory())
					type = "directory";
				else if (f.isFile())
					type = "file";
			}
			id = "dir_" + md5Hex(name);
			uri = ub.clone().path("{name}").build(f.getName()).toString();
		}
		DirectoryEntry(File f, File base, UriBuilder ub) {
			name = f.getAbsolutePath();
			if (f.exists()) {
				synchronized(ISO8601) {
					modTime = ISO8601.format(new Date(f.lastModified()));
				}
				if (f.isDirectory())
					type = "directory";
				else if (f.isFile())
					type = "file";
			}
			id = "dir_" + md5Hex(name);
			// Build the URI correctly. THIS IS SNEAKY CODE!
			String nm = name.substring(base.getAbsolutePath().length()
					- base.getName().length());
			String[] bits = nm.split("/");
			StringBuilder pathPattern = new StringBuilder();
			String sep = "";
			for (int i = 0; i < bits.length; i++) {
				pathPattern.append(sep).append("{bit").append(i).append("}");
				sep = "/";
			}
			uri = ub.clone().path(pathPattern.toString())
					.build((Object[]) bits).toString();
		}
		@XmlElement(name = "modification-time")
		public String modTime;
		@XmlElement
		public String name;
		@XmlAttribute
		public String id;
		@XmlElement
		public String type;
		@XmlAttribute
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
		public SeekConnector.User submitter;
		@XmlElement
		public SeekConnector.Assay assay;
		@XmlElement
		public SeekConnector.Study study;
		@XmlElement
		public List<DirectoryEntry> directory = new ArrayList<>();
		@XmlElement(name = "created-asset")
		public URI createdAsset;
		@XmlElement(name = "created-openbis-experiment")
		public URI createdExperiment;
		@XmlElement
		public SeekConnector.Project project;
		@XmlElement
		public String notes;

		void validate() throws BadRequestException {
			if (submitter == null)
				throw new BadRequestException("no user specified");
			if (submitter.url == null)
				throw new BadRequestException("no user url");

			if (assay == null && study == null)
				throw new BadRequestException("no assay or study specified");
			if (assay != null && study != null)
				throw new BadRequestException("must not specify both assay and study");
			if (assay != null && assay.url == null)
				throw new BadRequestException("no assay url");
			if (study != null && study.url == null)
				throw new BadRequestException("no study url");

			if (directory == null)
				throw new BadRequestException("bad directory");

			if (project != null
					&& (project.name == null || project.name.isEmpty()))
				throw new BadRequestException("project name must be non-empty");
		}
	}
}

class Constants {
	static final String JSON = "application/json";
}
