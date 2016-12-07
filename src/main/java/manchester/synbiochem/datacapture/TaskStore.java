package manchester.synbiochem.datacapture;

import static java.util.Collections.singletonList;
import static javax.ws.rs.core.Response.Status.GONE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.UriBuilder;

import manchester.synbiochem.datacapture.Interface.ArchiveTask;
import manchester.synbiochem.datacapture.Interface.Assay;
import manchester.synbiochem.datacapture.Interface.Project;
import manchester.synbiochem.datacapture.Interface.User;

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
	OpenBISIngester ingester;
	@Autowired
	InformationSource infoSource;
	@Value("${cifs.root}")
	private URI cifsRoot;
	@Autowired
	DirectoryLister lister;
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
			log.info("loading finished task from " + f);
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

	private Future<URL> submit(final ArchiverTask task) {
		return executor.submit(new Callable<URL>() {
			@Override
			public URL call() throws Exception {
				try {
					return task.call();
				} finally {
					finishedTask(task);
				}
			}
		});
	}

	private File existingDirectory(String dir) throws IOException {
		File d = new File(dir);
		if (!(d.exists() && d.isDirectory()))
			throw new IOException("can only archive an extant directory");
		return d;
	}

	public String newTask(User user, Project project, String dir,
			String notes) throws IOException {
		File d = existingDirectory(dir);

		MetadataRecorder md = new MetadataRecorder(tika, project, notes);
		md.setUser(user);
		ArchiverTask at = new ArchiverTask(md, archRoot, metaRoot, cifsRoot, d,
				ingester, infoSource);
		return storeTask(d, md, at, submit(at));
	}

	private String storeTask(File d, MetadataRecorder md, ArchiverTask at,
			Future<URL> taskResult) {
		List<String> dirs = singletonList(d.getAbsolutePath());
		String key;
		synchronized (this) {
			do {
				key = "task" + (++count);
			} while (tasks.containsKey(key) || doneTasks.containsKey(key));
			ActiveTask p = new ActiveTask(key, md, dirs, at, taskResult);
			tasks.put(key, p);
			at.setJavaTask(taskResult);
		}
		return key;
	}

	/**
	 * @param id
	 * @return Never returns <tt>null</tt>.
	 * @throws WebApplicationException
	 *             if the task cannot be found or there are other problems.
	 */
	private synchronized Task get(String id) {
		if (id == null || id.isEmpty())
			throw new BadRequestException("bad task id");
		FinishedTask t = doneTasks.get(id);
		if (t != null)
			return t;
		ActiveTask task = tasks.get(id);
		if (task == null)
			throw new NotFoundException("no such task");
		return task;
	}

	public ArchiveTask describeTask(String id, UriBuilder ub) {
		Task task = get(id);
		ArchiveTask result = new ArchiveTask();
		result.id = id;
		result.status = task.getStatus();
		result.progress = task.getProgress();
		result.submitter = task.getUser();
		result.assay = task.getExperiment();
		result.directory = new ArrayList<>();
		for (String d : task.getDirectories())
			result.directory.add(new Interface.DirectoryEntry(d));
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
			finalizeDoneActiveTask(task, result);
		} catch (URISyntaxException e) {
			// ignore these; they should all be impossible
		} catch (IOException e) {
			log.error("problem when serializing task", e);
		}
		return result;
	}

	private void finalizeDoneActiveTask(Task task, ArchiveTask result)
			throws URISyntaxException, IOException {
		if (!task.isDone())
			return;
		URL made = task.getCreatedAsset();
		if (made != null)
			result.createdAsset = made.toURI();
		if (!(task instanceof ActiveTask))
			return;
		synchronized (this) {
			ActiveTask at = (ActiveTask) task;
			tasks.remove(at.getKey());
			finishedTask(at);
		}
	}

	private void finishedTask(ArchiverTask at) throws IOException {
		// Ugly search, but space should be fairly small
		ActiveTask t = null;
		synchronized (this) {
			for (ActiveTask e : tasks.values())
				if (e.getTask() == at) {
					t = e;
					break;
				}
		}
		if (t != null)
			finishedTask(t);
	}

	private void finishedTask(ActiveTask task) throws IOException {
		log.info("stashing " + task.getKey() + " on disk");
		doneTasks.put(task.getKey(), task.toFinished(savedTasksRoot));
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
					finishedTask(task);
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
}
