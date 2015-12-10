package manchester.synbiochem.datacapture;

class MultipartFormData {
	private static final String DASH = "--";
	private static final String N = "\r\n";
	private static final String PREFIX = "SynBioChemMetadataUploadFormToken";
	private static final String CD = "Content-Disposition: form-data; name=\"";
	private final StringBuilder b = new StringBuilder();
	private final String token;
	private byte[] bytes;

	public MultipartFormData(String content) {
		String token;
		int tokenid = 10203040;
		do {
			token = PREFIX + (tokenid++);
		} while (content.indexOf(token) >= 0);
		this.token = token;
	}

	private void addSeparator(boolean terminal) {
		b.append(DASH).append(token);
		if (terminal)
			b.append(DASH);
		b.append(N);
	}

	public void addField(String field) {
		addField(field, "");
	}

	public void addField(String field, Object value) {
		addSeparator(false);
		b.append(CD).append(field).append("\"" + N + N).append(value)
				.append(N);
	}

	public void addContent(String field, String name, String type,
			String content) {
		addSeparator(false);
		b.append(CD).append(field).append("\"; filename=\"").append(name)
				.append("\"").append(N).append("Content-Type: ")
				.append(type).append(N)
				.append("Content-Transfer-Encoding: binary" + N + N)
				.append(content).append(N);
	}

	public void build() {
		addSeparator(true);
		bytes = b.toString().getBytes(SeekConnector.UTF8);
	}

	public String contentType() {
		return "multipart/form-data; boundary=" + token;
	}

	public String length() {
		assert bytes != null : "form must be built before use";
		return Integer.toString(bytes.length);
	}

	public byte[] content() {
		assert bytes != null : "form must be built before use";
		return bytes;
	}
}