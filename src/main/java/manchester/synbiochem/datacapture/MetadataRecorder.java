package manchester.synbiochem.datacapture;

import static java.util.Collections.sort;
import static manchester.synbiochem.datacapture.Algorithm.MD5;
import static manchester.synbiochem.datacapture.Algorithm.SHA1;
import static manchester.synbiochem.datacapture.JsonMetadataFields.EXPERIMENT;
import static manchester.synbiochem.datacapture.JsonMetadataFields.EXP_OPENBIS_ID;
import static manchester.synbiochem.datacapture.JsonMetadataFields.EXP_OPENBIS_URL;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILES;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_ARCHIVE;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_CIFS;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_MD5;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_MIME;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_NAME;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_NOTES;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_OPENBIS_URL;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_ORIGIN;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_PROJECT;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_SEEK_URL;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_SHA1;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_SIZE;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_TIME;
import static manchester.synbiochem.datacapture.JsonMetadataFields.ID;
import static manchester.synbiochem.datacapture.JsonMetadataFields.TIME;
import static manchester.synbiochem.datacapture.JsonMetadataFields.USER;
import static org.json.JSONObject.NULL;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import manchester.synbiochem.datacapture.ArchiverTask.Entry;
import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.Study;
import manchester.synbiochem.datacapture.SeekConnector.User;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.tika.Tika;
import org.json.JSONArray;
import org.json.JSONObject;

public class MetadataRecorder {
	/** Standard timezone; Z(ulu) */
	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	private static final int BUFFER_SIZE = 8192;

	// Thread-safe: http://stackoverflow.com/a/11163920/301832
	private final Tika tika;

	private final String timestamp;
	/**
	 * ISO8601 timestamp formatter. DO NOT share between instances; not
	 * thread-safe.
	 */
	private final DateFormat ISO8601;
	private final Map<String, JSONObject> files;
	private final JSONObject o;
	private User user;
	private Assay assay;
	private Study study;
	private StringBuilder csvBuffer;
	private CSVPrinter csv;
	private Map<String, CSVRow> csvRows = new HashMap<>();
	private JSONObject openbisExperiment;
	private Object openbisExperimentID;
	private Object openbisExperimentURL;
	private String project;
	private String notes;
	private final Map<String, String> filetypeMap = new HashMap<>();

	private class CSVRow implements Comparable<CSVRow> {
		CSVRow(File archived, File source, String sha1, String md5,
				String mimetype, long size, Date time, String cifs,
				URI openbis) {
			this.archived = archived.getAbsolutePath();
			this.source = source.getAbsolutePath();
			this.sha1 = sha1;
			this.md5 = md5;
			this.mimetype = mimetype;
			this.size = size;
			this.time = time;
			this.cifs = cifs;
			this.openbis = openbis;
			this.seek = "";
		}

		String sha1, source;
		Object archived, md5, mimetype, size, time, cifs, openbis, seek;

		void write() {
			addRecord(getExperiment().url, getUser().url, openbisExperimentID,
					openbisExperimentURL, timestamp, archived, source, sha1,
					md5, mimetype, size, ISO8601.format(time), cifs, seek,
					project, notes, openbis);
		}

		@Override
		public int compareTo(CSVRow o) {
			int cmp = sha1.compareTo(o.sha1);
			if (cmp == 0)
				cmp = source.compareTo(o.source);
			return cmp;
		}
	}

	public MetadataRecorder(Tika tika, String project, String notes) {
		ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		ISO8601.setTimeZone(UTC);
		this.tika = tika;

		timestamp = ISO8601.format(new Date());
		o = new JSONObject();
		o.put(ID, "");
		o.put(TIME, "");
		o.put(USER, NULL);
		o.put(EXPERIMENT, NULL);
		o.put(FILE_PROJECT, project);
		o.put(FILE_NOTES, notes);
		files = new HashMap<>();
		csvBuffer = new StringBuilder();
		try {
			csv = new CSVPrinter(csvBuffer, CSVFormat.TDF);
		} catch (IOException e) {
			throw new RuntimeException("unexpected IO failure", e);
		}
		addRecord(EXPERIMENT, USER, EXP_OPENBIS_ID, EXP_OPENBIS_URL, TIME,
				FILE_ARCHIVE, FILE_ORIGIN, FILE_SHA1, FILE_MD5, FILE_MIME,
				FILE_SIZE, FILE_TIME, FILE_CIFS, FILE_SEEK_URL, FILE_PROJECT,
				FILE_NOTES, FILE_OPENBIS_URL);
	}

	/**
	 * Force there to be exactly 11 columns in the CSV.
	 */
	private void addRecord(Object a1, Object a2, Object a3, Object a4,
			Object a5, Object a6, Object a7, Object a8, Object a9, Object a10,
			Object a11, Object a12, Object a13, Object a14, Object a15,
			Object a16, Object a17) {
		try {
			csv.printRecord(a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12,
					a13, a14, a15, a16, a17);
		} catch (IOException e) {
			throw new RuntimeException("unexpected IO failure", e);
		}
	}

	protected final void addFile(String sha1, String md5, String name,
			String mimetype, File source, File archived, long size,
			String cifs, URI openbis) {
		Date time = new Date(source.lastModified());
		String key = source.getAbsolutePath();
		filetypeMap.put(key, mimetype);
		JSONObject f = new JSONObject();
		f.put(FILE_SHA1, sha1);
		f.put(FILE_MD5, md5);
		f.put(FILE_NAME, name);
		f.put(FILE_MIME, mimetype);
		f.put(FILE_ORIGIN, source.getAbsolutePath());
		f.put(FILE_ARCHIVE, archived.getAbsolutePath());
		f.put(FILE_TIME, ISO8601.format(time));
		f.put(FILE_SIZE, size);
		f.put(FILE_CIFS, cifs);
		f.put(FILE_OPENBIS_URL, openbis);
		files.put(key, f);
		csvRows.put(key, new CSVRow(archived, source, sha1, md5, mimetype,
				size, time, cifs, openbis));
	}

