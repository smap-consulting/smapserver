/*****************************************************************************

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

 ******************************************************************************/

package surveyKPI;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ActionManager;
import org.smap.sdal.managers.PDFReportsManager;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.KeyValueSimp;
import org.smap.sdal.model.SurveyViewDefn;
import org.smap.sdal.model.Transform;
import utilities.XLSXReportsManager;

/*
 * Allow a temporary user to complete an action
 * There is an action service in both surveyMobileAPI and in surveyKPI.  The reason being only surveyKPI currently
 *  has access to Apache POI for reports
 *  -- TODO Remove this one
 */

@Path("/action")
public class ActionServiceKPI extends Application {

	Authorise auth = null;

	private static Logger log = Logger.getLogger(ActionServiceKPI.class.getName());

	public ActionServiceKPI() {

		ArrayList<String> authorisations = new ArrayList<String>();
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.MANAGE); // Enumerators with MANAGE access can process managed forms
		auth = new Authorise(authorisations, null);
	}
	
	SurveyViewDefn mfc = null;

	/*
	 * Get data identified in an action
	 */
	@GET
	@Path("/{ident}")
	public Response getAnonymousReport(
			@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("ident") String userIdent)
			throws IOException {

		Response responseVal = null;
		StringBuffer outputString = new StringBuffer();
		String requester = "surveyKPI-getAnonymousReport";

		Connection sd = SDDataSource.getConnection(requester);
		Connection cResults = ResultsDataSource.getConnection(requester);

		try {
			
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);

			// 1. Get details on the action to be performed using the user credentials
			ActionManager am = new ActionManager(localisation, "UTC");		// Time zone should be ignored, real time zone will be retrieved from the action
			Action a = am.getAction(sd, userIdent);

			// 2. If temporary user does not exist then throw exception
			if (a == null) {
				throw new Exception(localisation.getString("mf_adnf"));
			}
			
			// Authorisation - Access Don't validate user rights as this is for an anonymous report
			auth.isValidSurvey(sd, userIdent, a.sId, false, false);
			// End Authorisation

			// 3. Get parameters
			int fId = 0;
			boolean split_locn = false;
			boolean merge_select_multiple = false;
			String language = "none";
			boolean exp_ro = false;
			boolean embedImages = false;
			boolean landscape = false;
			boolean excludeParents = false;
			boolean hxl = false;	
			Date startDate = null;
			Date endDate = null;	
			int dateId = 0;
			String filter = null;
			boolean meta = false;
			String tz = "UTC";
			
			for(KeyValueSimp p : a.parameters) {
				if(p.k.equals("form")) {
					fId = Integer.parseInt(p.v);
				} else if(p.k.equals("split_locn")) {
					split_locn = Boolean.parseBoolean(p.v);
				} else if(p.k.equals("merge_select_multiple")) {
					merge_select_multiple = Boolean.parseBoolean(p.v);
				} else if(p.k.equals("language")) {
					language = p.v;
				} else if(p.k.equals("tz")) {
					tz = p.v;
				} else if(p.k.equals("exp_ro")) {
					exp_ro = Boolean.parseBoolean(p.v);
				} else if(p.k.equals("embed_images")) {
					embedImages = Boolean.parseBoolean(p.v);
				} else if(p.k.equals("excludeParents")) {
					excludeParents = Boolean.parseBoolean(p.v);
				} else if(p.k.equals("hxl")) {
					hxl = Boolean.parseBoolean(p.v);
				} else if(p.k.equals("startDate")) {
					startDate = Date.valueOf(p.v);
				} else if(p.k.equals("endDate")) {
					endDate = Date.valueOf(p.v);
				} else if(p.k.equals("dateId")) {
					dateId = Integer.parseInt(p.v);
				} else if(p.k.equals("filter")) {
					filter = p.v;
				} else if(p.k.equals("meta")) {
					meta = Boolean.parseBoolean(p.v);
				} else if(p.k.equals("landscape")) {
					landscape = Boolean.parseBoolean(p.v);
				} 
			}
			
			// Default to the top level form
			if(fId == 0) {
				Form f = GeneralUtilityMethods.getTopLevelForm(sd, a.sId);
				fId = f.id;
			}
			
			// 4. Get report		
			if(a.reportType == null || a.reportType.equals("xlsx")) {
				XLSXReportsManager rm = new XLSXReportsManager(localisation);
				responseVal = rm.getNewReport(
						sd,
						cResults,
						userIdent,
						request,
						response,
						a.sId,
						a.name,		// File name
						split_locn,
						merge_select_multiple,
						language,
						exp_ro,
						embedImages,
						excludeParents,
						hxl,
						fId,
						startDate,
						endDate,
						dateId,
						filter,
						a.transform,
						meta,
						tz);
			} else if(a.reportType.equals("pdf")) {
				PDFReportsManager prm = new PDFReportsManager(localisation);
				prm.getReport(sd, 
						cResults, 
						userIdent, 
						request, 
						response, 
						a.sId, 
						a.filename, 
						landscape, 
						language, 
						startDate, 
						endDate, 
						dateId, 
						filter);
			} else {
				throw new Exception(localisation.getString("Unknow report type: " + a.reportType));
			}

			responseVal = Response.status(Status.OK).entity(outputString.toString()).build();
		} catch (AuthorisationException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			responseVal = Response.status(Status.OK).entity(e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection(requester, sd);
			ResultsDataSource.closeConnection(requester, cResults);
		}

		return responseVal;
	}


}
