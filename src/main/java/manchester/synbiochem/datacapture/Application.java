package manchester.synbiochem.datacapture;

import static java.util.Collections.sort;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.seeOther;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.User;

import org.springframework.beans.factory.annotation.Autowired;

@Path("/")
@RolesAllowed("ROLE_USER")
public class Application implements Interface {
	@Autowired
	SeekConnector seek;
	@Autowired
	TaskStore tasks;
	@Autowired
	DirectoryLister lister;

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
			throw new WebApplicationException("bad user", BAD_REQUEST);
		if (u0.url == null)
			throw new WebApplicationException("no user url", BAD_REQUEST);
		User user = seek.getUser(u0.url);

		Assay a0 = proposedTask.assay;
		if (a0 == null)
			throw new WebApplicationException("bad assay", BAD_REQUEST);
		if (a0.url == null)
			throw new WebApplicationException("no assay url", BAD_REQUEST);
		Assay assay = seek.getAssay(a0.url);

		List<Directory> d0 = proposedTask.directory;
		if (d0 == null)
			throw new WebApplicationException("bad directory", BAD_REQUEST);
		List<String> dirs = lister.getSubdirectories(d0);
		if (dirs.isEmpty())
			throw new WebApplicationException(
					"need at least one directory to archive", BAD_REQUEST);

		String id = tasks.newTask(user, assay, dirs);
		UriBuilder ub = ui.getAbsolutePathBuilder().path("{id}");
		return created(ub.build(id)).entity(tasks.describeTask(id, ub))
				.type("application/json").build();
	}

	@Override
	public Response deleteTask(String id, UriInfo ui) {
		if (id == null || id.isEmpty())
			throw new WebApplicationException(BAD_REQUEST);
		tasks.deleteTask(id);
		return seeOther(ui.getBaseUriBuilder().path("tasks").build()).build();
	}
}
