package manchester.synbiochem.datacapture;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.Study;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * Class and bean used to work out what instrument and project are in play for a
 * particular ingestion.
 * 
 * @author Donal Fellows
 */
public class InformationSource {
	private Log log = LogFactory.getLog(getClass());
	private Map<String, String> instrumentTypes = new HashMap<>();
	@Value("${project.master.name:synbiochem}")
	private String projectMasterName;
	@Value("${project.default.instrument.type:SBC}")
	private String defaultInstrumentType;

	@Value("#{'${instrument.types}'.split(',')}")
	private void setInstrumentTypes(List<String> items) {
		for (int i = 0; i < items.size() - 1; i += 2) {
			String name = items.get(i).trim().toLowerCase();
			String type = items.get(i + 1).trim();
			instrumentTypes.put(name, type);
		}
	}

	public String getInstrumentType(String instrumentName) {
		String type = instrumentTypes.get(instrumentName.toLowerCase());
		if (type == null)
			type = defaultInstrumentType;
		return type;
	}

	public boolean hasMachineName(String name) {
		return instrumentTypes.get(name.toLowerCase()) != null;
	}

	public String getMachineName(File sourceDir) {
		log.info("getting machine name from directory: " + sourceDir);
		File sd = sourceDir;
		do {
			sd = sd.getParentFile();
		} while (sd != null && !hasMachineName(sd.getName()));
		return sd == null ? sourceDir.getParentFile().getName() : sd.getName();
	}

	public String getMachineName(String sourceDir) {
		return getMachineName(new File(sourceDir));
	}

	private String getProjectName(String machine, String projectName) {
		log.info("getting internal project name for machine:" + machine
				+ " and project:" + projectName);
		String prefix = getInstrumentType(machine);
		String project = "capture";
		if (projectName != null)
			project = projectName.replaceAll("[^a-zA-Z0-9]+", "-");
		if (project.equalsIgnoreCase(projectMasterName))
			project = "other";
		return prefix + "-" + (project.toLowerCase());
	}

	public String getProjectName(String machine, MetadataRecorder metadata) {
		return getProjectName(machine, metadata.getProjectName());
	}

	public String getProjectName(String machine, Assay assay) {
		return getProjectName(machine, assay.projectName);
	}

	public String getProjectName(String machine, Study study) {
		return getProjectName(machine, study.projectName);
	}
}
