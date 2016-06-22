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

import managers.DataManager;
import model.DataEndPoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Column;
import org.smap.sdal.model.Survey;

import utils.Utils;

/*
 * Provides access to collected data
 */
@Path("/v1/data")
public class Data extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Data.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Data.class);
		return s;
	}
	
	public Data() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * KoboToolBox API version 1 /data
	 * Returns a list of data end points
	 */
	@GET
	@Produces("application/json")
	public Response getData(@Context HttpServletRequest request) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolBoxAPI-getData");
		a.isAuthorised(sd, request.getRemoteUser());
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().build();
		}
		
		DataManager dm = new DataManager();
		try {
			ArrayList<DataEndPoint> data = dm.getDataEndPoints(sd, request, false);
			
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			String resp = gson.toJson(data);
			response = Response.ok(resp).build();
		} catch (SQLException e) {
			e.printStackTrace();
			response = Response.serverError().build();
		} finally {
			SDDataSource.closeConnection("koboToolBoxAPI-getData", sd);
		}

		return response;
	}
	
	/*
	 * KoboToolBox API version 1 /data
	 * Get records for an individual survey in JSON format
	 */
	@GET
	@Produces("application/json")
	@Path("/{sId}")
	public Response getDataRecords(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@QueryParam("start") int start,
			@QueryParam("limit") int limit,
			@QueryParam("mgmt") boolean mgmt,
			@QueryParam("sort") String sort,			// Column Human Name to sort on
			@QueryParam("dirn") String dirn,			// Sort direction, asc || desc
			@QueryParam("form") int fId,				// Form id (optional only specify for a child form)
			@QueryParam("parkey") int parkey,			// Parent key (optional, use to get records that correspond to a single parent record)
			@QueryParam("hrk") String hrk				// Unique key (optional, use to restrict records to a specific hrk)
			) { 
		
		Response response = null;
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolboxApi - get data records");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		Connection cResults = ResultsDataSource.getConnection("koboToolboxApi - get data records");
		
		String sqlGetMainForm = "select f_id, table_name from form where s_id = ? and parentform = 0;";
		PreparedStatement pstmtGetMainForm = null;
		
		String sqlGetForm = "select parentform, table_name from form where s_id = ? and f_id = ?;";
		PreparedStatement pstmtGetForm = null;
		
		
		PreparedStatement pstmtGetData = null;
		
		StringBuffer columnSelect = new StringBuffer();
		String table_name = null;
		int parentform = 0;
		ResultSet rs = null;

		if(sort != null && dirn == null) {
			dirn = "asc";
		}
		
		try {

			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);
			
			if(fId == 0) {
				pstmtGetMainForm = sd.prepareStatement(sqlGetMainForm);
				pstmtGetMainForm.setInt(1,sId);
				
				log.info("Getting main form: " + pstmtGetMainForm.toString() );
				rs = pstmtGetMainForm.executeQuery();
				if(rs.next()) {
					fId = rs.getInt(1);
					table_name = rs.getString(2);
				}
			} else {
				pstmtGetForm = sd.prepareStatement(sqlGetForm);
				pstmtGetForm.setInt(1,sId);
				pstmtGetForm.setInt(2,fId);
				
				log.info("Getting specific form: " + pstmtGetForm.toString() );
				rs = pstmtGetForm.executeQuery();
				if(rs.next()) {
					parentform = rs.getInt(1);
					table_name = rs.getString(2);
				}
			}
				
			ArrayList<Column> columns = GeneralUtilityMethods.getColumnsInForm(
					sd,
					cResults,
					parentform,
					fId,
					table_name,
					false,
					false,		// Don't include parent key
					false,		// Don't include "bad" columns
					true		// include instance id
					);
			
			if(mgmt) {
				GeneralUtilityMethods.addManagementColumns(columns);
			}
			
			for(int i = 0; i < columns.size(); i++) {
				Column c = columns.get(i);
				if(i > 0) {
					columnSelect.append(",");
				}
				columnSelect.append(c.getSqlSelect(urlprefix));
			}
			
			if(GeneralUtilityMethods.tableExists(cResults, table_name)) {
				
				String sqlGetData = "select " + columnSelect.toString() + " from " + table_name
						+ " where prikey >= ?";
				String sqlSelect = "";
				if(parkey > 0) {
					sqlSelect = " and parkey = ?";
				}
				if(hrk != null) {
					sqlSelect = " and _hrk = ?";
				}
				
				String sqlGetDataOrder = null;
				if(sort != null) {
					// User has requested a specific sort order
					sqlGetDataOrder = " order by " + getSortColumn(columns, sort) + " " + dirn + ";";
				} else {
					// Set default sort order
					if(mgmt) {
						sqlGetDataOrder = " order by prikey desc;";
					} else {
						sqlGetDataOrder = " order by prikey asc;";
					}
				}
				
				pstmtGetData = cResults.prepareStatement(sqlGetData + sqlSelect + sqlGetDataOrder);
				int paramCount = 1;
				pstmtGetData.setInt(paramCount++, start);
				if(parkey > 0) {
					pstmtGetData.setInt(paramCount++, parkey);
				}
				if(hrk != null) {
					pstmtGetData.setString(paramCount++, hrk);
				}
				
				log.info("Get data: " + pstmtGetData.toString());
				rs = pstmtGetData.executeQuery();
				
				int index = 0;
				JSONArray ja = new JSONArray();
				while (rs.next()) {
					
					if(limit > 0 && index >= limit) {
						break;
					}
					index++;
					
					JSONObject jr = new JSONObject();

					for(int i = 0; i < columns.size(); i++) {	
						
						Column c = columns.get(i);
						String name = null;
						String value = null;
						
						if(c.isGeometry()) {							
							// Add Geometry (assume one geometry type per table)
							String geomValue = rs.getString(i + 1);	
							name = "_geolocation";
							JSONArray coords = null;
							if(geomValue != null) {
								JSONObject jg = new JSONObject(geomValue);									
								coords = jg.getJSONArray("coordinates");
							} else {
								coords = new JSONArray();
							}
							jr.put(name, coords);
				
						} else {
							
							//String name = rsMetaData.getColumnName(i);	
							name = c.humanName;
							value = rs.getString(i + 1);	
							
							if(value == null) {
								value = "";
							}
							
							if(name != null) {
								jr.put(Utils.translateToKobo(name), value);
							}
						}
						
				
					}
						
					ja.put(jr);
				}
						

				response = Response.ok(ja.toString()).build();
			} else {
				response = Response.ok("{msg: No data}").build();
			}
			
			
			
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmtGetMainForm != null) {pstmtGetMainForm.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetForm != null) {pstmtGetForm.close();	}} catch (SQLException e) {	}
			try {if (pstmtGetData != null) {pstmtGetData.close();	}} catch (SQLException e) {	}
			
			ResultsDataSource.closeConnection("koboToolboxApi - get data records", cResults);			
			SDDataSource.closeConnection("koboToolboxApi - get data records", sd);
		}
		
		return response;
		
	}
	
	/*
	 * Convert the human name for the sort column into sql
	 */
	private String getSortColumn(ArrayList<Column> columns, String sort) {
		String col = "prikey";	// default to prikey
		sort = sort.trim();
		for(int i = 0; i < columns.size(); i++) {
			if(columns.get(i).humanName.equals(sort)) {
				Column c = columns.get(i);

				if(c.isCalculate()) {
					col = c.calculation;
				} else {
					col = c.name;
				}
				break;
			}
		}
		return col;
	}
	
	
}

