package model;
/*
This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

*/
import javax.servlet.http.HttpServletRequest;

/*
 * Smap extension
 */
public class TasksEndPoint {
	
	public String name;
	public String description;	
	public String url;
	public String group;			// Available groups (select one or none)
	public String x;				// Available X dimensions (select one or none)
	public String period;			// Period values if the x dimension is set and is a date / time
	public String chartFormats;		// Select one or none
	public String filters;			// Optional can select many
	public String example;

	public TasksEndPoint(HttpServletRequest request, String name, String description, 
			String group, String x, String period, String filters, String example) {
		
		String urlprefix = request.getScheme() + "://" + request.getServerName() + "/api/v1/tasks";
		
		this.name = name;
		this.description = description;
		if(name != null) {
			this.url = urlprefix + "/" + name;
		} else {
			this.url = urlprefix;
		}
		this.group = group;
		this.x = x;
		this.period = period;
		this.filters = filters;
		
		// Create an example url
		this.example = this.url + example; 
	}
}
