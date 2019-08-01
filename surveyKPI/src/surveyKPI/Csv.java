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
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
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
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.Utilities.UtilityMethodsEmail;
import org.smap.sdal.managers.BillingManager;
import org.smap.sdal.managers.CsvTableManager;
import org.smap.sdal.model.BillLineItem;
import org.smap.sdal.model.BillingDetail;
import org.smap.sdal.model.CsvTable;
import org.smap.sdal.model.Enterprise;
import org.smap.sdal.model.Organisation;
import org.smap.sdal.model.RateDetail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import utilities.XLSBillingManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Requests for csv data
 */
@Path("/csv")
public class Csv extends Application {

	Authorise a = null;
	
	private static Logger log =
			 Logger.getLogger(Tasks.class.getName());
	
	public Csv() {
		
		// Server Owner
		ArrayList<String> authorisations = new ArrayList<String> ();	
		authorisations.add(Authorise.ADMIN);	
		authorisations.add(Authorise.ANALYST);	
		a = new Authorise(authorisations, null);
	}
	
	/*
	 * Get a list of the available CSV files
	 */
	@GET
	@Path("/files")
	@Produces("application/json")
	public String getServer(@Context HttpServletRequest request) throws Exception { 
	
		String connectionString = "surveyKPI-Csv - Files";
		
		// Authorisation - Access
		Connection sd = SDDataSource.getConnection(connectionString);
		a.isAuthorised(sd, request.getRemoteUser());	
		// End Authorisation
		
		Gson gson =  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
		
		ArrayList<CsvTable> tables = null;
		try {
			// Get the users locale
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
		
			int oId = GeneralUtilityMethods.getOrganisationId(sd, request.getRemoteUser());
			CsvTableManager tm = new CsvTableManager(sd, localisation);
			tables = tm.getTables(oId, 0);
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception(e.getMessage());
		} finally {
			
			SDDataSource.closeConnection(connectionString, sd);
		}

		if(tables == null) {
			tables = new ArrayList<CsvTable> ();
		}
		return gson.toJson(tables);
	}
	
	
}

