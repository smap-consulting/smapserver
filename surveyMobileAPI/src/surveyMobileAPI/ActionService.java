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

package surveyMobileAPI;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.codehaus.jettison.json.JSONArray;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ActionManager;
import org.smap.sdal.managers.SurveyViewManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.SurveySettingsManager;
import org.smap.sdal.managers.TableDataManager;
import org.smap.sdal.model.Action;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.SurveyViewDefn;
import org.smap.sdal.model.Survey;
import org.smap.sdal.model.SurveySettingsDefn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*
 * Allow a temporary user to complete an action
 * There is an action service in both surveyMobileAPI and in surveyKPI.  The reason being only surveyKPI currently
 *  has access to Apache POI for reports
 */

@Path("/action")
public class ActionService extends Application {

	Authorise a = null;

	private static Logger log = Logger.getLogger(ActionService.class.getName());

	public ActionService() {

		ArrayList<String> authorisations = new ArrayList<String>();
		authorisations.add(Authorise.ANALYST);
		authorisations.add(Authorise.MANAGE); // Enumerators with MANAGE access can process managed forms
		a = new Authorise(authorisations, null);
	}
	
	SurveyViewDefn mfc = null;

	/*
	 * Get instance data Respond with JSON
	 */
	@GET
	@Path("/{ident}")
	@Produces(MediaType.TEXT_HTML)
	public Response getInstanceJson(@Context HttpServletRequest request, @PathParam("ident") String userIdent)
			throws IOException {

		return getActionForm(request, userIdent);
	}

	@POST
	@Produces("text/html")
	@Consumes("application/json")
	@Path("/update/{ident}")
	public Response updateManagedRecordAnon(@Context HttpServletRequest request, @PathParam("ident") String userIdent,
			@FormParam("settings") String settings) throws Exception {

		Response response = null;
		String requester = "surveyMobileAPI-updateManagedRecordAnon";

		Connection sd = SDDataSource.getConnection(requester);
		Connection cResults = ResultsDataSource.getConnection(requester);

		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);

			String tz = "UTC";	// set default tz
			
			// 1. Get details on the action to be performed using the user credentials
			ActionManager am = new ActionManager(localisation, tz);
			Action a = am.getAction(sd, userIdent);

			// 2. If temporary user does not exist then throw exception
			if (a == null) {
				throw new Exception(localisation.getString("mf_adnf"));
			}
			
