package manchester.synbiochem.datacapture;

import static java.lang.System.currentTimeMillis;
import static java.nio.file.Files.copy;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import manchester.synbiochem.datacapture.SeekConnector.Assay;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The overall sub-tasks of this task are:
 * <ol>
 * <li>List all the files to be archived.
 * <li>Copy the files from the instrument to the operational data store.
 * <li>Compute the metadata about each file.
 * <li>Construct the bagit.
 * <li>Instantiate the files on the Isilon.
 * </ol>
 * 
 * @author Donal Fellows
 */
public class ArchiverTask implements Callable<URL> {
	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private Log log = LogFactory.getLog(ArchiverTask.class);

	private static int tasksCounter;
	private final int myID;
	private final MetadataRecorder metadata;
	private final File directoryToArchive;
	private final File archiveRoot;
	private final File metastoreRoot;
	private final URI cifsRoot;
	private final SeekConnector seek;
	private volatile int fileCount;
	private volatile int metaCount;
	private volatile int copyCount;
	private volatile boolean done;
	private Future<?> javaTask;
	private final List<Entry> entries;
	Long start;
	Long finish;
	private DateFormat ISO8601;

	private static String getMachineName(File sourceDir, InformationSource info) {
		File sd = sourceDir;
		do {
			sd = sd.getParentFile();
		} while (sd != null && !info.hasMachineName(sd.getName()));
		return sd == null ? sourceDir.getParentFile().getName() : sd.getName();
	}

	private static String getProjectName(String machine,
			MetadataRecorder metadata, InformationSource info) {
		String prefix = info.getInstrumentType(machine);
		String project = "capture";
		Assay experiment = metadata.getExperiment();
		if (experiment.projectName != null)
			project = experiment.projectName.replaceAll("[^a-zA-Z0-9]+", "-");
		if (project.equalsIgnoreCase("synbiochem"))
			project = "other";
		return prefix + "-" + (project.toLowerCase());
	}

	public ArchiverTask(MetadataRecorder metadata, File archiveRoot,
			File metastoreRoot, URI cifsRoot, File directoryToArchive,
			SeekConnector seek, InformationSource infoSource) {
		ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		ISO8601.setTimeZone(UTC);
		this.metadata = metadata;

		String machine = getMachineName(directoryToArchive, infoSource);
		String project = getProjectName(machine, metadata, infoSource);
		// Real root is $archiveRoot/MS-$project/$machine
		this.archiveRoot = new File(new File(archiveRoot, project), machine);
		this.cifsRoot = cifsRoot.resolve(project + "/" + machine);
		this.metastoreRoot = metastoreRoot;
		this.directoryToArchive = directoryToArchive;
		this.seek = seek;
		this.entries = new ArrayList<>();
		synchronized (ArchiverTask.class) {
			myID = ++tasksCounter;
		}
	}

	/**
	 * Get an estimate of how much work has been done. May be called from
	 * outside the task thread.
	 * 
	 * @return a double from 0.0 to 1.0, or <tt>null</tt> if the system is still
	 *         working out how many files there are.
	 */
	public Double getProgress() {
		if (done)
			return 1.0;
		int files = fileCount, metas = metaCount, copies = copyCount;
		if (files == 0)
			return null;
		return (metas + copies) / (files * 2.0);
	}

	@Override
	public URL call() {
		log.info("task[" + myID + "] started archive");
		start = currentTimeMillis();
		try {
			return workflow();
		} catch (RuntimeException e) {
			log.warn("task[" + myID + "] unexpected problem processing", e);
			return null;
		} finally {
			finish = currentTimeMillis();
			log.info("task[" + myID + "] finished archive");
		}
	}

	private String state;

	public String getState() {
		synchronized (this) {
			return state;
		}
	}

	private void setState(String state) {
		synchronized (this) {
			this.state = state;
		}
	}

	protected URL workflow() {
		setState("listing");

		listFiles(directoryToArchive);
		if (javaTask != null && javaTask.isCancelled())
			return null;

		setState("copying");

		copyToWorkingDirectory();
		if (javaTask != null && javaTask.isCancelled())
			return null;

		setState("meta-ing");

		extractMetadata();
		if (javaTask != null && javaTask.isCancelled())
			return null;

		setState("bagging-it");
		bagItUp();

		setState("finishing");

		File jsonFile = new File(metastoreRoot, directoryToArchive.getName()
				+ ".json");
		try {
			FileUtils.write(jsonFile, metadata.get(), UTF8);
		} catch (IOException e) {
			log.warn("task[" + myID
					+ "] failed to write metadata descriptor to " + jsonFile, e);
		}
		return tellSeek();
	}

