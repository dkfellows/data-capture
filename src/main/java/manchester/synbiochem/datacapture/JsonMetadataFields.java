package manchester.synbiochem.datacapture;

public interface JsonMetadataFields {
	/** JSON key for what is the record recorded from? */
	public static final String EXPERIMENT = "assay";
	/**
	 * JSON key for where the file was archived to, <i>relative to the archive
	 * root</i>: used in an individual file record.
	 */
	public static final String FILE_ARCHIVE = "archived";
	/**
	 * JSON key for the MD5 hash of the file's contents: used in an individual
	 * file record.
	 */
	public static final String FILE_MD5 = "md5";
	/**
	 * JSON key for the computed MIME type of the file: used in an individual
	 * file record.
	 */
	public static final String FILE_MIME = "mimetype";
	/**
	 * JSON key for the virtual name of the file: used in an individual file
	 * record. Note that the file's name in the archive is usually different.
	 */
	public static final String FILE_NAME = "name";
	/**
	 * JSON key for the location that the file originally came from: used in an
	 * individual file record.
	 */
	public static final String FILE_ORIGIN = "src";
	/**
	 * JSON key for the SHA1 hash of the file's contents: used in an individual
	 * file record.
	 */
	public static final String FILE_SHA1 = "sha1";
	/**
	 * JSON key for the modification time of the file: used in an individual
	 * file record.
	 */
	public static final String FILE_TIME = "time";
	/** JSON key for the list of files in the overall record. */
	public static final String FILES = "files";
	/** JSON key for the ID for the overall record. */
	public static final String ID = "id";
	/** JSON key for the timestamp for the overall record. */
	public static final String TIME = "timestamp";
	/** JSON key for who is creating the record? */
	public static final String USER = "uploader";
}
