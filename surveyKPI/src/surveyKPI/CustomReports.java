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
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.CustomReportsManager;
import org.smap.sdal.model.CustomReportItem;
import org.smap.sdal.model.ReportConfig;
import org.smap.sdal.model.TableColumn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import utilities.XLSCustomReportsManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/custom_reports")
public class CustomReports extends Application {
	
	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Review.class.getName());
	
	public CustomReports() {
		
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.ADMIN);
		a = new Authorise(authorisations, null);		
	}
	
	/*
	 * Return the custom reports
	 */
	@GET
	@Produces("application/json")
	public Response getCustomREports(@Context HttpServletRequest request,
			@QueryParam("negateType") boolean negateType,
			@QueryParam("type") String type) { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-CustomReports");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		Response response = null;
		Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		try {
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			CustomReportsManager crm = new CustomReportsManager();
			ArrayList<CustomReportItem> reports = crm.getList(sd, oId, type, negateType);
			response = Response.ok(gson.toJson(reports)).build();
		
				
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-CustomReports", sd);
		}


		return response;
	}


	/*
	 * Delete a custom report
	 */
	@Path("/{id}")
	@DELETE
	public Response deleteCustomReport(@Context HttpServletRequest request,
			@PathParam("id") int id) { 
		
		Response response = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
		    response = Response.serverError().entity("Survey: Error: Can't find PostgreSQL JDBC Driver").build();
		    return response;
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-CustomReports");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		try {
			
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			CustomReportsManager crm = new CustomReportsManager();
			crm.delete(sd, oId, id, localisation);
			
			response = Response.ok().build();
			
		} catch (SQLException e) {
			log.log(Level.SEVERE,"SQL Exception", e);
		    response = Response.serverError().entity("SQL Error").build();
		} catch (AuthorisationException e) {
			log.info("Authorisation Exception");
		    response = Response.serverError().entity("Not authorised").build();
		} catch (ApplicationException e) {
			log.info("Application exception: " + e.getMessage());
		    response = Response.serverError().entity(e.getMessage()).build();
		} catch (Exception e) {
			log.log(Level.SEVERE,"SQL Exception", e);
		    response = Response.serverError().entity(e.getMessage()).build();
		} finally {
			
			SDDataSource.closeConnection("surveyKPI-CustomReports", sd);
			
		}

		return response;

	}
	
	/*
	 * Export an oversight form to XLS
	 */
	@GET
	@Path("/xls/{id}")
	public Response exportOversightForm(@Context HttpServletRequest request,
			@PathParam("id") int id,
			@QueryParam("filetype") String filetype,
			@QueryParam("filename") String filename,
			
			@Context HttpServletResponse response) { 
		
		Response responseVal = null;
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE,"Survey: Error: Can't find PostgreSQL JDBC Driver", e);
			 try {
			    	response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
			    		"Survey: Error: Can't find PostgreSQL JDBC Driver");
			  } catch (Exception ex) {
			    	log.log(Level.SEVERE, "Exception", ex);
			  }
		}
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-ExportOversight");
		a.isAuthorised(sd, request.getRemoteUser());
		// End Authorisation
		
		// Set file type to "xlsx" unless "xls" has been specified
		if(filetype == null || !filetype.equals("xls")) {
			filetype = "xlsx";
		}
		
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-ExportOversight");
		
		try {
			
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser(), 0);
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			CustomReportsManager crm = new CustomReportsManager ();
			ReportConfig config = crm.get(sd, id, oId);
			
			GeneralUtilityMethods.setFilenameInResponse(filename + "." + filetype, response);
			response.setHeader("Content-type",  "application/vnd.ms-excel; charset=UTF-8");
			
			// Create XLS oversight form
			XLSCustomReportsManager xcrm = new XLSCustomReportsManager();
			xcrm.writeOversightDefinition(sd,
					cResults,
					oId,
					filetype,
					response.getOutputStream(), 
					config, 
					localisation);
			
			responseVal = Response.ok().build();
			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response.setHeader("Content-type",  "text/html; charset=UTF-8");
			// Return an OK status so the message gets added to the web page
			// Prepend the message with "Error: ", this will be removed by the client
			responseVal = Response.status(Status.OK).entity("Error: " + e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection("surveyKPI-ExportOversight", sd);
			ResultsDataSource.closeConnection("surveyKPI-ExportOversight", cResults);
		}

		return responseVal;

	}

}

