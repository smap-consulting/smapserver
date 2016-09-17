package surveyKPI;

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
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.RoleManager;
import org.smap.sdal.model.Role;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Services for managing roles
 */

@Path("/utility")
public class Utility extends Application {
	
	Authorise a = null;

	private static Logger log =
			 Logger.getLogger(Utility.class.getName());
	
	private class SmapTimeZone {
		String offset;
		String id;
		
		public SmapTimeZone(String offset, String id) {
			this.offset = offset;
			this.id = id;
		}
	}
	
	public Utility() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		
		// Only allow administrators and analysts access to the security functions
		authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.ANALYST);
		a = new Authorise(authorisations, null);
		
	}
	
	/*
	 * Get available time zones
	 * Based on StackOverflow response http://stackoverflow.com/questions/33359955/sort-timezone-based-on-gmt-time-in-a-list-of-timezone-string
	 */
	@Path("/timezones")
	@GET
	@Produces("application/json")
	public Response getTimezones(
			@Context HttpServletRequest request
			) { 

		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			response = Response.serverError().build();
		    return response;
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-Utility");
		a.isAuthorised(sd, request.getRemoteUser());
		
		// End Authorisation
		
		try {
			ArrayList<SmapTimeZone> smapTzList = new ArrayList<SmapTimeZone> ();
			
			List<TimeZone> tzList = new ArrayList<>();
		    String[] ids = TimeZone.getAvailableIDs();
		    for (String id : ids) {
		        tzList.add(TimeZone.getTimeZone(id));
		    }
		    Collections.sort(tzList,
		                    new Comparator<TimeZone>() {
		        public int compare(TimeZone s1, TimeZone s2) {
		            return s1.getRawOffset() - s2.getRawOffset();
		        }
		    }); // Need to sort the GMT timezone here after getTimeZone() method call
		    for (TimeZone tz : tzList) {
		    	
		    	smapTzList.add(new SmapTimeZone(getTimeZone(tz), tz.getID()));

		    }
		    

			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(smapTzList);
			response = Response.ok(resp).build();
		} catch (Exception e) {
			
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE,"Error", e);

		} finally {
			
			SDDataSource.closeConnection("surveyKPI-roleList", sd);
		}

		return response;
	}
	

	
	/*
	 * Get the offset form UTC in a human readable format
	 * This code fragment is from: http://stackoverflow.com/questions/33359955/sort-timezone-based-on-gmt-time-in-a-list-of-timezone-string
	 */
	private static String getTimeZone(TimeZone tz) {
	    long hours = TimeUnit.MILLISECONDS.toHours(tz.getRawOffset());
	    long minutes = TimeUnit.MILLISECONDS.toMinutes(tz.getRawOffset()) 
	             - TimeUnit.HOURS.toMinutes(hours);
	    minutes = Math.abs(minutes);
	    String result = "";
	    if (hours > 0) {
	    result = String.format("(GMT+%d:%02d) %s", hours, minutes, tz.getID());
	    } else if (hours < 0) {
	    result = String.format("(GMT%d:%02d) %s", hours, minutes, tz.getID());
	    } else {
	        result = String.format("(GMT) %s", tz.getID());
	    }
	    return result;

	  }


}

