package manchester.synbiochem.datacapture;

import static java.lang.System.currentTimeMillis;
import static org.apache.commons.logging.LogFactory.getLog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.ws.rs.WebApplicationException;

import manchester.synbiochem.datacapture.Interface.Directory;

import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Value;

public class DirectoryLister {
	private Log log = getLog(DirectoryLister.class);
	private List<File> roots;

	@Value("#{'${instrument.directories}'.split(',')}")
	public void setRoots(List<String> rootNames) {
		List<File> newRoots = new ArrayList<>();
		for (String r : rootNames) {
			File root = new File(r);
			File[] list = root.listFiles();
			if (list == null)
				continue;
			for (File item : list)
				if (item != null && item.isDirectory()
						&& !item.getName().startsWith(".")) {
					log.info("adding source root: " + item);
					newRoots.add(item);
				}
		}
		roots = newRoots;
	}

	private List<String> subdirectories() {
		List<String> subdirs = new ArrayList<>();
		for (File root : roots) {
			if (root == null)
				continue;
			File[] list = root.listFiles();
			if (list == null)
				continue;
			for (File entry : list)
				if (entry != null && entry.isDirectory())
					subdirs.add(entry.getAbsolutePath());
		}
		return subdirs;
	}

	/** The time between listing the directories */
	private static final long LIFE_INTERVAL = 30000L;
	private List<String> subs;
	private long subtime;
	/**
	 * The (reciprocal of) the ratio between the time to list the directories
	 * and the time between listings when warnings will be issued.
	 */
	private static final long LONG_RATIO = 30L;

	public List<String> getSubdirectories() {
		long now = currentTimeMillis();
		if (subtime + LIFE_INTERVAL < now) {
			synchronized (this) {
				if (subtime + LIFE_INTERVAL < now) {
					subs = subdirectories();
					long delta = currentTimeMillis() - now;
					if (delta > LIFE_INTERVAL / LONG_RATIO)
						log.warn("directory listing took " + delta
								+ " milliseconds");
					subtime = now;
				}
			}
		}
		return subs;
	}

	public List<String> getSubdirectories(List<Directory> directory) {
		List<String> real = new ArrayList<>();
		HashSet<String> sd = new HashSet<>(getSubdirectories());
		for (Directory dir : directory) {
			String name = dir.name;
			if (!sd.contains(name))
				throw new WebApplicationException("no such directory: " + name
						+ " not in " + sd, 400);
			real.add(name);
		}
		return real;
	}

	@PostConstruct
	private void prebuildCache() {
		getSubdirectories();
	}
}
