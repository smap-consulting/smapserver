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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;

import model.Subscriber;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Return information about subscribers to upload events
 */

@Path("/subscribers")
public class SubscriberSvc extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(SubscriberSvc.class.getName());

	
	@GET
	@Produces("application/json")
	public Response getSubscribers(@Context HttpServletRequest request) { 
		
		Response response = null;

		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-SubscriberSvc");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		ArrayList<Subscriber> subscriberList = new ArrayList<Subscriber> ();

		PreparedStatement pstmt = null;
		try {
					
			String sql = "select distinct subscriber from subscriber_event order by subscriber desc;";
			ResultSet resultSet = null;
			log.info(sql);
			pstmt = connectionSD.prepareStatement(sql);	 			
			resultSet = pstmt.executeQuery();

			while (resultSet.next()) {								
				
				Subscriber aSubscriber = new Subscriber();
				subscriberList.add(aSubscriber);
				aSubscriber.name = resultSet.getString(1);
			}
				
			Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").create();
			String resp = gson.toJson(subscriberList);
			response = Response.ok(resp).build();
			
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		} catch (Exception e) {
			log.log(Level.SEVERE,"", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-SubscriberSvc", connectionSD);
			
		}

		return response;
	}


}

