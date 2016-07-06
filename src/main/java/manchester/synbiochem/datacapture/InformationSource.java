package manchester.synbiochem.datacapture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

public class InformationSource {
	private Map<String,String>instrumentTypes = new HashMap<>();

	@Value("#{'${instrument.types}'.split(',')}")
	private void setInstrumentTypes(List<String> items) {
		for (int i=0 ; i<items.size()-1 ; i+=2) {
			String name = items.get(i).trim().toLowerCase();
			String type = items.get(i+1).trim();
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
}
