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
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import manchester.synbiochem.datacapture.OpenBISIngester.IngestionResult;
import manchester.synbiochem.datacapture.SeekConnector.Study;

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
	/**
	 * The real content type of the CSV files we generate. Yes, this is actually
	 * tab-separated; that's actually more portable.
	 */
	private static final String CSV_CONTENT_TYPE = "text/tab-separated-values";
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
	final SeekConnector seek;
	final OpenBISIngester ingester;
	final InformationSource info;
	final String machine;
	final String project;
	private volatile int fileCount;
	private volatile int metaCount;
	private volatile int copyCount;
	private volatile int linkCount;
	private volatile boolean done;
	private Future<?> javaTask;
	private final List<Entry> entries;
	Long start;
	Long finish;
	DateFormat ISO8601;
	DateFormat HUMAN_READABLE;

	ArchiverTask(File dir) {
		directoryToArchive = dir;
		entries = new ArrayList<>();
		// Init stuff
		myID = 0;
		seek = null;
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

	public ArchiverTask(MetadataRecorder metadata, File archiveRoot,
			File metastoreRoot, URI cifsRoot, File directoryToArchive,
			SeekConnector seek, OpenBISIngester ingester,
			InformationSource infoSource) {
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
		this.seek = seek;
		this.ingester = ingester;
		this.entries = new ArrayList<>();
		this.info = infoSource;
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
		int files = fileCount, metas = metaCount, copies = copyCount, links = linkCount;
		if (files == 0)
			return null;
		return (metas + copies + links) / (files * 3.0);
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

		setState("registering");

		if (metadata.getExperiment() == null)
			makeAssay(ingestion);

		setState("meta-ing");

		extractMetadata(ingestion);
		if (isCancelled())
			return null;

		setState("bagging-it");
		bagItUp();

		setState("finishing");

		saveJsonManifest();
		return tellSeek(ingestion);
	}

	private void saveJsonManifest() {
		String name = directoryToArchive.getName() + ".json";
		File jsonFile = new File(metastoreRoot, name);
		try {
			FileUtils.write(jsonFile, metadata.get(), UTF8);
		} catch (IOException e) {
			final String MSG = "task[%d] failed to write metadata descriptor to %s";
			log.warn(format(MSG, myID, jsonFile), e);
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

	protected void makeAssay(IngestionResult ingestion) {
		// Do nothing in this class; subclass might implement
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

	// Construct the actual archive of the data. NOT YET DONE
	protected void bagItUp() {
		try {
			// TODO Need to actually do the build of the bagit
		} finally {

		}
	}

	private String describeLinkEntry(Entry ent) {
		Date ts = new Date(start);
		return "File copied from <i>" + ent.getFile()
				+ "</i> of (presumed) type <i>"
				+ metadata.getFileType(ent.getFile())
				+ "</i> and generated by <i>"
				+ info.getMachineName(directoryToArchive)
				+ "</i>; this upload was done at <abbr title=\""
				+ ISO8601.format(ts) + "\">" + HUMAN_READABLE.format(ts)
				+ "</abbr>.\n\n<b>Links</b>\n<a href=\""
				+ resolveToURI(cifsRoot, ent.getName())
				+ "\">Data Store (CIFS)</a>";
	}

	private String titleOfLinkEntry(Entry ent) {
		String tail = ent.getName().replaceFirst(".*/", "");
		log.info("chopped " + ent.getName() + " to " + tail);
		return "Experimental Results: " + tail;
	}

	private String describeManifest(IngestionResult ingestion) {
		String instrument = info.getMachineName(directoryToArchive);
		String time = ISO8601.format(new Date(start));
		String date = HUMAN_READABLE.format(new Date(start));
		String description = "<i>CSV document</i> describing the manifest of "
				+ "files copied from instrument <i>" + instrument
				+ "</i> to the Synology storage at timestamp <abbr title=\""
				+ time + ">" + date + "</abbr>.";
		if (ingestion != null)
			description += "\n\n" + "The data is also <a href=\""
					+ ingestion.dataRoot + "\">available in OpenBIS</a>.";
		return description;
	}

	// Turn off links; combination of brokenness in SEEK and OpenBIS
	private static boolean USE_SEEK_LINKS = false;

	/**
	 * Send the metadata to SEEK.
	 * 
	 * @return the location on SEEK of the descriptor we uploaded.
	 */
	protected URL tellSeek(IngestionResult ingestion) {
		try {
			for (Entry ent : entries) {
				if (USE_SEEK_LINKS && ingestion != null)
					putLinkToFileInSeek(ent, ingestion);
				linkCount++;
			}
		} catch (URISyntaxException e) {
			log.warn("unexpected failure to construct URI into OpenBIS", e);
		} catch (RuntimeException e) {
			log.warn("failed to notify SEEK about file; skipping remaining links");
		} finally {
			linkCount = metaCount;
		}

		// Finalize the metadata NOW
		metadata.get();
		String description = describeManifest(ingestion);
		return seek.uploadFileAsset(metadata.getUser(),
				metadata.getExperiment(), "metadata.tsv", description,
				"Experimental Results Manifest", CSV_CONTENT_TYPE,
				metadata.getCSV());
	}

	private void putLinkToFileInSeek(Entry ent, IngestionResult ingestion)
			throws URISyntaxException {
		String title = titleOfLinkEntry(ent);
		String description = describeLinkEntry(ent);
		URI openBisURI = resolveToURI(ingestion.dataRoot, ent.getName());
		URL seekURL = seek.linkFileAsset(metadata.getUser(),
				metadata.getExperiment(), description, title, openBisURI);
		metadata.setSeekLocation(ent, seekURL);
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

class StudyCreatingArchiverTask extends ArchiverTask {
	final Study study;

	public StudyCreatingArchiverTask(Study study, MetadataRecorder metadata,
			File archiveRoot, File metastoreRoot, URI cifsRoot,
			File directoryToArchive, SeekConnector seek,
			OpenBISIngester ingester, InformationSource infoSource) {
		super(metadata, archiveRoot, metastoreRoot, cifsRoot,
				directoryToArchive, seek, ingester, infoSource);
		this.study = study;
	}

	private static final String ASSAY_DESCRIPTION_TEMPLATE = "Capture of data "
			+ "relating to '<i>%s</i>' from the %s instrument in the <i>%s</i>"
			+ " project context at <abbr title=\"%s\">%s</abbr>.\n\n<b>Links</b>\n";
	private static final String ASSAY_EXPLINK_TEXT = "The OpenBIS Experiment";
	private static final String ASSAY_DSLINK_TEXT = "The OpenBIS DataSet";

	private String describeAssay(String title, IngestionResult ingestion) {
		StringBuilder buffer = new StringBuilder();
		Date timestamp = new Date(start);
		buffer.append(format(ASSAY_DESCRIPTION_TEMPLATE, title, machine,
				project, ISO8601.format(timestamp),
				HUMAN_READABLE.format(timestamp)));
		if (ingestion == null) {
			buffer.append(ASSAY_EXPLINK_TEXT).append(": <em>unknown</em>\n")
					.append(ASSAY_DSLINK_TEXT).append(": <em>unknown</em>");
		} else {
			buffer.append("<a href=\"").append(ingestion.experimentURL)
					.append("\">").append(ASSAY_EXPLINK_TEXT)
					.append("</a> (permlink)\n<a href=\"")
					.append(ingestion.dataRoot).append("\">")
					.append(ASSAY_DSLINK_TEXT).append("</a> (permlink)");
		}
		return buffer.toString();
	}

	/*
	 * Not the greatest way of creating a title, but not too problematic
	 * either.
	 */
	private String getAssayTitle(String directoryName) {
		String title = directoryName.replace("_", " ");
		if (title.matches("^\\d+ \\d+ \\d+ .*$")) {
			// If the leading part looks like a date, make it look more like a date
			String[] parts = title.split(" +", 4);
			title = parts[0] + "/" + parts[1] + "/" + parts[2] + " " + parts[3];
		}
		return title;
	}

	@Override
	protected void makeAssay(IngestionResult ingestion) {
		String directoryName = directoryToArchive.getAbsolutePath()
				.replaceFirst("/+$", "").replaceFirst(".*/", "")
				.replace("_", " ");
		String description = describeAssay(directoryName, ingestion);
		String title = getAssayTitle(directoryName);

		URL url = seek.createExperimentalAssay(metadata.getUser(), study,
				description, title);

		log.info("created assay at " + url);
		metadata.setExperiment(seek.getAssay(url));
	}
}
