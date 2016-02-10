package model;

import javax.servlet.http.HttpServletRequest;

/*
 * Smap extension
 */
public class SummaryEndPoint {
	
	public String name;
	public String description;	
	public String url;
	public String group;			// Available groups (select one or none)
	public String x;				// Available X dimensions (select one or none)
	public String period;			// Period values if the x dimension is set and is a date / time
	public String chartFormats;		// Select one or none
	public String example;

	public SummaryEndPoint(HttpServletRequest request, String name, String description, 
			String group, String x, String period, String example) {
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/api/v1/summary/";
		
		this.name = name;
		this.description = description;
		this.url = urlprefix + name;
		this.group = group;
		this.x = x;
		this.period = period;
		
		// Create an example url
		this.example = this.url + example; 
	}
}
