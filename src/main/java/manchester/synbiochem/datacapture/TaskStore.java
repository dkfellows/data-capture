package manchester.synbiochem.datacapture;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.UriBuilder;

import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.Study;
import manchester.synbiochem.datacapture.SeekConnector.User;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;

/**
 * Class and bean that manages the collection of archiving tasks, both current
 * and historic. Note that this also acts as the factory of tasks.
 * 
 * @author Donal Fellows
 */
public class TaskStore {
	private int count;
	private Map<String, FinishedTask> doneTasks = new TreeMap<>();
	private Map<String, ActiveTask> tasks = new TreeMap<>();
	@Value("${archive.root}")
	File archRoot;
	@Value("${metadata.root}")
	File metaRoot;
	@Value("${savedTasks.root}")
	File savedTasksRoot;
	@Autowired
	AsyncTaskExecutor executor;
	@Autowired
	SeekConnector seek;
	@Autowired
	OpenBISIngester ingester;
	@Autowired
	InformationSource infoSource;
	@Value("${cifs.root}")
	private URI cifsRoot;
	private Tika tika = new Tika();
	private Log log = LogFactory.getLog(getClass());
	private static final SimpleDateFormat ISO8601;
	static {
		ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	@PostConstruct
	void loadDoneTasks() {
		File[] files = savedTasksRoot.listFiles();
		if (files == null)
			return;
		for (File f : files) {
			// Skip anything untoward
			if (f.getName().startsWith(".") || !f.isFile())
				continue;
			try (InputStream fis = new FileInputStream(f);
					ObjectInputStream ois = new ObjectInputStream(fis)) {
				FinishedTask ft = (FinishedTask) ois.readObject();
				doneTasks.put(ft.getKey(), ft);
			} catch (ClassCastException | IOException | ClassNotFoundException e) {
				log.error("problem loading saved task from " + f
						+ "; deleting...", e);
				f.delete();
			}
		}
	}

	public interface Task {
		boolean isDone();

		Double getProgress();

		String getStatus();

		User getUser();

		Assay getExperiment();

		Date getStart();

		Date getFinish();

		Collection<String> getDirectories();

		URL getCreatedAsset();
	}

	public String newTask(SeekConnector.User user, SeekConnector.Assay assay,
			List<String> dirs) {
		MetadataRecorder md = new MetadataRecorder(tika);
		assert user != null && user.url != null;
		md.setUser(user);
		assert assay != null && assay.url != null;
		md.setExperiment(assay);
		ArchiverTask at = new ArchiverTask(md, archRoot, metaRoot, cifsRoot,
				existingDirectory(dirs), seek, ingester, infoSource);
		Future<URL> taskResult = executor.submit(at);
		synchronized (this) {
			String key;
			do {
				key = "task" + (++count);
			} while (tasks.containsKey(key) || doneTasks.containsKey(key));
			ActiveTask p = new ActiveTask(key, md, dirs, at, taskResult);
			tasks.put(key, p);
			at.setJavaTask(taskResult);
			return key;
		}
	}

	/**
	 * Get the first existing directory from the list.
	 * 
	 * @param dirs
	 *            List of names of directories to look at.
	 * @return The directory
	 * @throws WebApplicationException
	 *             If no suitable directory is present.
	 */
	private File existingDirectory(List<String> dirs) {
		for (String d : dirs) {
			File dir = new File(d);
			if (dir.exists())
				return dir;
		}
		throw new WebApplicationException("no such directory", BAD_REQUEST);
	}

	/**
	 * @param id
	 * @return Never returns <tt>null</tt>.
	 * @throws WebApplicationException
	 *             if the task cannot be found.
	 */
	private synchronized Task get(String id) {
		if (id == null || id.isEmpty())
			throw new WebApplicationException("silly input", BAD_REQUEST);
		FinishedTask t = doneTasks.get(id);
		if (t != null)
			return t;
		ActiveTask task = tasks.get(id);
		if (task == null)
			throw new WebApplicationException(NOT_FOUND);
		return task;
	}

	public Interface.ArchiveTask describeTask(String id, UriBuilder ub) {
		Task task = get(id);
		Interface.ArchiveTask result = new Interface.ArchiveTask();
		result.id = id;
		result.status = task.getStatus();
		result.progress = task.getProgress();
		result.submitter = task.getUser();
		result.assay = task.getExperiment();
		result.directory = new ArrayList<>();
		for (String d : task.getDirectories())
			result.directory.add(new Interface.Directory(d));
		synchronized (ISO8601) {
			Date t = task.getStart();
			if (t != null)
				result.startTime = ISO8601.format(t);
			t = task.getFinish();
			if (t != null)
				result.endTime = ISO8601.format(t);
		}
		if (ub != null)
			result.url = ub.build(id);
		try {
			if (task.isDone()) {
				URL made = task.getCreatedAsset();
				if (made != null)
					result.createdAsset = made.toURI();
				if (task instanceof ActiveTask)
					synchronized (this) {
						ActiveTask at = (ActiveTask) task;
						tasks.remove(at.getKey());
						doneTasks.put(at.getKey(),
								at.toFinished(savedTasksRoot));
					}
			}
		} catch (URISyntaxException e) {
			// ignore these; they should all be impossible
		} catch (IOException e) {
			log.error("problem when serializing task", e);
		}
		return result;
	}

	public void deleteTask(String id) throws InterruptedException,
			ExecutionException {
		ActiveTask task;
		synchronized (this) {
			get(id);
			task = tasks.remove(id);
			FinishedTask ft = doneTasks.remove(id);
			if (ft != null)
				ft.delete();
		}
		if (task == null)
			throw new WebApplicationException(GONE);
		if (!task.isDone())
			task.cancel(false);
	}

	@PreDestroy
	private void stopAllTasks() {
		List<ActiveTask> tasks;
		synchronized (this) {
			// Take a copy so we don't need to hold the lock
			tasks = new ArrayList<>(this.tasks.values());
		}
		for (ActiveTask task : tasks)
			if (task != null && !task.isDone()) {
				task.cancel(true);
				try {
					doneTasks.put(task.getKey(),
							task.toFinished(savedTasksRoot));
				} catch (IOException e) {
					log.error("problem when serializing task", e);
				}
			}
	}

	public Double getStatus(String id) throws InterruptedException,
			ExecutionException {
		Task p = get(id);
		p.isDone();
		return p.getProgress();
	}

	public List<String> list() {
		TreeSet<String> ts = new TreeSet<>(tasks.keySet());
		ts.addAll(doneTasks.keySet());
		return new ArrayList<>(ts);
	}

	private static final String DESCRIPTION_TEMPLATE = "Capture of data relating to '%s' from the %s instrument in the %s project at %s.";

	public String newTask(User user, Study study, List<String> dirs) {
		String machine = infoSource.getMachineName(dirs.get(0));
		String project = infoSource.getProjectName(machine, study);
		String now = DateFormat.getInstance().format(new Date());
		// Not the greatest way of creating a title, but not too problematic either.
		String title = dirs.get(0).replaceFirst("/+$", "")
				.replaceFirst(".*/", "").replace("_", " ");
		String description = format(DESCRIPTION_TEMPLATE, title, machine,
				project, now);
		URL url = seek.createExperimentalAssay(user, study, description, title);
		log.info("created assay at " + url);
		Assay assay = seek.getAssay(url);
		return newTask(user, assay, dirs);
	}
}
