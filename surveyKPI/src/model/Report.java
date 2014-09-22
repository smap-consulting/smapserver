package model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

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

public class Report {
	public String version = "1.0";
	public String type;				// Item type: photo || video || rich
	public int width;				// Width in pixels
	public int height;				// Height in pixels
	public String title;
	public String url;				// url for image or video
	public String html;				// HTML for type rich
	public String author_name;
	public String provider_name = "Smap";
	public String provider_url;		// Smap server
	public String thumbnail_url;	// Optional link to a thumbnail for the above image
	public int thumbnail_width;		// Thumbnail width in pixels
	public int thumbnail_height;	// Thumbnail height in pixels

	public ReportSmap smap = new ReportSmap();
	

}
