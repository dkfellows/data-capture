package manchester.synbiochem.datacapture;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import manchester.synbiochem.datacapture.Interface.Assay;
import manchester.synbiochem.datacapture.Interface.User;

public class FinishedTask implements TaskStore.Task, Serializable {
	private static final long serialVersionUID = 3774514312844079068L;

	private File file;
	private Assay assay;
	private User user;
	private Long start, end;
	private String[] dirs;
	private URL asset;

	public FinishedTask(ActiveTask t, File filename) {
		file = filename;
		assay = t.getExperiment();
		user = t.getUser();
		start = ts(t.getStart());
		end = ts(t.getFinish());
		dirs = t.getDirectories().toArray(new String[0]);
		asset = t.getCreatedAsset();
	}

	public String getKey() {
		return file.getName();
	}

	private static Long ts(Date d) {
		return d == null ? null : d.getTime();
	}

	public void delete() {
		file.delete();
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public Double getProgress() {
		return 1.0;
	}

	@Override
	public String getStatus() {
		return "finishing";
	}

	@Override
	public User getUser() {
		return user;
	}

	@Override
	public Assay getExperiment() {
		return assay;
	}

	@Override
	public Date getStart() {
		return start == null ? null : new Date(start);
	}

	@Override
	public Date getFinish() {
		return end == null ? null : new Date(end);
	}

	@Override
	public Collection<String> getDirectories() {
		return Arrays.asList(dirs);
	}

	@Override
	public URL getCreatedAsset() {
		return asset;
	}
}
