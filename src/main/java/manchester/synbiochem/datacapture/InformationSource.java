package manchester.synbiochem.datacapture;

import static java.util.Collections.unmodifiableList;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.BadRequestException;

import manchester.synbiochem.datacapture.SeekConnector.Assay;
import manchester.synbiochem.datacapture.SeekConnector.Project;
import manchester.synbiochem.datacapture.SeekConnector.Study;
import manchester.synbiochem.datacapture.SeekConnector.User;

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
	private List<User> users;
	private List<Project> projects;

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
		String project = "other";
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

	@Value("${userList}")
	private void setUsers(String userList) {
		List<User> users = new ArrayList<>();
		for (String userInfo : userList.split(",")) {
			String[] details = userInfo.split(":", 3);
			User u = new User();
			try {
				if (!details[0].isEmpty())
					u.id = Integer.parseInt(details[0].trim());
			} catch (NumberFormatException e) {
				log.error("problem parsing user ID: " + details[0], e);
			}
			if (!details[1].isEmpty())
				u.name = details[1].trim();
			try {
				if (!details[2].isEmpty())
					u.url = new URL(details[2].trim());
			} catch (MalformedURLException e) {
				log.error("problem parsing user URL: " + details[2], e);
			}
			users.add(u);
		}
		this.users = unmodifiableList(users);
	}

	public List<User> getUsers() {
		return users;
	}

	public User getUser(URL url) {
		String urlstr = url.toString();
		for (User u : users)
			if (u.url.toString().equals(urlstr))
				return u;
		throw new BadRequestException("no such user recognised");
	}

	@Value("${projectList}")
	private void setProjects(String projectList) {
		List<Project> projects = new ArrayList<>();
		for (String projectInfo : projectList.split(",")) {
			String[] details = projectInfo.split(":", 3);
			Project p = new Project();
			try {
				if (!details[0].isEmpty())
					p.id = Integer.parseInt(details[0].trim());
			} catch (NumberFormatException e) {
				log.error("problem parsing project ID: " + details[0], e);
			}
			if (!details[1].isEmpty())
				p.name = details[1].trim();
			try {
				if (!details[2].isEmpty())
					p.url = new URL(details[2].trim());
			} catch (MalformedURLException e) {
				log.error("problem parsing project URL: " + details[2], e);
			}
			projects.add(p);
		}
		this.projects = unmodifiableList(projects);
	}

	public List<Project> getProjects() {
		return projects;
	}

	public Project getProject(URL url) {
		if (url != null) {
			String urlstr = url.toString();
			for (Project p : projects)
				if (p.url.toString().equals(urlstr))
					return p;
		}
		throw new BadRequestException("no such project recognised");
	}
}
