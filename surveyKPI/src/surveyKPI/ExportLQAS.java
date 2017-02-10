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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.CustomReportsManager;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.PDFSurveyManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.LQAS;
import org.smap.sdal.model.LQASGroup;
import org.smap.sdal.model.LQASItem;
import org.smap.sdal.model.LQASdataItemOld;
import org.smap.sdal.model.LQASold;

import utilities.XLSFormManager;
import utilities.XLS_LQAS_Manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.parser.XMLParser;

import net.sourceforge.jeval.Evaluator;

/*
 * Creates an LQAS report in XLS
 */

@Path("/lqasExport/{sId}/{rId}")
public class ExportLQAS extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ExportLQAS.class.getName());

	LogManager lm = new LogManager();		// Application log
	
	/*
	 * Assume:
	 *  1) LQAS surveys only have one form and this form is the one that has the "lot" question in it
	 */

	
	@GET
	@Produces("application/x-download")
	public Response getXLSFormService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("sId") int sId,
			@PathParam("rId") int rId,
			@QueryParam("sources") boolean showSources,
			@QueryParam("filetype") String filetype) throws Exception {

		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		    throw new Exception("Can't find PostgreSQL JDBC Driver");
		}
				
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("createLQAS");	
		boolean superUser = false;
		try {
			superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		} catch (Exception e) {
		}
		a.isAuthorised(sd, request.getRemoteUser());		
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false, superUser);
		if(rId > 0) {
			a.isValidManagedForm(sd, request.getRemoteUser(), rId);
		}
		// End Authorisation 
		
		lm.writeLog(sd, sId, request.getRemoteUser(), "view", "Export to LQAS");
		
		Response responseVal = null;
		SurveyManager sm = new SurveyManager();
		org.smap.sdal.model.Survey survey = null;
		Connection cResults = ResultsDataSource.getConnection("createLQAS");
		
		String basePath = GeneralUtilityMethods.getBasePath(request);
		
		// Set file type to "xlsx" unless "xls" has been specified
		if(filetype == null || !filetype.equals("xls")) {
			filetype = "xlsx";
		}
		
		try {
			
			// Get the survey details
			survey = sm.getById(sd, cResults, request.getRemoteUser(), sId, false, basePath, null, false, false, 
					false, false, false, "real", superUser, 0, null);
			
			/*
			 * Get the LQAS definition to apply to this survey
			 */
			System.out.println("Report Id:" + rId);
			LQAS lqas = null;
			if(rId > 0) {
				CustomReportsManager crm = new CustomReportsManager();
				lqas = crm.getLQASReport(sd, rId);
			}		
			
			// Write out the definition ==== Temp
		   	Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			String json = gson.toJson(lqas);
			System.out.println("json: " + json);
			System.out.println("Filename: " + survey.displayName + "." + filetype);
			
			// Set file name
			GeneralUtilityMethods.setFilenameInResponse(survey.displayName + "." + filetype, response);
			
			// Create XLSForm
			XLS_LQAS_Manager xf = new XLS_LQAS_Manager(filetype);
			if(lqas != null) {
				xf.createLQASForm(sd, cResults, response.getOutputStream(), survey, lqas, showSources);
			} 
			
			responseVal = Response.ok("").build();
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			responseVal = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} finally {
			
			SDDataSource.closeConnection("createLQAS", sd);		
			ResultsDataSource.closeConnection("createLQAS", cResults);
			
		}
		return responseVal;
	}
	
}
