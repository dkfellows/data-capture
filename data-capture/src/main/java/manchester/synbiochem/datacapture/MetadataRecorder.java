package manchester.synbiochem.datacapture;

import static java.util.Collections.sort;
import static manchester.synbiochem.datacapture.Algorithm.MD5;
import static manchester.synbiochem.datacapture.Algorithm.SHA1;
import static manchester.synbiochem.datacapture.JsonMetadataFields.EXPERIMENT;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILES;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_ARCHIVE;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_MD5;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_MIME;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_NAME;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_ORIGIN;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_SHA1;
import static manchester.synbiochem.datacapture.JsonMetadataFields.FILE_TIME;
import static manchester.synbiochem.datacapture.JsonMetadataFields.ID;
import static manchester.synbiochem.datacapture.JsonMetadataFields.TIME;
import static manchester.synbiochem.datacapture.JsonMetadataFields.USER;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.json.JSONObject.NULL;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.User;

import org.apache.tika.Tika;
import org.json.JSONArray;
import org.json.JSONObject;

public class MetadataRecorder {
	/** Standard timezone; Z(ulu) */
	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
	/**
	 * Number of leading characters from filename to use for archive directory
	 * name.
	 */
	private static final int FILE_PREFIX_LENGTH = 2;

	// TODO Can we share this between instances
	private final Tika tika;

	private final Date timestamp;
	/**
	 * ISO8601 timestamp formatter. DO NOT share between instances; not
	 * thread-safe.
	 */
	private final DateFormat ISO8601;
	private final List<JSONObject> files;
	private final JSONObject o;
	private User user;
	private Assay assay;

	public MetadataRecorder(Tika tika) {
		ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		ISO8601.setTimeZone(UTC);
		this.tika = tika;

		timestamp = new Date();
		o = new JSONObject();
		o.put(ID, "");
		o.put(TIME, "");
		o.put(USER, NULL);
		o.put(EXPERIMENT, NULL);
		files = new ArrayList<>();
	}

	public void addFile(String sha1, String md5, String name, String mimetype,
			String source, String archived, Date time) {
		JSONObject f = new JSONObject();
		f.put(FILE_SHA1, sha1);
		f.put(FILE_MD5, md5);
		f.put(FILE_NAME, name);
		f.put(FILE_MIME, mimetype);
		f.put(FILE_ORIGIN, source);
		f.put(FILE_ARCHIVE, archived);
		f.put(FILE_TIME, ISO8601.format(time));
		files.add(f);
	}

	/**
	 * Add the given file to the metadata record with the given name. This is an
	 * expensive operation.
	 * 
	 * @param name
	 *            The name of the file that should be used as the user-visible
	 *            name.
	 * @param source
	 *            The file to add. Will have checksums computed and its MIME
	 *            type determined.
	 * @return The archive location to copy the file to.
	 * @throws IOException
	 *             If anything goes wrong when computing checksums or MIME
	 *             types.
	 */
	public String addFile(String name, File source) throws IOException {
		Digest sha1 = new Digest(SHA1);
		Digest md5 = new Digest(MD5);
		byte[] buffer = new byte[8192];
		int len;
		try (FileInputStream fis = new FileInputStream(source)) {
			while ((len = fis.read(buffer)) >= 0) {
				sha1.update(buffer, len);
				md5.update(buffer, len);
			}
		}
		String archived = "data/"
				+ sha1.toString().substring(0, FILE_PREFIX_LENGTH) + "/" + sha1
				+ "." + getExtension(source.getName());
		addFile(sha1.toString(), md5.toString(), name, tika.detect(source),
				source.getAbsolutePath(), archived,
				new Date(source.lastModified()));
		return archived;
	}

	public void setExperiment(Assay experiment) {
		if (experiment == null)
			o.put(EXPERIMENT, NULL);
		else
			o.put(EXPERIMENT, experiment.url.toString());
		this.assay = experiment;
	}

	public Assay getExperiment() {
		return assay;
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
			sort(files, comparator);
			JSONArray a = new JSONArray();
			for (JSONObject f : files) {
				a.put(f.get(FILE_ORIGIN));
				a.put(f.get(FILE_SHA1));
			}
			id = new Digest(SHA1).update(a.toString()).toString();
			o.put(TIME, ISO8601.format(timestamp));
			o.put(ID, id);
			o.put(FILES, files);
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
}
