package surveyKPI;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;

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

/*
 * This service handles requests from data tables components:
 *    1) PDF export
 */
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.LogManager;
import org.smap.sdal.managers.PDFTableManager;
import org.smap.sdal.managers.ManagedFormsManager;
import org.smap.sdal.model.Assignment;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.ManagedFormConfig;
import org.smap.sdal.model.Organisation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import utilities.XLSReportsManager;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/tables")
public class TableReports extends Application {

	private static Logger log =
			 Logger.getLogger(TableReports.class.getName());
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	LogManager lm = new LogManager();		// Application log

	@POST
	@Path("/generate")
	public void setManaged(
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@FormParam("data") String data,
			@FormParam("sId") int sId,
			@FormParam("managedId") int managedId,
			@FormParam("format") String format
			) throws Exception { 
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection("surveyKPI-GetConfig");
		a.isAuthorised(sd, request.getRemoteUser());
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation
		
		boolean isXLS = format.toLowerCase().equals("xls") || format.toLowerCase().equals("xlsx");
		boolean isPdf = format.toLowerCase().equals("pdf");
		Connection cResults = ResultsDataSource.getConnection("surveyKPI-GetConfig");
		
		String tz = "GMT";
		try {
			
			// Localisation
			Organisation organisation = UtilityMethodsEmail.getOrganisationDefaults(sd, null, request.getRemoteUser());
			Locale locale = new Locale(organisation.locale);
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
			
			// Get columns
			ManagedFormsManager qm = new ManagedFormsManager();
			ManagedFormConfig mfc = qm.getColumns(sd, cResults, sId, managedId, request.getRemoteUser());
			
			// Convert data to an array
			Type type = new TypeToken<ArrayList<ArrayList<KeyValue>>>(){}.getType();		
			ArrayList<ArrayList<KeyValue>> dArray = new Gson().fromJson(data, type);
			
			if(isXLS) {
				XLSReportsManager xm = new XLSReportsManager(format);
				xm.createXLSReportsFile(response.getOutputStream(), dArray, mfc, localisation, tz);
			} else if(isPdf) {
				String basePath = GeneralUtilityMethods.getBasePath(request);
				PDFTableManager pm = new PDFTableManager();
				pm.createPdf(
						sd,
						response.getOutputStream(), 
						dArray, 
						mfc, 
						localisation, 
						tz, false,	 // TBD set landscape and paper size from client
						request.getRemoteUser(),
						basePath);
						
			}
			
		
		} catch (SQLException e) {
			log.log(Level.SEVERE, "SQL Error", e);
			throw new Exception("Exception: " + e.getMessage());			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			SDDataSource.closeConnection("surveyKPI-GetConfig", sd);
			ResultsDataSource.closeConnection("surveyKPI-GetConfig", cResults);
		}


	}
	

}

