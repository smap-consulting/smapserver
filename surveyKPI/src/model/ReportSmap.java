package model;

import java.sql.Timestamp;

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

/*
 * Holds smap specific report items
 */
public class ReportSmap {
	
		public String country;
		public String region;
		public String district;
		public String community;
		public String category;
		public String description;
		public int project_id;			// Project where this report was first created (All subsequent access / editing must be in the same organisation)
		public String project_name;		
		public String ident;
		public Timestamp pub_date;
		public int epoch;				// Publish date in seconds since 1/1/1970
		public String data_type;		// image || table || map || graph
		public String data_url;			// url of data in geoJSON
		public boolean live = false;	// Set true if the data url connects to live data
		public Timeframe timeframe = new Timeframe();	// Bounding timeframe for report
		public String bbox[];			// Bounding box of report
		public String report_url;		// URL that returns this report
		public String data_gen;			// Data returned by browser
		public String data_gen_capture;	// Screen capture data returned by browser, for example snapshot of graph (never set by server)
		public String data_gen_type;	// Type of data returned by browser

}
