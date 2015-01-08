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
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import model.Settings;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeResponse;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.ServerSideCalculate;
import org.smap.sdal.model.Survey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/surveys")
public class Surveys extends Application {

	Authorise a = null;
	Authorise aDel = null;
	
	private static Logger log =
			 Logger.getLogger(Surveys.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Surveys.class);
		return s;
	}
	
	public Surveys() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		authorisations.add(Authorise.ENUM);
		a = new Authorise(authorisations, null);
		aDel = new Authorise(authorisations, null);
		
	}

	// JSON
	@GET
	@Produces("application/json")
	public Response getSurveys(@Context HttpServletRequest request,
			@QueryParam("deleted") boolean getDeleted,
			@QueryParam("blocked")  boolean getBlocked,
			@QueryParam("projectId") int projectId
			) { 
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return Response.serverError().build();
		}
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Surveys");
		if(getDeleted) {
			aDel.isAuthorised(connectionSD, request.getRemoteUser());
		} else {
			a.isAuthorised(connectionSD, request.getRemoteUser());
		}
		a.isValidProject(connectionSD, request.getRemoteUser(), projectId);
		// End Authorisation
		
		ArrayList<org.smap.sdal.model.Survey> surveys = null;
		
		Response response = null;
		PreparedStatement pstmt = null;
		SurveyManager sm = new SurveyManager();
		try {
			surveys = sm.getSurveys(connectionSD, pstmt,
					request.getRemoteUser(), getDeleted, getBlocked, projectId);
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(surveys);
			response = Response.ok(resp).build();
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			response = Response.serverError().build();
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			
			}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}

		return response;
	}
	
	// JSON
	@GET
	@Path("/{sId}")
	@Produces("application/json")
	public Response getSurveyDetails(@Context HttpServletRequest request,
			@PathParam("sId") int sId
			) { 
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return Response.serverError().build();
		}
		
		System.out.println("Get survey:" + sId);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Surveys");		
		a.isAuthorised(connectionSD, request.getRemoteUser());
		
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		org.smap.sdal.model.Survey survey = null;
		
		Response response = null;
		PreparedStatement pstmt = null;
		SurveyManager sm = new SurveyManager();
		try {
			survey = sm.getById(connectionSD, pstmt, request.getRemoteUser(), sId, true);
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			String resp = gson.toJson(survey);
			response = Response.ok(resp).build();
			
			
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			response = Response.serverError().build();
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}

		return response;
	}

	/*
	 * Apply updates to the survey
	 */
	@PUT
	@Path("/save/{sId}")
	@Produces("application/json")
	public Response saveSurveyDetails(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@FormParam("changes") String changesString
			) { 
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    return Response.serverError().build();
		}
		
		System.out.println("Save survey:" + sId);
		System.out.println("Changes: " + changesString);
		
		// Authorisation - Access
		Connection connectionSD = SDDataSource.getConnection("surveyKPI-Surveys");		
		a.isAuthorised(connectionSD, request.getRemoteUser());	
		a.isValidSurvey(connectionSD, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		Type type = new TypeToken<ArrayList<ChangeSet>>(){}.getType();
		Gson gson =  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		ArrayList<ChangeSet> changes = gson.fromJson(changesString, type);	
		Response response = null;

		try {
	
			SurveyManager sm = new SurveyManager();
			ChangeResponse resp = sm.applyChangeSetArray(connectionSD, sId, request.getRemoteUser(), changes);
					
			String respString = gson.toJson(resp);	// Create the response	
			response = Response.ok(respString).build();
			
			
		}  catch (Exception e) {
			try {connectionSD.rollback();} catch (Exception ex) {};
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			
			

			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}

		return response;
	}

	/*
	 * Update the survey settings (does not change question / forms etc)
	 */
	@Path("/save_settings/{sId}")
	@POST
	@Consumes("application/json")
	public Response rename(@Context HttpServletRequest request,
			@PathParam("sId") int sId,
			@FormParam("settings") String settings) { 
		
		Response response = null;
		
		System.out.println("Update settings: " + settings);
		
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
		
		Type type = new TypeToken<org.smap.sdal.model.Survey>(){}.getType();
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		org.smap.sdal.model.Survey survey = gson.fromJson(settings, type);
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtSSC = null;
		PreparedStatement pstmtDelSSC = null;
		PreparedStatement pstmtM1 = null;
		PreparedStatement pstmtM2 = null;
		PreparedStatement pstmtM3 = null;
		PreparedStatement pstmtM4 = null;
		PreparedStatement pstmtM5 = null;
		//PreparedStatement pstmtM6 = null;
		try {
			connectionSD.setAutoCommit(false);
		
			String sql = "update survey set display_name = ?, def_lang = ?, p_id = ? where s_id = ?;";		
		
			System.out.println("Saving survey: " + sql + " : " + survey.displayName);
			pstmt = connectionSD.prepareStatement(sql);	
			pstmt.setString(1, survey.displayName);
			pstmt.setString(2, survey.def_lang);
			pstmt.setInt(3, survey.p_id);
			pstmt.setInt(4, sId);
			int count = pstmt.executeUpdate();

			if(count == 0) {
				log.info("Error: Failed to update survey");
			} else {
				log.info("Info: Survey updated");
			}
			
			// Delete the old server side calculates
			sql = "delete from ssc where s_id = ?;";
			pstmtDelSSC = connectionSD.prepareStatement(sql);
			pstmtDelSSC.setInt(1, sId);
			pstmtDelSSC.executeUpdate();
			
			// Save the server side calculations
			sql = "insert into ssc (s_id, f_id, name, function, units) values " +
					" (?, ?, ?, ?, ?); ";
			pstmtSSC = connectionSD.prepareStatement(sql);
			for(int i = 0; i < survey.sscList.size(); i++) {
				
				ServerSideCalculate ssc = survey.sscList.get(i);
				pstmtSSC.setInt(1, sId);
				pstmtSSC.setInt(2, ssc.getFormId());
				pstmtSSC.setString(3, ssc.getName());
				pstmtSSC.setString(4, ssc.getFunction());
				pstmtSSC.setString(5, ssc.getUnits());
				pstmtSSC.executeUpdate();
				
				System.out.println("Inserting: " + ssc.getName());
			}
			
			/*
			 * Save the manifest entries
			 * Deprecated - now moved to media management page
			 */
			System.out.println("Saving manifest entries: " + survey.surveyManifest.size());
		    
		    // 1) Get the languages
		    String sqlM1 = "select distinct language from translation where s_id = ?;";
		    pstmtM1 = connectionSD.prepareStatement(sqlM1);
	    	String sqlM2 = "SELECT qtext_id FROM question WHERE q_id = ?;"; 
	    	pstmtM2 = connectionSD.prepareStatement(sqlM2);
	    	String sqlM3 = "SELECT label_id FROM option WHERE o_id = ?;"; 
	    	pstmtM3 = connectionSD.prepareStatement(sqlM3);
	    	String sqlM4 = "delete FROM translation " +
	    			" where s_id = ? " +
	    			" and text_id = ? " + 
	    			" and type = ? ";
	    	pstmtM4 = connectionSD.prepareStatement(sqlM4);
	    	String sqlM5 = "insert into translation (t_id, s_id, text_id, type, value,language) " +
	    			"values (nextval('t_seq'),?,?,?,?,?);"; 
	    	pstmtM5 = connectionSD.prepareStatement(sqlM5);
	    	
	    	/*
	    	 * No longer required
	    	String sqlGetIdent = "select ident from survey where s_id = ?;"; 
	    	    	 
	    	pstmtM6 = connectionSD.prepareStatement(sqlGetIdent);
		    SurveyManager sm = new SurveyManager();
		    for(int i = 0; i < survey.surveyManifest.size(); i++) {
		    	System.out.println(survey.surveyManifest.get(i).filename);
		    	sm.saveSurveyManifest(connectionSD, pstmtM1, pstmtM2, pstmtM3, pstmtM4, pstmtM5,
		    			pstmtM6, sId, survey.surveyManifest.get(i));
		    }
		    */

			
			
			connectionSD.commit();
			response = Response.ok().build();
			
		} catch (SQLException e) {
			try{connectionSD.rollback();}catch(Exception ex){};
			log.log(Level.SEVERE,"No data available", e);
		    response = Response.serverError().entity("No data available").build();
		} finally {
			
			if (pstmt != null) try {pstmt.close();} catch (SQLException e) {}
			if (pstmtDelSSC != null) try {pstmtDelSSC.close();} catch (SQLException e) {}
			if (pstmtSSC != null) try {pstmtSSC.close();} catch (SQLException e) {}
			if (pstmtM1 != null) try {pstmtM1.close();} catch (SQLException e) {}
			if (pstmtM2 != null) try {pstmtM2.close();} catch (SQLException e) {}
			if (pstmtM3 != null) try {pstmtM3.close();} catch (SQLException e) {}
			if (pstmtM4 != null) try {pstmtM4.close();} catch (SQLException e) {}
			if (pstmtM5 != null) try {pstmtM5.close();} catch (SQLException e) {}
			//if (pstmtM6 != null) try {pstmtM6.close();} catch (SQLException e) {}
			
			try {
				if (connectionSD != null) {
					connectionSD.close();
					connectionSD = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE,"Failed to close connection", e);
			    response = Response.serverError().entity("Survey: Failed to close connection").build();
			}
			
		}

		return response;
	}
}