	/**
	 * Add the given file to the metadata record with the given name. This is an
	 * expensive operation.
	 * 
	 * @param name
	 *            The name of the file that should be used as the user-visible
	 *            name.
	 * @param source
	 *            The original location of the file.
	 * @param archived
	 *            The file to add. Will have checksums computed and its MIME
	 *            type determined.
	 * @param cifs
	 *            The direct location for the file on the filestore at the time
	 *            that this record was created. Not guaranteed to stay relevant.
	 * @param openbis
	 *            The location on the OpenBIS DSS for the file. Persistent.
	 * @return The archive location to copy the file to.
	 * @throws IOException
	 *             If anything goes wrong when computing checksums or MIME
	 *             types.
	 */
	public void addFile(String name, File source, File archived, String cifs,
			URI openbis) throws IOException {
		Digest sha1 = new Digest(SHA1);
		Digest md5 = new Digest(MD5);
		byte[] buffer = new byte[BUFFER_SIZE];
		long size = source.length();
		try (FileInputStream fis = new FileInputStream(archived)) {
			int len;
			while ((len = fis.read(buffer)) >= 0) {
				sha1.update(buffer, len);
				md5.update(buffer, len);
			}
		}
		addFile(sha1.toString(), md5.toString(), name, tika.detect(source),
				source, archived, size, cifs, openbis);
	}

	public void setExperiment(Assay experiment) {
		if (experiment == null)
			o.put(EXPERIMENT, NULL);
		else
			o.put(EXPERIMENT, experiment.url.toString());
		this.assay = experiment;
	}

	public void setExperiment(Study experiment) {
		if (experiment == null)
			o.put(EXPERIMENT, NULL);
		else
			o.put(EXPERIMENT, experiment.url.toString());
		this.study = experiment;
	}

	public Assay getExperiment() {
		return assay;
	}

	public String getProjectName() {
		if (project != null)
			return project;
		if (assay != null)
			return assay.projectName;
		if (study != null)
			return study.projectName;
		return null;
	}

	public void setUser(User user) {
		if (user == null)
			o.put(USER, NULL);
		else
			o.put(USER, user.url.toString());
		this.user = user;
	}

	public User getUser() {
		return user;
	}

	public String getFileType(File originFile) {
		return filetypeMap.get(originFile.getAbsolutePath());
	}

	/**
	 * Get the ID of the document. <strong>NB:</strong> this finalizes the
	 * document the first time it is called.
	 * 
	 * @return The ID (<i>implementation detail:</i> computed from a SHA-1 hash
	 *         of the file mapping)
	 */
	public String getId() {
		String id = o.getString(ID);
		if (id.isEmpty()) {
			ArrayList<JSONObject> files = new ArrayList<>(this.files.values());
			sort(files, comparator);
			JSONArray a = new JSONArray();
			for (JSONObject f : files) {
				a.put(f.get(FILE_ORIGIN));
				a.put(f.get(FILE_SHA1));
			}
			id = new Digest(SHA1).update(a.toString()).toString();
			o.put(TIME, timestamp);
			o.put(ID, id);
			o.put(FILES, files);
			o.put("OpenBISExperiment", openbisExperiment);
		}
		return id;
	}

	/**
	 * Get the JSON document. <strong>NB:</strong> this finalizes the document
	 * the first time it is called.
	 * 
	 * @return JSON in a string.
	 */
	public String get() {
		getId();
		return o.toString(4);
	}

	/**
	 * Get the CSV document.
	 * 
	 * @return CSV in a string.
	 */
	public synchronized String getCSV() {
		if (csvRows != null) {
			List<CSVRow> rows = new ArrayList<>(csvRows.values());
			csvRows = null;
			sort(rows);
			for (CSVRow r : rows)
				r.write();
		}
		return csvBuffer.toString();
	}

	/**
	 * How to compare two entries describing the metadata of individual files
	 * for ordering. We only need one instance of this. It's entirely
	 * thread-safe.
	 */
	private static final Comparator<JSONObject> comparator = new MetadataFileComparator();

	private static class MetadataFileComparator implements
			Comparator<JSONObject> {
		@Override
		public int compare(JSONObject o1, JSONObject o2) {
			String s1 = o1.getString(FILE_SHA1);
			String s2 = o2.getString(FILE_SHA1);
			int cmp = s1.compareTo(s2);
			if (cmp == 0) {
				s1 = o1.getString(FILE_ORIGIN);
				s2 = o2.getString(FILE_ORIGIN);
				cmp = s1.compareTo(s2);
			}
			return cmp;
		}
	}

	private static String emptyIfNull(Object o) {
		if (o == null)
			return "";
		return o.toString();
	}

	public void setOpenBISExperiment(String experimentID, URL experimentURL) {
		openbisExperimentID = emptyIfNull(experimentID);
		openbisExperimentURL = emptyIfNull(experimentURL);
		openbisExperiment = new JSONObject();
		if (experimentID != null) {
			openbisExperiment.put("id", openbisExperimentID);
			openbisExperiment.put("url", openbisExperimentURL);
		}
	}

	public void setSeekLocation(Entry ent, URL seekURL) {
		JSONObject obj = files.get(ent.getName());
		if (obj != null)
			obj.put(FILE_SEEK_URL, seekURL.toString());
		CSVRow row = csvRows.get(ent.getName());
		if (row != null)
			row.seek = seekURL.toString();
	}
}
