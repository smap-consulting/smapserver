package model;

import java.util.HashMap;

public class DataEndPoint {
	
	// Kobo
	public int id;
	public String id_string;
	public String title;
	public String description;		// Not available in Smap will be just title
	public String url;
	public HashMap<String, String> subforms = null;
	
	// Smap Extensions
	//public String project;
	
	// Methods
	public String getCSVColumns() {
		return "id, id_string, title, description, url";
	}
	public String getCSVData() {
		StringBuffer r = new StringBuffer();
		
		r.append(id);
		
		r.append(",");
		if(id_string != null) {
			r.append("\"" + id_string.replaceAll("\"", "\"\"") + "\"");
		}
		
		r.append(",");
		if(title != null) {
			r.append("\"" + title.replaceAll("\"", "\"\"") + "\"");
		}
		
		r.append(",");
		if(description != null) {
			r.append("\"" + description.replaceAll("\"", "\"\"") + "\"");
		}
		
		r.append(",");
		if(url != null) {
			r.append("\"" + url.replaceAll("\"", "\"\"") + "\"");
		}
		
		return r.toString();
	}
}
