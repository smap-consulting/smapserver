package koboToolboxApi;
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
import javax.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.SubmissionsManager;

/*
 * Provides access to collected data
 */
@Path("/v1/submissions")
public class Submissions extends Application {

	Authorise a = null;
	Authorise aSuper = null;

	private static Logger log =
			Logger.getLogger(Submissions.class.getName());

	LogManager lm = new LogManager();		// Application log

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Submissions.class);
		return s;
	}

	public Submissions() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
	}

	/*
	 * Get submission records in JSON formats
	 */
	@GET
	@Produces("application/json")
	public void getDataRecordsService(@Context HttpServletRequest request,
			@Context HttpServletResponse response,
			@QueryParam("start") int start,				// Primary key to start from
			@QueryParam("limit") int limit,				// Number of records to return
			@QueryParam("tz") String tz,					// Timezone
			@QueryParam("geojson") String geojson,		// if set to yes then format as geoJson
			@QueryParam("startDate") Date startDate,
			@QueryParam("endDate") Date endDate,
			@QueryParam("stopat") int stopat,
			@QueryParam("user") String user,
			@QueryParam("links") String links
			) throws ApplicationException, Exception { 
		
		
		getSubmissions(request, response, start, limit,  tz, geojson, 
				startDate,
				endDate,
				user,
				links,
				stopat);
	}
	

	
	/*
	 * KoboToolBox API version 1 /data
	 * Get records for an individual survey in JSON format
	 */
	private void getSubmissions(HttpServletRequest request,
			HttpServletResponse response,
			int start,				// Primary key to start from
			int limit,				// Number of records to return
			String tz,				// Timezone
			String geoJson,
			Date startDate,
			Date endDate,
			String user,
			String links,
			int stopat
			) throws ApplicationException, Exception { 

		String connectionString = "koboToolboxApi - get data records";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		boolean isGeoJson = false;
		if(geoJson != null && (geoJson.equals("yes") || geoJson.equals("true"))) {
			isGeoJson = true;
		}
		
		boolean incLinks = false;
		if(links != null && (links.equals("yes") || links.equals("true"))) {
			incLinks = true;
		}
		
		if(tz == null) {
			tz = GeneralUtilityMethods.getOrganisationTZ(sd, 
					GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser()));
		}
		tz = (tz == null) ? "UTC" : tz;
		
		int dateId = 1;			// Upload time

		PrintWriter outWriter = null;
		try {
		
			response.setContentType("application/json; charset=UTF-8");
			response.setCharacterEncoding("UTF-8");
			outWriter = response.getWriter();
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			if(!GeneralUtilityMethods.isApiEnabled(sd, request.getRemoteUser())) {
				throw new ApplicationException(localisation.getString("susp_api"));
			}

			if(isGeoJson) {
				outWriter.print("{\"type\":\"FeatureCollection\",");		// type
																		// TODO metadata
				outWriter.print("\"features\":");						// Features
			}
			
			// Add feature data
			outWriter.print("[");
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			SubmissionsManager subMgr = new SubmissionsManager(localisation, tz);
			String whereClause = subMgr.getWhereClause(user, oId, dateId, startDate, endDate, stopat);	
				
			// page the results to reduce memory usage
			log.info("---------------------- paging results to postgres");
			sd.setAutoCommit(false);		
			pstmt = subMgr.getSubmissionsStatement(
					sd, 
					limit, 
					start, 
					whereClause,
					user,		// user to filter on
					oId,			// oId to filter on
					request.getRemoteUser(),
					dateId,
					startDate,
					endDate,
					stopat);
			pstmt.setFetchSize(100);	
			
			log.info("Get submissions: " + pstmt.toString());
			rs = pstmt.executeQuery();
			
			int index = 0;
			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);
			while(rs.next()) {
				
				JSONObject jo  =  subMgr.getRecord(rs, isGeoJson, false, true, incLinks, urlprefix);
				if(jo != null) {
					if(index > 0) {
						outWriter.print(",");
					}
					outWriter.print(jo.toString());
				}
				
				index++;
				if (limit > 0 && index >= limit) {
					break;
				}

			}
			
			sd.setAutoCommit(true);		// page the results to reduce memory
			
			outWriter.print("]");
			
			
			if(isGeoJson) {
				outWriter.print("}");	// close
			}

		} catch (Exception e) {
			try {sd.setAutoCommit(true);} catch(Exception ex) {};
			log.log(Level.SEVERE, "Exception", e);
			outWriter.print(e.getMessage());
		} finally {

			outWriter.flush(); 
			outWriter.close();
			
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
				
			SDDataSource.closeConnection(connectionString, sd);
		}

		//return response;

	}


}

