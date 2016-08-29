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

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.TableColumn;

/*
 * Returns data for the passed in table name
 */
@Path("/v1/data.csv")
public class Data_CSV extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Data_CSV.class.getName());
	
	LogManager lm = new LogManager();		// Application log

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Data_CSV.class);
		return s;
	}
	
	public Data_CSV() {
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * KoboToolBox API version 1 /data.csv
	 * CSV version
	 */
	@GET
	@Produces("text/csv")
	public void getDataCsv(@Context HttpServletRequest request,
			@Context HttpServletResponse response) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolBoxApi-getDataCSV");
		a.isAuthorised(sd, request.getRemoteUser());
		
		StringBuffer record = null;
		PrintWriter outWriter = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
			try {response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);} catch(Exception ex) {};
		}
		
		DataManager dm = new DataManager();
		try {
			ArrayList<DataEndPoint> data = dm.getDataEndPoints(sd, request, true);
			
			outWriter = response.getWriter();
		
			if(data.size() > 0) {
				for(int i = 0; i < data.size(); i++) {
					DataEndPoint dep = data.get(i);
					if(i == 0) {
						outWriter.print(dep.getCSVColumns() + "\n");
					}
					outWriter.print(dep.getCSVData() + "\n");
			
				}
			} else {
				outWriter.print("No Data");
			}
			
			outWriter.flush(); 
			outWriter.close();
			
		} catch (Exception e) {
			e.printStackTrace();
			try {response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);} catch(Exception ex) {};
		} finally {
			SDDataSource.closeConnection("koboToolBoxApi-getDataCSV", sd);
		}


	}
	
	/*
	 * KoboToolBox API version 1 /data
	 */
	@GET
	@Produces("text/csv")
	@Path("/{sId}")
	public void getDataRecords(@Context HttpServletRequest request,
			@Context HttpServletResponse response,
			@PathParam("sId") int sId,
			@QueryParam("start") int start,
			@QueryParam("limit") int limit) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("koboToolboxApi - get data records csv");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		lm.writeLog(sd, sId, request.getRemoteUser(), "view", "API CSV view");
		
		Connection cResults = ResultsDataSource.getConnection("koboToolboxApi - get data records csv");
		
		String sqlGetMainForm = "select f_id, table_name from form where s_id = ? and parentform = 0;";
		PreparedStatement pstmtGetMainForm = null;
		PreparedStatement pstmtGetData = null;
		
		StringBuffer columnSelect = new StringBuffer();
		StringBuffer columnHeadings = new StringBuffer();
		StringBuffer record = null;
		PrintWriter outWriter = null;

		try {
			outWriter = response.getWriter();
			String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);
			
			pstmtGetMainForm = sd.prepareStatement(sqlGetMainForm);
			pstmtGetMainForm.setInt(1,sId);
			
			log.info("Getting main form: " + pstmtGetMainForm.toString() );
			ResultSet rs = pstmtGetMainForm.executeQuery();
			if(rs.next()) {
				int fId = rs.getInt(1);
				String table_name = rs.getString(2);
				
				ArrayList<TableColumn> columns = GeneralUtilityMethods.getColumnsInForm(
						sd,
						cResults,
						sId,
						request.getRemoteUser(),
						0,			// parent form
						fId,
						table_name,
						false,
						false,		// Don't include parent key
						false,		// Don't include "bad" columns
						false,		// Don't include instance id
						true		// Include other meta data
						);
				
				for(int i = 0; i < columns.size(); i ++) {
					TableColumn c = columns.get(i);
					if(i > 0) {
						columnSelect.append(",");
						columnHeadings.append(",");
					}
					columnSelect.append(c.getSqlSelect(urlprefix));
					columnHeadings.append(c.humanName);
				}
				
				if(GeneralUtilityMethods.tableExists(cResults, table_name)) {
					
					outWriter.print(columnHeadings.toString() + "\n");
					
					String sqlGetData = "select " + columnSelect.toString() + " from " + table_name
							+ " where prikey >= ?"
							+ " order by prikey asc;";
					
					pstmtGetData = cResults.prepareStatement(sqlGetData);
					pstmtGetData.setInt(1, start);
					
					log.info("Get CSV data: " + pstmtGetData.toString());
					rs = pstmtGetData.executeQuery();
					
					int index = 0;
					while (rs.next()) {
						
						if(limit > 0 && index >= limit) {
							break;
						}
						index++;
						
						record = new StringBuffer();
						
						for(int i = 0; i < columns.size(); i++) {
							TableColumn c = columns.get(i);
							if(i > 0) {
								record.append(",");
							}
							
							String val = rs.getString(i + 1);
							if(val != null) {	
								record.append("\"" + val.replaceAll("\"", "\"\"") + "\"");
							}
						}
						
						record.append("\n");
						outWriter.print(record.toString());
					
					}
				} else {
					outWriter.print("No data\n");
				}
				
				outWriter.flush(); 
				outWriter.close();
				
			}
			
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			try {response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);} catch(Exception ex) {};
		} finally {
			
			try {if (pstmtGetMainForm != null) {pstmtGetMainForm.close();	}} catch (SQLException e) {	}
			
			ResultsDataSource.closeConnection("koboToolboxApi - get data records csv", cResults);
			SDDataSource.closeConnection("koboToolboxApi - get data records csv", sd);
		}
		

	}
	
	
}

