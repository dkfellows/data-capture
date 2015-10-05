package manchester.synbiochem.datacapture;

import static java.lang.System.currentTimeMillis;
import static java.nio.file.Files.copy;
import static java.util.Collections.singletonList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ArchiverTask implements Callable<URL> {
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private Log log = LogFactory.getLog(ArchiverTask.class);

	private final MetadataRecorder metadata;
	private final List<File> directoriesToArchive;
	private final File archiveRoot;
	private final SeekConnector seek;
	private volatile int fileCount;
	private volatile int metaCount;
	private volatile int copyCount;
	private volatile boolean done;
	private Future<?> javaTask;
	private List<String> directories;
	Long start;
	Long finish;

	public ArchiverTask(MetadataRecorder metadata, File archiveRoot,
			File directoryToArchive, SeekConnector seek) {
		this.metadata = metadata;
		this.archiveRoot = archiveRoot;
		this.directoriesToArchive = singletonList(directoryToArchive);
		this.seek = seek;
		setupDirectories();
	}

	public ArchiverTask(MetadataRecorder metadata, File archiveRoot,
			Collection<File> directories, SeekConnector seek) {
		this.metadata = metadata;
		this.archiveRoot = archiveRoot;
		this.directoriesToArchive = new ArrayList<>(directories);
		this.seek = seek;
		setupDirectories();
	}

	private void setupDirectories() {
		directories = new ArrayList<>();
		for (File d : directoriesToArchive)
			directories.add(d.getAbsolutePath());
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
		start = currentTimeMillis();
		for (File dir : directoriesToArchive)
			addAndCopy(dir, archiveRoot);
		String name = "experiments/" + metadata.getId() + ".metadata.json";
		File metadataFile = new File(archiveRoot, name);
		metadataFile.getParentFile().mkdirs();
		String json = metadata.get();
		URL location = null;//FIXME
		try {
			location = seek.uploadFileAsset(metadata.getUser(),
					metadata.getExperiment(), name,
					"Experimental Results Metadata", "application/json", json);
		} catch (IOException e) {
			log.warn("failed to upload metadata to SEEK", e);
		}
		try (Writer w = new OutputStreamWriter(new FileOutputStream(
				metadataFile), UTF8)) {
			w.write(json);
		} catch (IOException e) {
			log.warn("failed to write metadata to " + metadataFile, e);
		} finally {
			done = true;
			finish = currentTimeMillis();
		}
		return location;
	}

	/**
	 * Discovers where all the files are in a folder, and what their names are
	 * mapped from (relative to the root folder passed in).
	 * 
	 * @param folder
	 *            The folder to look in.
	 * @return Mapping of name/file.
	 */
	public List<Entry> listFilesForFolder(File folder) {
		List<Entry> entries = new ArrayList<>();
		listFiles(entries, folder, folder.getName());
		return entries;
	}

	private void listFiles(List<Entry> entries, File folder, String location) {
		location = (location == null) ? "" : location + '/';
		for (File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory())
				listFiles(entries, fileEntry, location + fileEntry.getName());
			else if (fileEntry.isFile())
				entries.add(new Entry(location + fileEntry.getName(), fileEntry));
			if (javaTask != null && javaTask.isCancelled())
				break;
		}
	}

	/**
	 * Adds the files found in the directory to the metadata and returns a list
	 * of instructions on what is to be copied when the data is registered.
	 * 
	 * @param directory
	 *            Directory to find the data within.
	 * @return Mapping from files to the name to copy to within the target
	 *         structure.
	 */
	public List<Entry> addFilesToMetadata(File directory) {
		List<Entry> copyInstructions = new ArrayList<>();
		Set<String> done = new HashSet<>();
		List<Entry> fileList = listFilesForFolder(directory);
		fileCount = fileList.size();
		for (Entry entry : fileList)
			try {
				String target = metadata.addFile(entry.getName(),
						entry.getFile());
				if (done.add(target))
					copyInstructions.add(new Entry(target, entry.getFile()));
				else
					copyCount++;
			} catch (IOException e) {
				log.warn("failed to generate metadata for " + entry.getFile(),
						e);
			} finally {
				metaCount++;
				if (javaTask != null && javaTask.isCancelled())
					break;
			}
		return copyInstructions;
	}

	/**
	 * Perform the add of the files to the metadata record, and then copy the
	 * files to their target location(s).
	 * 
	 * @param toArchive
	 *            The root directory containing the files to archive.
	 * @param archiveRoot
	 *            The root of the archive that files will be copied relative to.
	 */
	public void addAndCopy(File toArchive, File archiveRoot) {
		for (Entry copyInstruction : addFilesToMetadata(toArchive)) {
			File target = new File(archiveRoot, copyInstruction.getName());
			target.getParentFile().mkdirs();
			try {
				copy(copyInstruction.getFile().toPath(), target.toPath());
			} catch (FileAlreadyExistsException e) {
				// Don't need to do anything about this
				log.debug(copyInstruction.getFile() + " already exists at "
						+ target);
			} catch (IOException e) {
				log.warn("failed to copy " + copyInstruction.getFile() + " to "
						+ target, e);
			} finally {
				copyCount++;
				if (javaTask != null && javaTask.isCancelled())
					break;
			}
		}
	}

	/**
	 * A name/file pair.
	 * 
	 * @author Donal Fellows
	 */
	public static class Entry {
		public Entry(String name, File file) {
			if (name == null || file == null)
				throw new IllegalArgumentException("arguments must not be null");
			this.name = name;
			this.file = file;
		}

		private final String name;
		private final File file;

		public String getName() {
			return name;
		}

		public File getFile() {
			return file;
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
