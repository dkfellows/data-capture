package manchester.synbiochem.datacapture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.User;

class ActiveTask implements TaskStore.Task {
	ActiveTask(String key, MetadataRecorder md, List<String> dirs,
			ArchiverTask task, Future<URL> result) {
		assert key != null;
		this.key = key;
		assert md != null;
		this.md = md;
		assert task != null;
		this.task = task;
		assert dirs != null;
		this.dirs = dirs;
		assert result != null;
		this.result = result;
	}

	private final MetadataRecorder md;
	private final ArchiverTask task;
	private Future<URL> result;
	private final String key;
	private final List<String> dirs;
	private URL createdAsset;

	public FinishedTask toFinished(File root) throws IOException {
		File filename = new File(root, getKey());
		FinishedTask ft = new FinishedTask(this, filename);
		try (OutputStream fos = new FileOutputStream(filename);
				ObjectOutputStream oos = new ObjectOutputStream(fos)) {
			oos.writeObject(ft);
		}
		return ft;
	}

	public String getKey() {
		return key;
	}

	@Override
	public Assay getExperiment() {
		return md.getExperiment();
	}

	@Override
	public User getUser() {
		return md.getUser();
	}

	@Override
	public Collection<String> getDirectories() {
		return dirs;
	}

	@Override
	public String getStatus() {
		if (result.isDone())
			return null;
		return task.getState();
	}

	@Override
	public Double getProgress() {
		if (result.isDone())
			return 1.0;
		return task.getProgress();
	}

	@Override
	public Date getStart() {
		if (task.start == null)
			return null;
		return new Date(task.start.longValue());
	}

	@Override
	public Date getFinish() {
		if (task.finish == null)
			return null;
		return new Date(task.finish.longValue());
	}

	ArchiverTask getTask() {
		return task;
	}

	private Future<URL> getResult() throws InterruptedException,
			ExecutionException {
		Future<URL> r = result;
		if (r == null)
			return null;
		if (r.isDone()) {
			createdAsset = r.get();
			result = null;
		}
		return r;
	}

	public void cancel(boolean mayInterrupt) {
		try {
			Future<URL> future = getResult();
			if (future != null)
				future.cancel(mayInterrupt);
		} catch (InterruptedException | ExecutionException e) {
			// ignore
		}
	}

	@Override
	public boolean isDone() {
		try {
			Future<URL> future = getResult();
			return future == null ? true : future.isDone();
		} catch (InterruptedException | ExecutionException e) {
			return false;
		}
	}

	@Override
	public URL getCreatedAsset() {
		try {
			if (createdAsset == null)
				getResult();
		} catch (InterruptedException | ExecutionException e) {
			// Nothing to do here
		}
		return createdAsset;
	}
}
