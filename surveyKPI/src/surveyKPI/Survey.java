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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import model.Settings;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.ServerSideCalculate;
import org.smap.server.utilities.GetXForm;

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/*
 * Return meta information about a survey
 *  For each table in the survey
 *  	Table Name
 *  	Form Name
 *  	Number of rows
 *  	If the table has a geometry column
 *      If it does, the bounding box of the survey
 *  For the whole survey
 *      The survey id
 *  	For each date column
 *  		The question id
 *  		The question name
 */

@Path("/survey/{sId}")
public class Survey extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Survey.class.getName());

	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Survey.class);
		return s;
	}
	
	public Survey() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);
		
	}
	
	@Path("/download")
	@GET
	public Response getSurveyDownload(@Context HttpServletRequest request,
			@QueryParam("type") String type,
			@QueryParam("language") String language,
			@PathParam("sId") int sId) { 
		
		ResponseBuilder builder = Response.ok();
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation

		if(type == null) {
			type = "xml";
		}
		
		PreparedStatement pstmt = null;
		try {
			String sourceName = null;
			String display_name = null;
			String fileBasePath = null;		// File path excluding extensions
			String folderPath = null;
			String filename = null;
			String filepath = null;
			String sourceExt = null;
			int projectId = 0;
					
			String sql = null;
			ResultSet resultSet = null;
		
			/*
			 * Get the survey name (file name)
			 */
			sql = "SELECT s.display_name, s.p_id " +
					"FROM survey s " + 
					"where s.s_id = ?;";
			
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, sId);
			log.info("Get survey details: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
			
			if (resultSet.next()) {				
				display_name = resultSet.getString(1);
				projectId = resultSet.getInt(2);
				
				String basePath = GeneralUtilityMethods.getBasePath(request);
				String target_name = GeneralUtilityMethods.convertDisplayNameToFileName(display_name);
				
				fileBasePath = basePath + "/templates/" + projectId + "/" + target_name; 
				folderPath = basePath + "/templates/" + projectId;

				String ext;		
				if(type.equals("codebook")) {
					ext = "_gen.pdf";		// Codebooks are written as PDF files 
					sourceExt = "_gen.xml";		// input name is xml for a codebook file
				} else if(type.equals("xls")) {
					ext = ".xls";
					sourceExt = ".xls";
				} else if(type.equals("xml")) {	
					ext = "_gen.xml";		// Generate xml
					sourceExt = "_gen.xml";
				} else {
					ext = "." + type;
					sourceExt = "." + type;
				}
				sourceName = fileBasePath + sourceExt;

				log.info("Source name: " + sourceName + " type: " + type);
				/*
				 * The XML file for a code book or an XML download needs to be generated so that it contains the latest changes
				 */
				if(type.equals("codebook") || type.equals("xml")) {
					
					try {
						SurveyTemplate template = new SurveyTemplate();
						template.readDatabase(sId);
						GetXForm xForm = new GetXForm();
						
						String xmlForm = xForm.get(template, false);
						
					    // 1. Create the project folder if it does not exist
					    File folder = new File(folderPath);
					    FileUtils.forceMkdir(folder);
					    
						File f = new File(sourceName);
						
						// 2. Re-Create the file
						if(f.exists()) {
							f.delete();
						} 
						f.createNewFile();
						FileWriter fw = new FileWriter(f.getAbsoluteFile());
						BufferedWriter bw = new BufferedWriter(fw);
						bw.write(xmlForm);
						bw.close();
						
						log.info("Written xml file to: " + f.getAbsoluteFile());
					} catch (Exception e) {
						log.log(Level.SEVERE, "", e);
					}
					

				}
						 
				// Check for the existence of the source file, if it isn't at the standard location try obsolete locations
				File sourceFile = new File(sourceName);
				if(!sourceFile.exists()) {
					// Probably this is an old survey that is missing the projectId path in the path
					log.info("Locating old survey");
							
					if(type.equals("xls")) {
						fileBasePath = basePath + "/templates/xls/" + target_name; // Old xls files were in their own folder
					} else {
						fileBasePath = basePath + "/templates/" + target_name;
					}
					sourceName =  fileBasePath + sourceExt;	
				}

				filepath = fileBasePath + ext;
				filename = target_name + ext;
				
				try {  		
	        		int code = 0;
					if(type.equals("codebook")) {
						Process proc = Runtime.getRuntime().exec(new String [] {"/bin/sh", "-c", "/usr/bin/smap/gettemplate.sh " + sourceName +
								" " + language +
	        					" >> /var/log/tomcat7/survey.log 2>&1"});
						code = proc.waitFor();
						log.info("Process exitValue: " + code);
					}
	        		
	                File file = new File(filepath);
	                byte [] fileData = new byte[(int)file.length()];
	                DataInputStream dis = new DataInputStream(new FileInputStream(file));
	                dis.readFully(fileData);
	                dis.close();
	                
	                if(type.equals("codebook")) {
	                	builder.header("Content-type","application/pdf; charset=UTF-8");
	                } else if(type.equals("xls")) {
	                	builder.header("Content-type","application/vnd.ms-excel; charset=UTF-8");
	                } else if(type.equals("xml")) {
	                	builder.header("Content-type","text/xml; charset=UTF-8");
	                }
	                builder.header("Content-Disposition", "attachment;Filename=" + filename);
					builder.entity(fileData);
					
					response = builder.build();
	                
	    		} catch (Exception e) {
	    			log.log(Level.SEVERE, "", e);
	    			response = Response.serverError().entity("<h1>Error retrieving " + type + " file</h1><p>" + e.getMessage() + "</p>").build();
	        	}
			} else {
				response = Response.serverError().entity("Invalid survey name: " + sourceName).build();
				
			}
	
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);
			
		}

		return response;
	}
	
	/*
	 * Get the Survey Meta data
	 */
	private class DateInfo {	// Temporary storage of the array of date questions
		int qId;
		String name;
		String columnName;
		int fId;
		Date first;
		Date last;
	}
	
	@Path("/getMeta")
	@GET
	@Produces("application/json")
	public Response getSurveyMeta(@Context HttpServletRequest request,
			@PathParam("sId") int sId) { 
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		// End Authorisation
		
		JSONObject jo = new JSONObject();

		Connection connectionRel = null; 
		PreparedStatement pstmt = null;
		PreparedStatement pstmt2 = null;
		PreparedStatement pstmt3 = null;
		PreparedStatement pstmtTables = null;
		PreparedStatement pstmtGeom = null;
		

		try {
			String sqlTables = "SELECT DISTINCT f.table_name, f.name, f_id, f.parentform FROM form f " +
					"where f.s_id = ? " + 
					"order by f.table_name;";		
			pstmtTables = connectionSD.prepareStatement(sqlTables);	
			
			String sqlGeom = "SELECT q.q_id " +
					"FROM form f, question q " + 
					" where f.f_id = q.f_id " +
					" AND (q.qtype='geopoint' " +
					" OR q.qtype='geopolygon' OR q.qtype='geolinestring'" +
					" OR q.qtype='geoshape' OR q.qtype='geotrace') " +
					" AND f.f_id = ?" + 
					" AND f.s_id = ?; ";
			pstmtGeom = connectionSD.prepareStatement(sqlTables);	
			
			// Add the sId to the response so that it is available in the survey meta object
			jo.put("sId", sId);
			
			connectionRel = ResultsDataSource.getConnection("surveyKPI-Survey");
					
			String sql = null;
			ResultSet resultSet = null;
			ResultSet resultSetTable = null;
			ResultSet resultSetBounds = null;
			ArrayList<DateInfo> dateInfoList = new ArrayList<DateInfo> ();
			JSONArray ja = null;

			/*
			 * Get Date columns available in this survey
			 * The maximum and minimum value for these dates will be added when 
			 * the results data for each table is checked
			 */
			sql = "SELECT q.q_id, q.qname, f.f_id, q.column_name " +
					"FROM form f, question q " + 
					"where f.f_id = q.f_id " +
					"AND (q.qtype='date' " +
					"OR q.qtype='dateTime') " +
					"AND f.s_id = ?; "; 	
			
			
			log.info(sql);
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, sId);
			resultSet = pstmt.executeQuery();
			
			while (resultSet.next()) {	
				DateInfo di = new DateInfo();
				di.qId = resultSet.getInt(1);
				di.name = resultSet.getString(2);
				di.fId = resultSet.getInt(3);
				di.columnName = resultSet.getString(4);
				dateInfoList.add(di);
			}			
			
			/*
			 * Get Forms and row counts in this survey
			 */
			pstmtTables.setInt(1, sId);
			resultSet = pstmtTables.executeQuery();

			ja = new JSONArray();
			float [] bbox = new float[4]; 
			bbox[0] = 180;
			bbox[1] = 90;
			bbox[2] = -180;
			bbox[3] = -90;
			while (resultSet.next()) {								
					
				String tableName = resultSet.getString(1);
				String formName = resultSet.getString(2);
				int fId = resultSet.getInt(3);
				String p_id = resultSet.getString(4);
				int rowCount = 0;
				boolean has_geom = false;
				String geom_id = null;
				String bounds = null;
				
				try {
					sql = "SELECT COUNT(*) FROM " + tableName + ";";
					log.info(sql);
					try {if (pstmt2 != null) {pstmt2.close();}} catch (SQLException e) {}
					pstmt2 = connectionRel.prepareStatement(sql);
					resultSetTable = pstmt2.executeQuery();
					if(resultSetTable.next()) {
						rowCount = resultSetTable.getInt(1);
					}
					
				} catch (Exception e) {
					// If the table has not been created yet set row count to the default=0
				}
				
				// Get any geometry questions for this table
				pstmtGeom = connectionSD.prepareStatement(sqlGeom);
				pstmtGeom.setInt(1, fId);
				pstmtGeom.setInt(2, sId);
				resultSetTable = pstmtGeom.executeQuery();
				if(resultSetTable.next()) {
					geom_id = resultSetTable.getString(1);
					has_geom = true;
				}
				
				// Get the table bounding box
				try {
					if(has_geom) {
						sql = "SELECT ST_Extent(the_geom) as table_extent FROM " +
								 tableName + ";";
						log.info(sql);
						try {if (pstmt3 != null) {pstmt3.close();}} catch (SQLException e) {}
						pstmt3 = connectionRel.prepareStatement(sql);
						resultSetBounds = pstmt3.executeQuery();
						if(resultSetBounds.next()) {
							bounds = resultSetBounds.getString(1);
							if(bounds != null) {
								addToSurveyBounds(bbox, bounds);
							}
						}
					}
				} catch (Exception e) {
					// If the table has not been created don't set the table bounds
				}
				
				/*
				 * Get first last record of any date fields
				 */
				for(int i = 0; i < dateInfoList.size(); i++) {
					DateInfo di = dateInfoList.get(i);
					if(fId == di.fId) {
						try {
							String name = di.columnName;
							sql = "select min(" + name + "), max(" + name + ") FROM " + tableName + ";";
							
							try {if (pstmt2 != null) {pstmt2.close();}} catch (SQLException e) {}
							pstmt2 = connectionRel.prepareStatement(sql);
							log.info("Get max, min dates: " + pstmt2.toString());
							resultSetTable = pstmt2.executeQuery();
							if(resultSetTable.next()) {
								di.first = resultSetTable.getDate(1);
								di.last = resultSetTable.getDate(2);
							}
							
						} catch (Exception e) {
							// Ignore errors, for example table not created
						}
					}
				}
				
				
				JSONObject jp = new JSONObject();
				jp.put("name", tableName);
				jp.put("form", formName);
				jp.put("rows", rowCount);
				jp.put("geom", has_geom);
				jp.put("f_id", fId);
				jp.put("p_id", p_id);
				if(p_id == null || p_id.equals("0")) {
					jo.put("top_table", tableName);
				}
				jp.put("geom_id", geom_id);
				ja.put(jp);

			} 
			jo.put("forms", ja);
			
			/*
			 * Add the date information
			 */
			ja = new JSONArray();
			for (int i = 0; i < dateInfoList.size(); i++) {
				DateInfo di = dateInfoList.get(i);
				JSONObject jp = new JSONObject();
				jp.put("id", di.qId);
				jp.put("name", di.name);
				jp.put("first", di.first);
				jp.put("last", di.last);
				ja.put(jp);
			}			
			jo.put("dates", ja);
			
			/*
			 * Add the bounding box
			 *  Don't set the bbox if there is no location data, that is the left is greater than right
			 *  If there was only one point then add a buffer around that point
			 */		
			if(bbox[0] <= bbox[2] && bbox[1] <= bbox[3]) {
				if(bbox[0] == bbox[2]) {	// Zero width
					bbox[0] -= 0.05;		// Size in degrees 
					bbox[2] += 0.05;
				}
				if(bbox[1] == bbox[3]) {	// Zero height
					bbox[1] -= 0.05;		// Size in degrees 
					bbox[3] += 0.05;
				}
				JSONArray bb = new JSONArray();
				for(int i = 0; i < bbox.length; i++) {
					bb.put(bbox[i]);
				}
				jo.put("bbox", bb);
			} 
			

			
			/*
			 * Get other survey details
			 */
			sql = "SELECT s.display_name, s.deleted, s.p_id, s.ident, s.model " +
					"FROM survey s " + 
					"where s.s_id = ?;";
			
			log.info(sql);
			if(pstmt != null) try {pstmt.close();}catch(Exception e) {}
			pstmt = connectionSD.prepareStatement(sql);
			pstmt.setInt(1, sId);
			resultSet = pstmt.executeQuery();
			
			if (resultSet.next()) {				
				jo.put("name", resultSet.getString(1));
				jo.put("deleted", resultSet.getBoolean(2));
				jo.put("project", resultSet.getInt(3));
				jo.put("survey_ident", resultSet.getString(4));
				jo.put("model", resultSet.getString(5));
			}
			
			String resp = jo.toString();
			response = Response.ok(resp).build();
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		} catch (JSONException e) {
			log.log(Level.SEVERE,"", e);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			try {if (pstmt2 != null) {pstmt2.close();}} catch (SQLException e) {}
			try {if (pstmt3 != null) {pstmt3.close();}} catch (SQLException e) {}
			try {if (pstmtTables != null) {pstmtTables.close();}} catch (SQLException e) {}
			try {if (pstmtGeom != null) {pstmtGeom.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);
			ResultsDataSource.closeConnection("surveyKPI-Survey", connectionRel);
		}

		return response;
	}
	

	
	/*
	 * Prevent any more submissions of the survey
	 */
	@Path("/block")
	@POST
	@Consumes("application/json")
	public Response block(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@QueryParam("set") boolean set) { 
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
		
		PreparedStatement pstmt = null;
		try {
		
			/*
			 * Get Forms and row counts in this survey
			 */
			String sql = "update survey set blocked = ? where s_id = ?;";		
		
			log.info(sql + " : " + set + " : " + sId);
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setBoolean(1, set);
			pstmt.setInt(2, sId);
			int count = pstmt.executeUpdate();

			if(count == 0) {
				log.info("Error: Failed to update blocked status");
			} else {
				log.info("userevent: " + request.getRemoteUser() + (set ? " : block survey : " : " : unblock survey : ") + sId);
			}
			
			response = Response.ok().build();
			

			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);
		}

		return response;
	}
	
	/*
	 * Save the survey things at model
	 */
	@Path("/model")
	@POST
	@Consumes("application/json")
	public Response save_model(
			@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@FormParam("model") String model
			) { 
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
		
		PreparedStatement pstmt = null;
		if(model == null) {
			response = Response.serverError().entity("Empty model").build();
		} else {
			try {
			
				/*
				 * Get Forms and row counts in this survey
				 */
				String sql = "update survey set model = ? where s_id = ?;";		
			
				
				pstmt = connectionSD.prepareStatement(sql);	
				pstmt.setString(1, model);
				pstmt.setInt(2, sId);
				int count = pstmt.executeUpdate();
	
				if(count == 0) {
					response = Response.serverError().entity("Failed to update model").build();
				} else {
					response = Response.ok().build();
				}
				
	
				
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to update model", e);
			    response = Response.serverError().entity("Failed to update model").build();
			} finally {
				
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				
				SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);
				
			}
		}

		return response;
	}
	
	/*
	 * Remove media attachments
	 */
	@Path("/remove_media")
	@POST
	@Consumes("application/json")
	public Response remove_media(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@FormParam("qId") int qId,
			@FormParam("oId") int oId,
			@FormParam("text_id") String text_id) { 
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);	// Validate that the user can access this survey
		// End Authorisation
		
		PreparedStatement pstmt = null;
		try {
			String sql = null;
			if(text_id != null) {
				// Survey level media
				sql = "delete FROM translation t " +
		    			" where t.s_id = ? " +
		    			" and t.text_id = ? "; 
		    	pstmt = connectionSD.prepareStatement(sql);
		    	pstmt.setInt(1, sId);
		    	pstmt.setString(2, text_id);
			} else 	if(oId == -1) {
				// Question level media
				sql = "delete FROM translation t " +
		    			" where t.s_id = ? " +
		    			" and (t.type = 'image' or t.type = 'video' or t.type = 'audio') " +
		    			" and t.text_id in (select q.qtext_id from question q " + 
		    			" where q.q_id = ?); "; 
		    	pstmt = connectionSD.prepareStatement(sql);
		    	pstmt.setInt(1, sId);
		    	pstmt.setInt(2, qId);

			} else {
				// Option level media
				sql = "delete FROM translation t " +
		    			" where t.s_id = ? " +
		    			" and (t.type = 'image' or t.type = 'video' or t.type = 'audio') " +
		    			" and t.text_id in (select o.label_id from option o " + 
		    			" where o.o_id = ?); "; 
		    	pstmt = connectionSD.prepareStatement(sql);
		    	pstmt.setInt(1, sId);
		    	pstmt.setInt(2, oId);
			}


	    	int count = pstmt.executeUpdate();

			if(count == 0) {
				log.info("Error: Failed to remove any media");
			} else {
				log.info("Info: Media removed");
			}

			response = Response.ok().build();
			

			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"", e);
		    response = Response.serverError().entity("Error").build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);
			
		}

		return response;
	}

	/*
	 * Deletes a survey template
	 *  @param tables if set to yes, data tables will also be deleted
	 *  @param hard if set to true then data and tables will be physically deleted otherwise they will only
	 *    be marked as deleted in the meta data tables but will remain in the database
	 *  @param delData if set to yes then the results tables will be deleted even if they have data
	 */
	// JSON
	@DELETE
	public String deleteSurvey(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@QueryParam("tables") String tables,
			@QueryParam("hard") boolean hard,
			@QueryParam("undelete") boolean undelete,
			@QueryParam("delData") boolean delData) { 
		
		log.info("Deleting template:" + sId);
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Can't find PostgreSQL JDBC Driver", e);
		    return "Survey: Error: Can't find PostgreSQL JDBC Driver";
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Survey");
		a.isAuthorised(connectionSD, request.getRemoteUser());
		boolean surveyMustBeDeleted = undelete || hard;
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, surveyMustBeDeleted);  // Note if hard delete is set to true the survey should have already been soft deleted
		// End Authorisation
		
		if(sId != 0) {

			String sql = null;				
			Connection connectionRel = null; 
			PreparedStatement pstmt = null;
			PreparedStatement pstmtDelTem = null;
			PreparedStatement pstmtIdent = null;

			try {
				if(undelete) {
					/*
					 * Restore the survey
					 */
					sql = "update survey set deleted='false' where s_id = ?;";	
					log.info(sql + " : " + sId);
					pstmt = connectionSD.prepareStatement(sql);
					pstmt.setInt(1, sId);
					pstmt.executeUpdate();
					log.info("userevent: " + request.getRemoteUser() + " : un delete survey : " + sId);
				
				} else {
					
					// Get the survey ident and name
					String surveyIdent = null;
					String surveyName = null;
					String surveyDisplayName = null;
					int projectId = 0;
					sql = "SELECT s.name, s.ident, s.display_name, s.p_id " +
							"FROM survey s " + 
							"where s.s_id = ?;";
					
					pstmtIdent = connectionSD.prepareStatement(sql);
					pstmtIdent.setInt(1, sId);
					log.info("Get survey name and ident: " + pstmtIdent.toString());
					ResultSet resultSet = pstmtIdent.executeQuery();
					
					if (resultSet.next()) {		
						surveyName = resultSet.getString("name");
						surveyIdent = resultSet.getString("ident");
						surveyDisplayName = resultSet.getString("display_name");
						projectId = resultSet.getInt("p_id");
					}
					
					// Get the organisation id
					//int orgId = GeneralUtilityMethods.getOrganisationId(connectionSD,request.getRemoteUser());
					
					/*
					 * Delete the survey. Either a soft or a hard delete
					 */
					if(hard) {
						connectionRel = ResultsDataSource.getConnection("surveyKPI-Survey");
	
						// Get the tables associated with this survey
						boolean nonEmptyDataTables = false;
						if(tables != null && tables.equals("yes")) {
									
							sql = "SELECT DISTINCT f.table_name FROM form f " +
									"WHERE f.s_id = ? " +
									"ORDER BY f.table_name;";						
						
							log.info(sql);
							pstmt = connectionSD.prepareStatement(sql);	
							pstmt.setInt(1, sId);
							resultSet = pstmt.executeQuery();
							
							while (resultSet.next() && (delData || !nonEmptyDataTables)) {		
								
								String tableName = resultSet.getString(1);
								int rowCount = 0;
								
								// Ensure the table is empty
								if(!delData) {
									try {
										sql = "SELECT COUNT(*) FROM " + tableName + ";";
										log.info(sql + " : " + tableName);
										
										pstmt = connectionRel.prepareStatement(sql);
										ResultSet resultSetCount = pstmt.executeQuery();
										resultSetCount.next();							
										rowCount = resultSetCount.getInt(1);
									} catch (Exception e) {
										log.severe("failed to get count from table");
									}
								}
								
								try {
									if(delData || (rowCount == 0)) {
										sql = "DROP TABLE " + tableName + ";";
										log.info(sql + " : " + tableName);
										Statement stmtRel = connectionRel.createStatement();
										stmtRel.executeUpdate(sql);
										
									} else {
										nonEmptyDataTables = true;
									}
								} catch (Exception e) {
									log.severe("failed to drop table");
								}
							}
							
						} 
		
						String basePath = GeneralUtilityMethods.getBasePath(request);
						
						/*
						 * Delete any attachments
						 */
						String fileFolder = basePath + "/attachments/" + surveyIdent;
					    File folder = new File(fileFolder);
					    try {
					    	log.info("Deleting attachments folder: " + fileFolder);
							FileUtils.deleteDirectory(folder);
						} catch (IOException e) {
							log.info("Error deleting attachments directory:" + fileFolder + " : " + e.getMessage());
						}
					    
						/*
						 * Delete any raw upload data
						 */
						fileFolder = basePath + "/uploadedSurveys/" + surveyIdent;
					    folder = new File(fileFolder);
					    try {
					    	log.info("Deleting uploaded files for survey: " + surveyName + " in folder: " + fileFolder);
							FileUtils.deleteDirectory(folder);
						} catch (IOException e) {
							log.info("Error deleting uploaded instances: " + fileFolder + " : " + e.getMessage());
						}
					    

					    // Delete the templates
						try {
							GeneralUtilityMethods.deleteTemplateFiles(surveyDisplayName, basePath, projectId );
						} catch (Exception e) {
							log.info("Error deleting templates: " + surveyName + " : " + e.getMessage());
						}
						
						// Delete survey definition
						if(delData || !nonEmptyDataTables) {
							sql = "DELETE FROM survey WHERE s_id = ?;";	
							log.info(sql + " : " + sId);
							if(pstmt != null) try {pstmt.close();}catch(Exception e) {}
							pstmt = connectionSD.prepareStatement(sql);
							pstmt.setInt(1, sId);
							pstmt.execute();
						}
						
						// Delete changeset data, this is an audit trail of modifications to the data
						if(delData || !nonEmptyDataTables) {
							sql = "DELETE FROM changeset WHERE s_id = ?;";	
							log.info(sql + " : " + sId);
							if(pstmt != null) try {pstmt.close();}catch(Exception e) {}
							pstmt = connectionRel.prepareStatement(sql);
							pstmt.setInt(1, sId);
							pstmt.execute();
						}

						log.info("userevent: " + request.getRemoteUser() + " : hard delete survey : " + sId);
				
					} else {
						
						// Add date and time to the display name
						DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd HH:mm:ss");
						dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
						Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));		// Store all dates in UTC
						String newDisplayName = surveyDisplayName + " (" + dateFormat.format(cal.getTime()) + ")";
						
						// Update the "name"
						String newName = null;
						if(surveyName != null) {
							int idx = surveyName.lastIndexOf('/');
							newName = surveyName;
							if(idx > 0) {
								newName = surveyName.substring(0, idx + 1) + GeneralUtilityMethods.convertDisplayNameToFileName(newDisplayName) + ".xml";
							}
						}
						
						// Update the survey definition to indicate that the survey has been deleted
						// Add the current date and time to the name and display name to ensure the deleted survey has a unique name 
						sql = "update survey set " +
								" deleted='true', " +
								" name = ?, " +
								" display_name = ? " +
								"where s_id = ?;";	
					
						pstmt = connectionSD.prepareStatement(sql);
						pstmt.setString(1, newName);
						pstmt.setString(2, newDisplayName);
						pstmt.setInt(3, sId);
						log.info("Soft delete survey: " + pstmt.toString());
						pstmt.executeUpdate();
						
						log.info("userevent: " + request.getRemoteUser() + " : soft delete survey : " + sId);
						
						// Rename files
						String basePath = GeneralUtilityMethods.getBasePath(request);
						GeneralUtilityMethods.renameTemplateFiles(surveyDisplayName, newDisplayName, basePath, projectId, projectId);
					}
					
					/*
					 * Delete any panels that reference this survey
					 */
					sql = "delete from dashboard_settings where ds_s_id = ?;";	
					log.info(sql + " : " + sId);
					pstmt = connectionSD.prepareStatement(sql);
					pstmt.setInt(1, sId);
					pstmt.executeUpdate();
					
					/*
					 * Delete any tasks that are to update this survey
					 */
					sql = "delete from tasks where form_id = ?;";	
					log.info(sql + " : " + sId);
					pstmt = connectionSD.prepareStatement(sql);
					pstmt.setInt(1, sId);
					pstmt.executeUpdate();
					
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "SQL Error", e);
			    return "Error: Failed to delete";
			    
			} catch (Exception e) {
				log.log(Level.SEVERE, "Error", e);
			    return "Error: Failed to delete";
			    
			} finally {
				try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
				try {if (pstmtDelTem != null) {pstmtDelTem.close();}} catch (SQLException e) {}
				try {if (pstmtIdent != null) {pstmtIdent.close();}} catch (SQLException e) {}
				
				SDDataSource.closeConnection("surveyKPI-Survey", connectionSD);
				ResultsDataSource.closeConnection("surveyKPI-Survey", connectionRel);
			}
		}

		return null; 
	}
	
	private void addToSurveyBounds(float[] bbox, String bounds) {
		int idx = bounds.indexOf('(');
		if(idx > 0) {
			String b2 = bounds.substring(idx + 1, bounds.length() - 1);
			String [] coords = b2.split(",");
			if(coords.length > 1) {
				String [] c1 = coords[0].split(" ");
				String [] c2 = coords[1].split(" ");
			
				float [] newBounds = new float[4];
				newBounds[0] = Float.parseFloat(c1[0]);
				newBounds[1] = Float.parseFloat(c1[1]);
				newBounds[2] = Float.parseFloat(c2[0]);
				newBounds[3] = Float.parseFloat(c2[1]);
				
				if(newBounds[0] < bbox[0]) {
					bbox[0] = newBounds[0];
				}
				if(newBounds[1] < bbox[1]) {
					bbox[1] = newBounds[1];
				}
				if(newBounds[2] > bbox[2]) {
					bbox[2] = newBounds[2];
				}
				if(newBounds[3] > bbox[3]) {
					bbox[3] = newBounds[3];
				}
			}
		}
	}
}

