package manchester.synbiochem.datacapture;

import static java.lang.System.currentTimeMillis;
import static org.apache.commons.logging.LogFactory.getLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;

import manchester.synbiochem.datacapture.Interface.DirectoryEntry;

import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Value;

/**
 * Class and bean that handles listing of directories and maintaining where
 * should be archived and where shouldn't. This is the core of the policy used
 * in the security enforcement point.
 * 
 * @author Donal Fellows
 */
public class DirectoryLister {
	private Log log = getLog(DirectoryLister.class);
	private List<File> roots;

	@Value("#{'${instrument.directories}'.split(',')}")
	private List<String> rootNames;
	@Value("#{'${instrument.directories.suppress}'.split(',')}")
	private List<String> suppressNames;

	/**
	 * The core of the directory listing engine. Gets all directories that are
	 * subdirectories of the directories in the {@link #roots} list.
	 * 
	 * @return List of directory names. Each of them is an absolute pathname.
	 */
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
	public static final long LIFE_INTERVAL = 30000L;
	private List<String> subs;
	private long subtime;
	/**
	 * The (reciprocal of) the ratio between the time to list the directories
	 * and the time between listings when warnings will be issued.
	 */
	private static final long LONG_RATIO = 30L;

	/**
	 * Get the subdirectories that we have vetted as being acceptable places to
	 * perform archiving from. The results are internally cached for
	 * {@value #LIFE_INTERVAL} milliseconds.
	 */
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

	public List<File> getListing(String path) throws IOException {
		String[] bits = path.replaceFirst("^/+", "").split("/");
		File dir = walkPath(bits, getRoot(bits[0]));

		List<File> result = new ArrayList<>();
		// Need to filter the results
		for (File bit : dir.listFiles()) {
			String name = bit.getName();
			if (!(name.equals(".") || name.equals("..")))
				result.add(bit);
		}
		return result;
	}

	/** Look up the root with the given name. Assumes all roots have differing final components. */
	private File getRoot(String rootname) throws IOException {
		for (File root : roots)
			if (root.getName().equals(rootname))
				return root;
		throw new IOException("no such root");
	}
	/** Find the existing file with the given name in the given directory. */
	private File findInDir(String name, File dir) throws FileNotFoundException {
		if (name.equals(".") || name.equals(".."))
			throw new FileNotFoundException("illegal path component");
		for (File d : dir.listFiles())
			if (d.getName().equals(name))
				return d;
		throw new FileNotFoundException("no such file: " + name);
	}

	private File walkPath(String[] bits, File root) throws IOException {
		File dir = root;
		boolean first = true;
		for (String bit : bits) {
			// Skip first element; that was already used to get the root
			if (first) {
				first = false;
				continue;
			}
			// Skip absent bits
			if (bit == null || bit.isEmpty())
				continue;
			// Map the name to the directory entry
			dir = findInDir(bit, dir);
		}
		return dir;
	}

	/**
	 * Get the subdirectories from our vetted list that match up with the
	 * requested list of directories.
	 * 
	 * @param directory
	 *            List of directory descriptor structures taken from the webapp
	 *            API.
	 * @return List of directory names.
	 * @throws WebApplicationException
	 *             If any of the vetting steps fail.
	 */
	public List<String> getSubdirectories(List<DirectoryEntry> directory) {
		List<String> real = new ArrayList<>();
		Set<String> sd = new HashSet<>(getSubdirectories());
		for (DirectoryEntry dir : directory) {
			String name = dir.name;
			if (!sd.contains(name))
				throw new BadRequestException("no such directory: " + name
						+ " not in " + sd);
			real.add(name);
		}
		return real;
	}

	@PostConstruct
	private void prebuildCache() {
		List<File> newRoots = new ArrayList<>();
		Set<String> suppress = new HashSet<>(suppressNames);
		for (String r : rootNames) {
			File root = new File(r.trim());
			File[] list = root.listFiles();
			if (list == null)
				continue;
			for (File item : list)
				if (item != null && item.isDirectory()
						&& !item.getName().startsWith(".")
						&& !suppress.contains(item.getAbsolutePath())) {
					log.info("adding source root: " + item);
					newRoots.add(item);
				}
		}
		roots = newRoots;
		getSubdirectories();
	}
}
