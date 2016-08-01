package manchester.synbiochem.datacapture;

import static java.lang.String.format;
import static java.lang.Thread.sleep;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;

public class OpenBISIngester {
	public static final String MARKER_PREFIX = ".MARKER_is_finished_";
	public static final String OUT_PREFIX = ".MARKER_is_ingested_";

	private Log log = LogFactory.getLog(OpenBISIngester.class);

	@Value("${openbis.dssUrlRootPattern}")
	private String datasetRootPattern;
	@Value("${openbis.experimentUrlPattern}")
	private String experimentPattern;

	@Value("${openbis.dropbox}")
	public void setOpenbisDropbox(String json) {
		JSONObject obj = new JSONObject(json);
		Map<String, Map<String, File>> map = new HashMap<>();
		for (Object instObj : obj.keySet()) {
			String instrument = instObj.toString();
			JSONObject subobj = obj.getJSONObject(instrument);
			Map<String, File> submap = new HashMap<>();
			for (Object prjObj : subobj.keySet()) {
				String project = prjObj.toString();
				String dir = subobj.getString(project);
				submap.put(project.toLowerCase(), new File(dir));
			}
			map.put(instrument.toLowerCase(), submap);
		}
		dropboxDirectoryMap = map;
	}

	private Map<String, Map<String, File>> dropboxDirectoryMap;

	public static class IngestionResult {
		public String experimentID;
		public URL experimentURL;
		public String dataID;
		public URL dataRoot;
	}

	public IngestionResult ingest(File source, String instrument, String project)
			throws IOException, InterruptedException {
		log.info("looking up dropbox for instrument " + instrument
				+ " and project " + project);
		File dropbox = getDropbox(instrument.trim(), project.trim());
		if (dropbox == null) {
			log.info("no dropbox available - skipping ingest to openBIS");
			return null;
		}
		log.info("dropbox located at " + dropbox);
		Path target = copy(source.toPath(), dropbox.toPath(), COPY_ATTRIBUTES);
		File marker = new File(dropbox, MARKER_PREFIX + source.getName());
		marker.createNewFile();
		File outMarker = new File(dropbox, OUT_PREFIX + source.getName());
		// TODO Bound the ingestion time
		while (target.toFile().exists() && !outMarker.exists())
			sleep(1000);
		try {
			String dsid = null;
			String exid = null;
			for (String line : readAllLines(outMarker.toPath(),
					defaultCharset())) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#"))
					continue;
				if (dsid == null) {
					dsid = line;
				} else if (exid == null) {
					exid = line;
				}
				if (dsid != null && exid != null) {
					IngestionResult result = new IngestionResult();
					result.dataID = dsid;
					result.experimentID = exid;
					log.info("ingest complete: DataID:" + dsid + " ExpID:"
							+ exid);
					result.dataRoot = new URL(format(datasetRootPattern, dsid,
							source.getName()));
					result.experimentURL = new URL(format(experimentPattern,
							exid));
					return result;
				}
			}
			log.info("ingestion failed; please check openBIS logs for reason");
			return null;
		} finally {
			outMarker.delete();
		}
	}

	protected File getDropbox(String instrument, String project) {
		log.info("looking for " + instrument + " in " + dropboxDirectoryMap.keySet());
		Map<String, File> map = dropboxDirectoryMap.get(instrument
				.toLowerCase());
		if (map != null)
			log.info("looking for " + project + " in " + map.keySet());
		return map == null ? null : map.get(project.toLowerCase());
	}
}