			response = am.processUpdate(request, sd, cResults, userIdent, a.sId, a.managedId, settings);

		} finally {
			SDDataSource.closeConnection(requester, sd);
			ResultsDataSource.closeConnection(requester, cResults);
		}

		return response;
	}

	@POST
	@Produces("text/html")
	@Consumes("application/json")
	@Path("/updatestatus/{uIdent}/{status}")
	public Response updateStatus(@Context HttpServletRequest request, @PathParam("uIdent") String uIdent,
			@PathParam("status") String status) {

		Response response = null;

		String sql = "delete from users where temporary and ident = ?";
		PreparedStatement pstmt = null;

		Connection sd = SDDataSource.getConnection("surveyKPI-managedForms");

		try {

			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, uIdent);
			log.info("Delete temporary user - action complete" + pstmt.toString());
			pstmt.executeUpdate();

			response = Response.ok().build();

		} catch (Exception e) {
			response = Response.serverError().entity(e.getMessage()).build();
			log.log(Level.SEVERE, "Error", e);
		} finally {

			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (Exception e) {
			}

			SDDataSource.closeConnection("surveyKPI-managedForms", sd);

		}

		return response;
	}

	/*
	 * Get the response as either HTML or JSON
	 */
	private Response getActionForm(HttpServletRequest request, String userIdent) {

		Response response = null;
		StringBuffer outputString = new StringBuffer();
		String requester = "surveyMobileAPI-getActionForm";

		Connection sd = SDDataSource.getConnection(requester);
		Connection cResults = ResultsDataSource.getConnection(requester);

		try {
			Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(sd, request, request.getRemoteUser()));
			ResourceBundle localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);

			String tz = "UTC";	// set default tz
			
			// 1. Get details on the action to be performed using the user credentials
			ActionManager am = new ActionManager(localisation, tz);
			Action a = am.getAction(sd, userIdent);

			// 2. If temporary user does not exist then throw exception
			if (a == null) {
				throw new Exception(localisation.getString("mf_adnf"));
			}

			// 3. Generate the form
			outputString.append(addActionDocument(sd, cResults, request, localisation, a, userIdent, false, tz)); 

			response = Response.status(Status.OK).entity(outputString.toString()).build();
		} catch (Exception e) {
			log.log(Level.SEVERE, e.getMessage(), e);
			response = Response.status(Status.OK).entity(e.getMessage()).build();
		} finally {
			SDDataSource.closeConnection(requester, sd);
		}

		return response;
	}

	/*
	 * Add the HTML
	 */
	private StringBuffer addActionDocument(Connection sd, Connection cResults, HttpServletRequest request,
			ResourceBundle localisation, Action a, String uIdent, boolean superUser, String tz) throws SQLException, Exception {

		StringBuffer output = new StringBuffer();

		SurveyManager sm = new SurveyManager(localisation, "UTC");
		Survey s = sm.getById(sd, cResults, uIdent, a.sId, false, null, null, false, false, false, false, false, null,
				false, false, false, null,
				false,	// child surveys
				false	// launched only
				);
		if (s == null) {
			throw new Exception(localisation.getString("mf_snf"));
		}

		output.append("<!DOCTYPE html>\n");

		output.append(addHead(sd, cResults, localisation, request, a, uIdent, superUser, tz));
		output.append(addBody(request, localisation, s));

		output.append("</html>\n");
		return output;
	}

	/*
	 * Add the head section
	 */
	private StringBuffer addHead(Connection sd, Connection cResults, ResourceBundle localisation, HttpServletRequest request, Action a,
			String uIdent, boolean superUser, String tz) throws SQLException, Exception {

		StringBuffer output = new StringBuffer();

		// head
		output.append("<head>\n");
		output.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
		output.append("<meta charset=\"utf-8\">\n");

		output.append("<title>Action Completion</title>\n");

		output.append("<link href=\"/css/bootstrap.min.css\" rel=\"stylesheet\">\n");
		output.append("<link href=\"/font-awesome/css/font-awesome.css\" rel=\"stylesheet\">\n");
		output.append("<link href=\"/css/bootstrap-datetimepicker.min.css\" rel=\"stylesheet\">\n");
		output.append("<link href=\"/css/wb/plugins/iCheck/custom.css\" rel=\"stylesheet\">\n");

		output.append("<link href=\"/css/wb/animate.css\" rel=\"stylesheet\">\n");
		output.append("<link href=\"/css/wb/style.css\" rel=\"stylesheet\">\n");
		output.append("<link href=\"/css/smap-wb.css\" rel=\"stylesheet\">\n");
		output.append("<link href=\"/css/wb/plugins/sweetalert/sweetalert.css\" rel=\"stylesheet\">");

		output.append("<script> if (!window.console) console = {log: function() {}}; </script>\n");

		output.append("<script src=\"/js/libs/modernizr.js\"></script>");
		output.append("<script src=\"/js/app/custom.js\"></script>\n");

		output.append("<script data-main=\"/tasks/js/action_forms\" src=\"/js/libs/require.js\"></script>\n");

		output.append("<script>");

		int uId = GeneralUtilityMethods.getUserId(sd, uIdent);
		SurveyViewManager mfm = new SurveyViewManager(localisation, tz);
		
		mfc = mfm.getSurveyView(sd, cResults, uId, null, a.sId, a.managedId, uIdent,
				GeneralUtilityMethods.getOrganisationIdForSurvey(sd, a.sId), superUser,
				null);	// TODO Add GroupSurvey
		String urlprefix = GeneralUtilityMethods.getUrlPrefix(request);
		Form f = GeneralUtilityMethods.getTopLevelForm(sd, a.sId);

		output.append(getManagedConfig(mfc));
		output.append(getRecord(sd, cResults, localisation, mfc, a.sId, a.prikey, urlprefix, request.getRemoteUser(), f.tableName,
				superUser));

		output.append("var gSurvey="); // Survey id
		output.append(a.sId);
		output.append(";\n");

		output.append("var gManage="); // Manage id
		output.append(a.managedId);
		output.append(";\n");

		output.append("var gIdent='"); // User Ident
		output.append(uIdent);
		output.append("';\n");

		output.append("</script>");
		output.append("</head>\n");

		return output;
	}

	/*
	 * Add the body
	 */
	private StringBuffer addBody(HttpServletRequest request, ResourceBundle localisation, Survey s) {
		StringBuffer output = new StringBuffer();

		output.append("<body>\n");
		output.append("<div id=\"wrapper\">");
		output.append(addNoScriptWarning(localisation));
		output.append(addMain(localisation, s));

		output.append("</div>");
		output.append("</body>\n");
		return output;
	}

	/*
	 * Get the "Main" element
	 */
	private StringBuffer addMain(ResourceBundle localisation, Survey s) {
		StringBuffer output = new StringBuffer();

		output.append("<div id=\"page-wrapper\" class=\"gray-bg\" style=\"margin: 0 50px 0 50px;\">");
		//output.append(getNavBar());
		output.append(addHeaderRow(s));
		output.append(addActionRow());
		output.append("</div>");
		return output;
	}

	/*
	 * Add a header row
	 */
	StringBuffer addHeaderRow(Survey s) {
		StringBuffer output = new StringBuffer();

		output.append("<div class=\"row wrapper border-bottom white-bg page-heading\">");
		output.append("<div class=\"col-sm-2\">");
		output.append("<h2 class=\"lang\" data-lang=\"m_mf\">manage</h2>");
		output.append("</div>");
		output.append("<div class=\"col-sm-4\">");
		output.append("<h2");
		output.append(s.displayName);
		output.append("</h2>");
		output.append("</div>");
		output.append("</div>");

		return output;
	}

	/*
	 * Get the nav bar
	 */
	StringBuffer getNavBar() {

		StringBuffer output = new StringBuffer();

		output.append("<div class=\"row border-bottom\">");
		output.append("<nav class=\"navbar navbar-static-top\" role=\"navigation\" style=\"margin-bottom: 0\">");
		output.append("<div class=\"navbar-header\">");
		output.append(
				"<a class=\"navbar-minimalize minimalize-styl-2 btn btn-primary\" href=\"#\"><i class=\"fa fa-bars\"></i></a>");
		output.append("</div>");
		output.append("<ul class=\"nav navbar-top-links navbar-right\">");
		output.append("<li>");
		output.append("<a href=\"#\" id=\"m_refresh\"><span class=\"lang\" data-lang=\"m_refresh\">refresh</span></a>");
		output.append("</li>");
		output.append("<li>");
		output.append("<a class=\"right-sidebar-toggle\">");
		output.append("<i class=\"fa fa-cog\"></i>");
		output.append("</a>");
		output.append("</li>");
		output.append("</ul>");
		output.append("</nav>");
		output.append("</div>");

		return output;
	}

	private String addNoScriptWarning(ResourceBundle localisation) {
		StringBuffer output = new StringBuffer();
		output.append("<noscript>");
		output.append("<div>");
		output.append("<span style=\"color:red\">");
		output.append(localisation.getString("wf_njs"));
		output.append("</span>");
		output.append("</div>");
		output.append("</noscript>");

		return output.toString();
	}

	private StringBuffer getManagedConfig(SurveyViewDefn mfc) throws SQLException, Exception {

		StringBuffer output = new StringBuffer();

		Gson gson = new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();

		output.append("var gSurveyConfig=");
		output.append(gson.toJson(mfc));
		output.append(";\n");
		return output;
	}

	private StringBuffer getRecord(Connection sd, Connection cResults, ResourceBundle localisation, SurveyViewDefn mfc, int sId, int prikey,
			String urlprefix, String uIdent, String tableName, boolean superUser) throws SQLException, Exception {

		StringBuffer output = new StringBuffer();

		String tz = "UTC";	// Set default timezone
		
		TableDataManager tdm = new TableDataManager(localisation, tz);
		JSONArray ja = null;

		PreparedStatement pstmt = null;
		try {
			pstmt = tdm.getPreparedStatement(sd, cResults, mfc.columns, urlprefix, sId, tableName, 
					0, 					// parkey
					null, 				// HRK
					uIdent, 
					null,				// roles
					null, 				// Sort
					null, 				// Sort direction
					true, 				// Management
					false, 				// group
					false, 				// isDt
					prikey, 
					false, 				// get parkey
					0, 					// start parkey
					superUser, 
					true, 	// Return the specific primary key
					"none", 				// include bad
					null	,				// no custom filter
					null,				// no key filter
					tz,
					null,
					null	,			// advanced filter
					null,			// Date filter name
					null,			// Start date
					null				// End date
			);

			if (pstmt != null) {
				ja = tdm.getData(pstmt, mfc.columns, urlprefix, false, false, 1);
			}
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}

		output.append("\nvar gRecord=");
		output.append(ja.toString());
		output.append(";\n");
		return output;
	}

	private StringBuffer addActionRow() {
		StringBuffer output = new StringBuffer();

		output.append("<div class=\"row\">");
		output.append("<div class=\"ibox float-e-margins\">");
		output.append("<div class=\"ibox-content\">");
		
		// controls section
		String fd = "on";
		if(mfc.parameters != null) {
			fd = mfc.parameters.get("form_data");
		}
		if(fd.equals("on")) {
			output.append("<div class=\"row border-bottom\">");
			output.append("<div class=\"col-md-12 manageFormData\">");
			output.append("<label>");
			output.append("</label>");
			output.append("<input type=\"checkbox\" name=\"formdata\" value=\"yes\" id=\"er_form_data\" checked=\"checked\" />");
			output.append("<span class=\"lang\" data-lang=\"mf_fd\">form data</span>");
			output.append("</div>");
			output.append("</div>");
		}
		
		// Main section
		output.append("<div class=\"row\">");
		if(fd.equals("on")) {
			output.append("<div class=\"col-sm-6 b-r showFormData\"><h3 class=\"m-t-none m-b lang\" data-lang=\"mf_fd\">form data</h3>");
			output.append("<form id=\"surveyForm\" role=\"form\" class=\"form-horizontal\"></form>");
			output.append("</div>");
			output.append("<div class=\"col-sm-6 b-r showMgmtData\"><h3 class=\"m-t-none m-b lang\" data-lang=\"mf_md\">mgmt data</h3>");
		} else {
			output.append("<div class=\"col-sm-12 b-r\"><h3 class=\"m-t-none m-b lang\" data-lang=\"mf_md\">mgmt data</h3>");
		}
		output.append("<form id=\"editRecordForm\" role=\"form\" class=\"form-horizontal\"></form>");
		output.append(
				"<button type=\"button\" class=\"btn btn-default lang\" data-dismiss=\"modal\" data-lang=\"c_close\">close</button>");
		output.append(
				"<button id=\"saveRecord\" type=\"button\" class=\"btn btn-primary lang\" data-dismiss=\"modal\" data-lang=\"c_save\">Save</button>");
		output.append("</div>");
		output.append("</div>");		// Main Section
		
		output.append("</div>");		// ibox content
		output.append("</div>");		// ibox
		output.append("</div>");		// row

		return output;
	}

}
