package manchester.synbiochem.datacapture;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.nio.file.Files.copy;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import manchester.synbiochem.datacapture.OpenBISIngester.IngestionResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The overall sub-tasks of this task are:
 * <ol>
 * <li>List all the files to be archived.
 * <li>Copy the files from the instrument to the operational data store.
 * <li>Compute the metadata about each file.
 * <li>Construct the bagit. <i>(Not yet done.)</i>
 * <li>Instantiate the files on the NAS.
 * <li>Tell SEEK about the files.
 * </ol>
 * 
 * @author Donal Fellows
 */
public class ArchiverTask implements Callable<URL> {
	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	private static final Charset UTF8 = Charset.forName("UTF-8");
	final Log log = LogFactory.getLog(ArchiverTask.class);

	private static int tasksCounter;
	final int myID;
	final MetadataRecorder metadata;
	final File directoryToArchive;
	final File archiveRoot;
	final File metastoreRoot;
	final URI cifsRoot;
	final OpenBISIngester ingester;
	final InformationSource info;
	final String machine;
	final String project;
	volatile int fileCount;
	volatile int metaCount;
	volatile int copyCount;
	volatile boolean done;
	private Future<?> javaTask;
	final List<Entry> entries;
	Long start;
	Long finish;
	DateFormat ISO8601;
	DateFormat HUMAN_READABLE;

	ArchiverTask(File dir) {
		directoryToArchive = dir;
		entries = new ArrayList<>();
		// Init stuff
		myID = 0;
		metastoreRoot = null;
		metadata = null;
		cifsRoot = null;
		archiveRoot = null;
		project = null;
		machine = null;
		ingester = null;
		info = null;
	}

	List<Entry> getEntries() {
		return entries;
	}

	private static synchronized int issueID() {
		return ++tasksCounter;
	}

	public ArchiverTask(MetadataRecorder metadata, File archiveRoot,
			File metastoreRoot, URI cifsRoot, File directoryToArchive,
			OpenBISIngester ingester, InformationSource infoSource) {
		ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		ISO8601.setTimeZone(UTC);
		HUMAN_READABLE = new SimpleDateFormat("dd MMMM yyyy");
		HUMAN_READABLE.setTimeZone(UTC);
		this.metadata = metadata;

		machine = infoSource.getMachineName(directoryToArchive);
		project = infoSource.getProjectName(machine, metadata);
		// Real root is $archiveRoot/MS-$project/$machine
		this.archiveRoot = new File(new File(archiveRoot, project), machine);
		this.cifsRoot = cifsRoot.resolve(project + "/" + machine);
		this.metastoreRoot = metastoreRoot;
		this.directoryToArchive = directoryToArchive;
		this.ingester = ingester;
		this.entries = new ArrayList<>();
		this.info = infoSource;
		this.myID = issueID();
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

	private boolean isCancelled() {
		return javaTask != null && javaTask.isCancelled();
	}

	protected URL workflow() {
		setState("listing");

		listFiles(directoryToArchive);
		if (isCancelled())
			return null;

		setState("copying");

		copyToWorkingDirectory();
		if (isCancelled())
			return null;

		setState("ingesting");

		IngestionResult ingestion = ingestIntoOpenBIS();
		if (isCancelled())
			return null;

		setState("recording");

		extractMetadata(ingestion);
		if (isCancelled())
			return null;

		setState("finishing");

		saveJsonManifest();
		return ingestion.dataRoot;
	}

	private File saveJsonManifest() {
		String name = directoryToArchive.getName() + ".json";
		File jsonFile = new File(metastoreRoot, name);
		try {
			int counter = 0;
			while (jsonFile.exists())
				// We don't really ever want to overwrite an existing file
				jsonFile = new File(metastoreRoot, directoryToArchive.getName()
						+ "." + (++counter) + ".json");
			FileUtils.write(jsonFile, metadata.get(), UTF8);
			return jsonFile;
		} catch (IOException e) {
			final String MSG = "task[%d] failed to write metadata descriptor to %s";
			log.warn(format(MSG, myID, jsonFile), e);
			return null;
		}
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
				log.debug("task[" + myID + "] copying " + source);
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

	protected IngestionResult ingestIntoOpenBIS() {
		try {
			File base = new File(archiveRoot, directoryToArchive.getName());
			return ingester.ingest(base, machine, project);
		} catch (IOException | InterruptedException e) {
			log.error("problem during openbis-ingestion phase", e);
		}
		return null;
	}

	static URI resolveToURI(URI base, String part) {
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
		return base.resolve(sb.toString());
	}

	static URI resolveToURI(URL base, String part) throws URISyntaxException {
		return resolveToURI(base.toURI(), part);
	}

	/**
	 * Get the metadata out of a single file.
	 * 
	 * @param ingestion
	 *            The info out of the OpenBIS ingestion process.
	 */
	private void extractMetadatum(Entry ent, IngestionResult ingestion)
			throws IOException, URISyntaxException {
		String cifs = resolveToURI(cifsRoot, ent.getName()).toString();
		metadata.addFile(
				ent.getName(),
				ent.getFile(),
				ent.getDestination(),
				cifs,
				ingestion != null ? resolveToURI(ingestion.dataRoot,
						ent.getName()) : null);
	}

	/**
	 * Get the metadata out of the files (identified by {@link #listFiles(File)}
	 * ).
	 * 
	 * @param ingestion
	 *            The info out of the OpenBIS ingestion process.
	 */
	protected void extractMetadata(IngestionResult ingestion) {
		if (ingestion != null)
			metadata.setOpenBISExperiment(ingestion.experimentID,
					ingestion.experimentURL);
		for (Entry ent : entries) {
			try {
				log.debug("task[" + myID + "] characterising " + ent.getFile());
				extractMetadatum(ent, ingestion);
			} catch (IOException | URISyntaxException e) {
				log.warn("task[" + myID + "] failed to generate metadata for "
						+ ent.getDestination(), e);
			} finally {
				metaCount++;
			}
			if (javaTask != null && javaTask.isCancelled())
				break;
		}
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
