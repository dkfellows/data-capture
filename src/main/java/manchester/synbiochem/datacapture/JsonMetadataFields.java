package manchester.synbiochem.datacapture;

public interface JsonMetadataFields {
	/** Key for what is the record recorded from? */
	public static final String EXPERIMENT = "assay";
	/**
	 * Key for where the file was archived to, <i>relative to the archive
	 * archRoot</i>: used in an individual file record.
	 */
	public static final String FILE_ARCHIVE = "archived";
	/**
	 * Key for the MD5 hash of the file's contents: used in an individual
	 * file record.
	 */
	public static final String FILE_MD5 = "md5";
	/**
	 * Key for the computed MIME type of the file: used in an individual
	 * file record.
	 */
	public static final String FILE_MIME = "mimetype";
	/**
	 * Key for the virtual name of the file: used in an individual file
	 * record. Note that the file's name in the archive is usually different.
	 */
	public static final String FILE_NAME = "name";
	/**
	 * Key for the location that the file originally came from: used in an
	 * individual file record.
	 */
	public static final String FILE_ORIGIN = "src";
	/**
	 * Key for the SHA1 hash of the file's contents: used in an individual
	 * file record.
	 */
	public static final String FILE_SHA1 = "sha1";
	/**
	 * Key for the modification time of the file: used in an individual
	 * file record.
	 */
	public static final String FILE_TIME = "time";
	/** Key for the size of the file: used in an individual file record. */
	public static final String FILE_SIZE = "size";
	/** Key for the list of files in the overall record. */
	public static final String FILES = "files";
	/** Key for the ID for the overall record. */
	public static final String ID = "id";
	/** Key for the timestamp for the overall record. */
	public static final String TIME = "timestamp";
	/** Key for who is creating the record? */
	public static final String USER = "uploader";
}
