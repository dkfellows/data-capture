package manchester.synbiochem.datacapture;

import static java.util.Collections.sort;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.seeOther;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static manchester.synbiochem.datacapture.Constants.JSON;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.Project;
import manchester.synbiochem.datacapture.SeekConnector.Study;
import manchester.synbiochem.datacapture.SeekConnector.User;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
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
	InformationSource infoSource;
	private Log log = LogFactory.getLog(getClass());

	@Override
	public String getStatus() {
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
		ul.users = new ArrayList<>(infoSource.getUsers());
		return ul;
	}

	@Override
	public ProjectList projects() {
		ProjectList pl = new ProjectList();
		pl.projects = new ArrayList<>(infoSource.getProjects());
		return pl;
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
	public DirectoryList dirs(UriInfo ui) {
		UriBuilder ub = ui.getBaseUriBuilder().path(Paths.DIR);
		DirectoryList dl = new DirectoryList();
		for (File root : lister.getRoots())
			dl.dirs.add(new DirectoryEntry(root, ub));
		return dl;
	}

	@Override
	public Response dirs(String path, UriInfo ui) {
		String[] bits = path.replaceFirst("^/+", "").split("/");
		UriBuilder ub = ui.getBaseUriBuilder().path(Paths.DIR);
		DirectoryList dl = new DirectoryList();
		if (bits == null || bits.length == 0) {
			for (File root : lister.getRoots())
				dl.dirs.add(new DirectoryEntry(root, ub));
		} else {
			try {
				File base = lister.getRoot(bits[0]);
				for (File f : lister.getListing(base, bits))
					dl.dirs.add(new DirectoryEntry(f, base, ub));
			} catch (IOException e) {
				return status(NOT_FOUND).type("text/plain")
						.entity(e.getMessage()).build();
			}
		}
		return ok(dl, JSON).build();
	}

	@Override
	public Response tree(String id, UriInfo ui) {
		File base;
		List<File> results;
		try {
			if ("#".equals(id)) {
				base = null;
				results = new ArrayList<>(lister.getRoots());
			} else {
				String[] bits = id.replaceFirst("^/+", "").split("/");
				base = lister.getRoot(bits[0]);
				results = lister.getListing(base, bits);
			}
		} catch (IOException e) {
			return status(NOT_FOUND).type("text/plain").entity(e.getMessage())
					.build();
		}
		Collections.sort(results, new Comparator<File>() {
			@Override
			public int compare(File o1, File o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		JSONArray out = new JSONArray();
		for (File f : results) {
			JSONObject obj = new JSONObject();
			if (base == null) {
				obj.put("id", f.getName());
				obj.put("parent", "#");
				obj.put("text", "Instrument: " + f.getName());
				obj.put("children", true);
				obj.put("icon", "images/instrument.png");
			} else {
				obj.put("id", id + "/" + f.getName());
				obj.put("parent", id);
				obj.put("text", f.getName());
				if (f.isDirectory()) {
					obj.put("children", true);
					obj.put("icon", "images/directory.png");
				} else {
					obj.put("state", new JSONObject().put("disabled", true));
					obj.put("icon", "images/file.png");
				}
			}
			out.put(obj);
		}
		return Response.ok(out, JSON).build();
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
			try {
				atl.tasks.add(tasks.describeTask(id, ub));
			} catch (NotFoundException e) {
				/*
				 * Ignore tasks that have just been squelched; we can pretend
				 * they just don't exist for our purposes.
				 */
			}
		sort(atl.tasks, taskComparator);
		return atl;
	}

	@Override
	public Response task(String id) {
		if (id == null || id.isEmpty())
			throw new BadRequestException("bad id");
		try {
			return ok(tasks.describeTask(id, null), JSON).build();
		} catch (NotFoundException e) {
			/*
			 * This case is normal enough that we don't want an exception in the
			 * log.
			 */
			return status(NOT_FOUND).build();
		}
	}

	@Override
	@RolesAllowed("ROLE_USER")
	public Response createTask(ArchiveTask proposedTask, UriInfo ui) {
		if (proposedTask == null)
			throw new BadRequestException("bad task");
		proposedTask.validate();

		List<String> dirs = lister.getSubdirectories(proposedTask.directory);
		if (dirs.isEmpty())
			throw new BadRequestException(
					"need at least one directory to archive");

		String notes = proposedTask.notes;
		if (notes == null)
			notes = "";
		else
			notes = notes.trim();

		String id;
		try {
			id = createTask(proposedTask.submitter, proposedTask.project,
					dirs.get(0), notes);
		} catch (IOException e) {
			return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
		}

		log.info("created task " + id + " to archive " + dirs.get(0));
		UriBuilder ub = ui.getAbsolutePathBuilder().path("{id}");
		return created(ub.build(id)).entity(tasks.describeTask(id, ub))
				.type("application/json").build();
	}

	private String createTask(User user, Project project, String dir,
			String notes) throws IOException {
		user = infoSource.getUser(user.url);
		project = infoSource.getProject(project.url);
		log.info("creating task for " + user.name + " to work archive for project "
				+ project.name);
		return tasks.newTask(user, project, dir, notes);
	}

	private String createTask(User user, Assay a0, List<String> dirs,
			Project project, String notes) {
		Assay assay = seek.getAssay(a0.url);
		log.info("creating task for " + user.name + " to work on assay "
				+ assay.url + " (" + assay.name + ")");
		return tasks.newTask(user, assay, dirs, project.name, notes);
	}

	private String createTask(User user, Study s0, List<String> dirs,
			Project project, String notes) {
		Study study = seek.getStudy(s0.url);
		log.info("creating task for " + user.name + " to work on study "
				+ study.url + " (" + study.name + ")");
		return tasks.newTask(user, study, dirs, project.name, notes);
	}

	@Override
	public Response deleteTask(String id, UriInfo ui) {
		if (id == null || id.isEmpty())
			throw new BadRequestException("bad id");
		try {
			tasks.deleteTask(id);
		} catch (InterruptedException | ExecutionException e) {
			throw new InternalServerErrorException("problem when cancelling task", e);
		} catch (NotFoundException e) {
			// No exception logging; it's just already gone
			return status(NOT_FOUND).build();
		}
		return seeOther(ui.getBaseUriBuilder().path("tasks").build()).build();
	}
}
