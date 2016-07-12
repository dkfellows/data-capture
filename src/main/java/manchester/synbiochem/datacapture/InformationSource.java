package manchester.synbiochem.datacapture;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.Study;

import org.springframework.beans.factory.annotation.Value;

public class InformationSource {
	private Map<String, String> instrumentTypes = new HashMap<>();
	@Value("${project.master.name:synbiochem}")
	private String projectMasterName;

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
			type = "SBC";
		return type;
	}

	public boolean hasMachineName(String name) {
		return instrumentTypes.get(name.toLowerCase()) != null;
	}

	public String getMachineName(File sourceDir) {
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
		String prefix = getInstrumentType(machine);
		String project = "capture";
		if (projectName != null)
			project = projectName.replaceAll("[^a-zA-Z0-9]+", "-");
		if (project.equalsIgnoreCase(projectMasterName))
			project = "other";
		return prefix + "-" + (project.toLowerCase());
	}

	public String getProjectName(String machine, MetadataRecorder metadata) {
		return getProjectName(machine, metadata.getExperiment().projectName);
	}

	public String getProjectName(String machine, Assay assay) {
		return getProjectName(machine, assay.projectName);
	}

	public String getProjectName(String machine, Study study) {
		return getProjectName(machine, study.projectName);
	}
}
