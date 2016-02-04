package manchester.synbiochem.datacapture;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.PreDestroy;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.UriBuilder;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;

public class TaskStore {
	private int count;
	private Map<String, Pair> tasks = new TreeMap<>();
	@Value("${archive.root}")
	File archRoot;
	@Value("${metadata.root}")
	File metaRoot;
	@Autowired
	AsyncTaskExecutor executor;
	@Autowired
	SeekConnector seek;
	@Value("${cifs.root}")
	private URI cifsRoot;
	private Tika tika = new Tika();
	private static final SimpleDateFormat ISO8601;
	static {
		ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	static class Pair {
		Pair(MetadataRecorder md, List<String> dirs, ArchiverTask task,
				Future<URL> result) {
			assert md != null;
			this.md = md;
			assert task != null;
			this.task = task;
			assert dirs != null;
			this.dirs = dirs;
			assert result != null;
			this.result = result;
		}

		final MetadataRecorder md;
		final ArchiverTask task;
		final Future<URL> result;
		String key;
		final List<String> dirs;

		String getStatus() {
			if (result.isDone())
				return null;
			return task.getState();
		}

		Double getProgress() {
			if (result.isDone())
				return 1.0;
			return task.getProgress();
		}

		Date getStart() {
			if (task.start == null)
				return null;
			return new Date(task.start.longValue());
		}

		Date getFinish() {
			if (task.finish == null)
				return null;
			return new Date(task.finish.longValue());
		}
	}

	public String newTask(SeekConnector.User user, SeekConnector.Assay assay,
			List<String> dirs) {
		MetadataRecorder md = new MetadataRecorder(tika);
		assert user != null && user.url != null;
		md.setUser(user);
		assert assay != null && assay.url != null;
		md.setExperiment(assay);
		File directory = null;
		for (String d : dirs) {
			File dir = new File(d);
			if (dir.exists()) {
				directory = dir;
				break;
			}
		}
		if (directory == null)
			throw new WebApplicationException("no such directory", BAD_REQUEST);
		ArchiverTask at = new ArchiverTask(md, archRoot, metaRoot, cifsRoot, directory,
				seek);
		synchronized (this) {
			Pair p = new Pair(md, dirs, at, executor.submit(at));
			String key = "task" + (++count);
			p.key = key;
			tasks.put(key, p);
			at.setJavaTask(p.result);
			return key;
		}
	}

	/**
	 * @param id
	 * @return Never returns <tt>null</tt>.
	 * @throws WebApplicationException
	 *             if the task cannot be found.
	 */
	private synchronized Pair get(String id) {
		if (id == null || id.isEmpty())
			throw new WebApplicationException("silly input", BAD_REQUEST);
		Pair task = tasks.get(id);
		if (task == null)
			throw new WebApplicationException(NOT_FOUND);
		return task;
	}

	public Interface.ArchiveTask describeTask(String id, UriBuilder ub) {
		Pair task = get(id);
		Interface.ArchiveTask result = new Interface.ArchiveTask();
		result.id = id;
		result.status = task.getStatus();
		result.progress = task.getProgress();
		result.submitter = task.md.getUser();
		result.assay = task.md.getExperiment();
		result.directory = new ArrayList<>();
		for (String d : task.dirs)
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
			if (task.result.isDone()) {
				URL made = task.result.get();
				if (made != null)
					result.createdAsset = made.toURI();
			}
		} catch (URISyntaxException | InterruptedException | ExecutionException e) {
			// ignore these; they should all be impossible
		}
		return result;
	}

	public Future<URL> getTask(String id) {
		Pair task = get(id);
		return task.result;
	}

	public void deleteTask(String id) {
		Pair task;
		synchronized (this) {
			get(id);
			task = tasks.remove(id);
		}
		if (task == null)
			throw new WebApplicationException(GONE);
		if (!task.result.isDone())
			task.result.cancel(false);
	}

	@PreDestroy
	private void stopAllTasks() {
		List<Pair> tasks;
		synchronized (this) {
			// Take a copy so we don't need to hold the lock
			tasks = new ArrayList<>(this.tasks.values());
		}
		for (Pair task : tasks)
			if (task != null && task.result != null && !task.result.isDone())
				task.result.cancel(true);
	}

	public Double getStatus(String id) throws InterruptedException,
			ExecutionException {
		Pair p = get(id);
		if (p.result.isDone())
			p.result.get();
		return p.getProgress();
	}

	public List<String> list() {
		return new ArrayList<>(tasks.keySet());
	}
}
