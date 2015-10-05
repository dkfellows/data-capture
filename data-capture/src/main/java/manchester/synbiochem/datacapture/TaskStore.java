package manchester.synbiochem.datacapture;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import java.io.File;
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
	File root;
	@Autowired
	AsyncTaskExecutor executor;
	@Autowired
	SeekConnector seek;
	private Tika tika = new Tika();
	private static final SimpleDateFormat ISO8601;
	static {
		ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		ISO8601.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	static class Pair {
		Pair(MetadataRecorder md, List<String> dirs, ArchiverTask task, Future<URL> result) {
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
		ArrayList<File> directories = new ArrayList<>();
		for (String d : dirs) {
			File dir = new File(d);
			if (dir.exists())
				directories.add(dir);
		}
		ArchiverTask at = new ArchiverTask(md, root, directories, seek);
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
			throw new WebApplicationException(BAD_REQUEST);
		Pair task = tasks.get(id);
		if (task == null)
			throw new WebApplicationException(NOT_FOUND);
		return task;
	}

	public Interface.ArchiveTask describeTask(String id, UriBuilder ub) {
		Pair task = get(id);
		Interface.ArchiveTask result = new Interface.ArchiveTask();
		result.id = id;
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
			if (task.result.isDone())
				result.createdAsset = task.result.get().toURI();
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