	/**
	 * Discovers where all the files are in a folder, and what their names are
	 * mapped from (relative to the archRoot folder passed in).
	 * 
	 * @param folder
	 *            The folder to look in.
	 */
	public void listFiles(File folder) {
		assert folder != null;
		assert folder.exists() && folder.isDirectory();
		listFiles(folder, folder.getName());
		fileCount = entries.size();
	}

	private void listFiles(File folder, String location) {
		location = (location == null) ? "" : location + '/';
		for (File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory())
				listFiles(fileEntry, location + fileEntry.getName());
			else if (fileEntry.isFile())
				entries.add(new Entry(location + fileEntry.getName(), fileEntry));
			if (javaTask != null && javaTask.isCancelled())
				break;
		}
	}

	/**
	 * Copy all the files (identified by {@link #listFiles(File)}) to the task's
	 * target directory structure.
	 */
	protected void copyToWorkingDirectory() {
		for (Entry ent : entries) {
			File source = ent.getFile();
			File dest = new File(archiveRoot, ent.getName());
			try {
				log.info("task[" + myID + "] copying " + source);
				ent.setDest(copyOneFile(source, dest));
			} catch (IOException e) {
				log.warn("task[" + myID + "] failed to copy " + source + " to "
						+ dest, e);
			} finally {
				copyCount++;
			}
			if (javaTask != null && javaTask.isCancelled())
				break;
		}
	}

	private File copyOneFile(File source, File dest) throws IOException {
		File dir = dest.getParentFile();
		if (!dir.exists())
			dir.mkdirs();
		try {
			copy(source.toPath(), dest.toPath(), COPY_ATTRIBUTES);
		} catch (FileAlreadyExistsException e) {
			// ignore; assume it is the same thing
		}
		return dest;
	}

	static String resolveToURI(URI base, String part) {
		StringBuilder sb = new StringBuilder(part.length());
		for (char ch : part.toCharArray()) {
			if (Character.isAlphabetic(ch) || Character.isDigit(ch)) {
				sb.append(ch);
				continue;
			} else if (ch == ' ') {
				sb.append('+');
				continue;
			}
			String c = new String(new char[] { ch });
			if ("/_-!.~'()*".contains(c))
				sb.append(c);
			else
				for (byte b : c.getBytes(UTF8))
					sb.append(String.format("%%%02X", 0xFF & b));
		}
		return base.resolve(sb.toString()).toString();
	}

	/**
	 * Get the metadata out of a single file.
	 */
	private void extractMetadatum(Entry ent) throws IOException {
		String cifs = resolveToURI(cifsRoot, ent.getName());
		metadata.addFile(ent.getName(), ent.getFile(), ent.getDestination(),
				cifs);
	}

	/**
	 * Get the metadata out of the files (identified by {@link #listFiles(File)}
	 * ).
	 */
	protected void extractMetadata() {
		for (Entry ent : entries) {
			try {
				log.info("task[" + myID + "] characterising " + ent.getFile());
				extractMetadatum(ent);
			} catch (IOException e) {
				log.warn("task[" + myID + "] failed to generate metadata for "
						+ ent.getDestination(), e);
			} finally {
				metaCount++;
			}
			if (javaTask != null && javaTask.isCancelled())
				break;
		}
	}

	// Construct the actual archive of the data. NOT YET DONE
	protected void bagItUp() {
		try {
			// TODO
		} finally {

		}
	}

	/**
	 * Send the metadata to SEEK.
	 * 
	 * @return the location on SEEK of the descriptor we uploaded.
	 */
	protected URL tellSeek() {
		metadata.get();
		String instrument = directoryToArchive.getParentFile().getName();
		String time = ISO8601.format(new Date(start));
		return seek.uploadFileAsset(metadata.getUser(),
				metadata.getExperiment(), "metadata.tsv",
				"CSV document describing files copied from instrument "
						+ instrument + " to storage at timestamp " + time,
				"Experimental Results Metadata", "text/tab-separated-values",
				metadata.getCSV());
	}

	/**
	 * A name/file pair.
	 * 
	 * @author Donal Fellows
	 */
	static class Entry {
		public Entry(String name, File file) {
			if (name == null || file == null)
				throw new IllegalArgumentException("arguments must not be null");
			this.name = name;
			this.file = file;
		}

		private final String name;
		private final File file;
		private File dest;

		public String getName() {
			return name;
		}

		public File getFile() {
			return file;
		}

		public File getDestination() {
			return dest;
		}

		void setDest(File dest) {
			this.dest = dest;
		}

		@Override
		public boolean equals(Object o) {
			if (o == null || !(o instanceof Entry))
				return false;
			Entry other = (Entry) o;
			return name.equals(other.name) && file.equals(other.file);
		}

		@Override
		public int hashCode() {
			return name.hashCode() ^ file.hashCode();
		}
	}

	public void setJavaTask(Future<?> result) {
		javaTask = result;
	}
}
