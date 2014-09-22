package org.smap.subscribers;
/*****************************************************************************

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

 ******************************************************************************/

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.http.entity.ContentType;
import org.smap.model.IE;

public class Utilities {
	/*
	 * Get a substring of a date that is in ISO 8601 format
	 */
	public static String getPartDate(String fullDate, String format) throws ParseException {
		
		String partDate = null;
		
		// parse the input date
		SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");  // ISO8601 date formats - add timezone after upgrade of java rosa libraries
		SimpleDateFormat outFormat = new SimpleDateFormat(format);
		Date theDate = null;

		theDate = inFormat.parse(fullDate);
		partDate = outFormat.format(theDate).toString();
		
		return partDate;
}
	
	public static String getPartLocation(String location, String dimension) {
		
		String partLocation= "0.0";
		
		String vals[] = location.split(" ");
		if(vals.length > 2) {
			if(dimension.equals("lat")) {
				partLocation = vals[0];
			} else if(dimension.equals("lon")) {
				partLocation = vals[1];
			}
		}	
		
		return partLocation;
	}
	
	/*
	 * Get the content type from the filename
	 */
	public static String getContentType(String filename) {
		
		String ct = null;
		String extension = "";
		int idx = filename.lastIndexOf('.');
		if(idx > 0) {
			extension = filename.substring(idx+1);
		}
		
	      if (extension.equals("xml")) {
          	ct = "text/xml";
          } else if (extension.equals("jpg") || extension.equals("jpeg") || extension.equals("jpe")) {
          	ct = "image/jpeg";
          } else if (extension.equals("png")) {
            	ct = "image/png";
          } else if (extension.equals("3gp")) {
          	ct = "video/3gp";
          } else if (extension.equals("3ga")) {
            ct = "audio/3ga";
          } else if (extension.equals("mp2") || extension.equals("mp3") || extension.equals("mpga")) {
            ct = "audio/mpeg";
          } else if (extension.equals("mpeg") || extension.equals("mpg") || extension.equals("mpe")) {
            ct = "video/mpeg";
          } else if (extension.equals("qt") || extension.equals("mov")) {
            ct = "video/quicktime";
          } else if (extension.equals("mp4") || extension.equals("m4p")) {
          	ct = "video/mp4";
          } else if (extension.equals("avi")) {
            ct = "video/x-msvideo";
          } else if (extension.equals("movie")) {
            ct = "video/x-sgi-movie";
          } else if (extension.equals("m4a")) {
          	ct = "audio/m4a";
          } else if (extension.equals("csv")) {
          	ct = "text/csv";
          } else if (extension.equals("amr")) {
          	ct = "audio/amr";
          } else if (extension.equals("xls")) {
          	ct = "application/vnd.ms-excel";
          }  else {
          	ct = "application/octet-stream";
          	System.out.println("	Info: unrecognised content type for extension " + extension);           
          }
		
		return ct;
	}
}
