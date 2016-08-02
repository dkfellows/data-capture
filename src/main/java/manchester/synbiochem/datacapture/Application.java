package manchester.synbiochem.datacapture;

import static java.util.Collections.sort;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.seeOther;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.Study;
import manchester.synbiochem.datacapture.SeekConnector.User;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Class and bean that implements the application's user-facing interface.
 *
 * @author Donal Fellows
 */
@Path("/")
@RolesAllowed("ROLE_USER")
public class Application implements Interface {
	@Autowired
	SeekConnector seek;
	@Autowired
	TaskStore tasks;
	@Autowired
	DirectoryLister lister;
	@Autowired
	InformationSource info;
	private Log log = LogFactory.getLog(getClass());

	@Override
	public String status() {
		return "OK";
	}

	@Override
	public Description describe(UriInfo ui) {
		UriBuilder ub = ui.getAbsolutePathBuilder().path("{piece}");
		Description d = new Description();
		d.assays = ub.build("assays");
		d.directories = ub.build("directories");
		d.tasks = ub.build("tasks");
		d.users = ub.build("users");
		return d;
	}

	@Override
	public UserList users() {
		UserList ul = new UserList();
		ul.users = new ArrayList<>(seek.getUsers());
		return ul;
	}

	@Override
	public StudyList studies() {
		StudyList sl = new StudyList();
		sl.studies = new ArrayList<>(seek.getStudies());
		return sl;
	}

	@Override
	public AssayList assays() {
		AssayList al = new AssayList();
		al.assays = new ArrayList<>(seek.getAssays());
		return al;
	}

	@Override
	public DirectoryList dirs() {
		DirectoryList dl = new DirectoryList();
		for (String name : lister.getSubdirectories())
			dl.dirs.add(new Directory(name));
		return dl;
	}

	private static final Comparator<ArchiveTask> taskComparator = new Comparator<ArchiveTask>() {
		@Override
		public int compare(ArchiveTask o1, ArchiveTask o2) {
			return o1.id.compareTo(o2.id);
		}
	};

	@Override
	public ArchiveTaskList tasks(UriInfo ui) {
		UriBuilder ub = ui.getAbsolutePathBuilder().path("{id}");
		ArchiveTaskList atl = new ArchiveTaskList();
		atl.tasks = new ArrayList<>();
		for (String id : tasks.list())
			atl.tasks.add(tasks.describeTask(id, ub));
		sort(atl.tasks, taskComparator);
		return atl;
	}

	@Override
	public ArchiveTask task(String id) {
		if (id == null || id.isEmpty())
			throw new WebApplicationException(BAD_REQUEST);
		return tasks.describeTask(id, null);
	}

	@Override
	@RolesAllowed("ROLE_USER")
	public Response createTask(ArchiveTask proposedTask, UriInfo ui) {
		if (proposedTask == null)
			throw new WebApplicationException("bad task", BAD_REQUEST);

		User u0 = proposedTask.submitter;
		if (u0 == null)
			throw new WebApplicationException("no user specified", BAD_REQUEST);
		if (u0.url == null)
			throw new WebApplicationException("no user url", BAD_REQUEST);
		User user = seek.getUser(u0.url);

		Assay a0 = proposedTask.assay;
		Study s0 = proposedTask.study;
		if (a0 == null && s0 == null)
			throw new WebApplicationException("no assay or study specified", BAD_REQUEST);
		if (a0 != null && s0 != null)
			throw new WebApplicationException("must not specify both assay and study", BAD_REQUEST);
		if (a0 != null && a0.url == null)
			throw new WebApplicationException("no assay url", BAD_REQUEST);
		if (s0 != null && s0.url == null)
			throw new WebApplicationException("no study url", BAD_REQUEST);

		List<Directory> d0 = proposedTask.directory;
		if (d0 == null)
			throw new WebApplicationException("bad directory", BAD_REQUEST);
		List<String> dirs = lister.getSubdirectories(d0);
		if (dirs.isEmpty())
			throw new WebApplicationException(
					"need at least one directory to archive", BAD_REQUEST);

		String id;
		if (a0 != null)
			id = createTask(user, a0, dirs);
		else
			id = createTask(user, s0, dirs);
		log.info("created task " + id + " to archive " + dirs.get(0));
		UriBuilder ub = ui.getAbsolutePathBuilder().path("{id}");
		return created(ub.build(id)).entity(tasks.describeTask(id, ub))
				.type("application/json").build();
	}

	private String createTask(User user, Assay a0, List<String> dirs) {
		Assay assay = seek.getAssay(a0.url);
		return tasks.newTask(user, assay, dirs);
	}

	private static final String DESCRIPTION_TEMPLATE = "Capture of data relating to '%s' from the %s instrument in the %s project at %s.";

	private String createTask(User user, Study s0, List<String> dirs) {
		Study study = seek.getStudy(s0.url);
		String machine = info.getMachineName(dirs.get(0));
		String project = info.getProjectName(machine, study);
		String now = DateFormat.getInstance().format(new Date());
		// Not the greatest way of creating a title, but not too problematic either.
		String title = dirs.get(0).replaceFirst("/+$", "")
				.replaceFirst(".*/", "").replace("_", " ");
		String description = String.format(DESCRIPTION_TEMPLATE, title,
				machine, project, now);
		URL url = seek.createExperimentalAssay(user, study, description, title);
		log.info("created assay at " + url);
		Assay assay = seek.getAssay(url);
		return tasks.newTask(user, assay, dirs);
	}

	@Override
	public Response deleteTask(String id, UriInfo ui) {
		if (id == null || id.isEmpty())
			throw new WebApplicationException(BAD_REQUEST);
		try {
			tasks.deleteTask(id);
		} catch (InterruptedException | ExecutionException e) {
			throw new WebApplicationException("problem when cancelling task", e);
		}
		return seeOther(ui.getBaseUriBuilder().path("tasks").build()).build();
	}
}
