package model;

import javax.servlet.http.HttpServletRequest;

/*
 * Smap extension
 */
public class SummaryEndPoint {
	
	public String name;
	public String description;	
	public String url;
	public String columns;		// Available commas (select multiple)
	public String x;			// Available X dimensions (select 1)
	public String chartFormats;	// Select 1

	public SummaryEndPoint(HttpServletRequest request, String name, String description) {
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/api/v1/summary/";
		
		this.name = name;
		this.description = description;
		this.url = urlprefix + name;
	}
}
