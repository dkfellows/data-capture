package manchester.synbiochem.datacapture;

import static java.lang.System.currentTimeMillis;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.ws.rs.WebApplicationException;

import manchester.synbiochem.datacapture.Interface.Directory;

import org.springframework.beans.factory.annotation.Value;

public class DirectoryLister {
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
						&& !item.getName().startsWith("."))
					newRoots.add(item);
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

	private static final int LIFE_INTERVAL = 30000;
	private List<String> subs;
	private long subtime;

	public List<String> getSubdirectories() {
		long now = currentTimeMillis();
		if (subtime + LIFE_INTERVAL < now) {
			synchronized (this) {
				if (subtime + LIFE_INTERVAL < now) {
					subs = subdirectories();
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
				throw new WebApplicationException("no such directory: " + name + " not in " + sd, 400);
			real.add(name);
		}
		return real;
	}
}
