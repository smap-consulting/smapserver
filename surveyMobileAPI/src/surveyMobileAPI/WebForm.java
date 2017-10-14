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
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.JsonAuthorisationException;
import org.smap.sdal.Utilities.NotFoundException;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.ServerManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.TranslationManager;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.ServerData;
import org.smap.sdal.model.Survey;
import org.smap.server.utilities.GetHtml;
import org.smap.server.utilities.GetXForm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/*
 * Return a survey as a webform
 */

@Path("/webForm")
public class WebForm extends Application {

	Authorise a = new Authorise(null, Authorise.ENUM);

	private static Logger log = Logger.getLogger(WebForm.class.getName());

	class SurveyData {
		String modelStr;
		String instanceStrToEdit;
		String instanceStrToEditId;
		int assignmentId;
		String accessKey;
		String surveyClass;
		ArrayList<String> files;
	}

	class JsonResponse {
		List<ManifestValue> manifestList;
		SurveyData surveyData = new SurveyData();
		String main;
	}
	
	// Globals
	private ServerData serverData = null;
	private List<ManifestValue> manifestList = null;
	private String mimeType = null;
	private JsonResponse jr = null;
	SurveyTemplate template = null;
	ResourceBundle localisation = null;
	boolean viewOnly = false;
	String userIdent = null;

	/*
	 * Get instance data Respond with JSON
	 */
	@GET
	@Path("/key/instance/{ident}/{updateid}/{key}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getInstanceJson(@Context HttpServletRequest request, @PathParam("ident") String formIdent,
			@PathParam("updateid") String updateid, // Unique id of instance data
			@PathParam("key") String authorisationKey) throws IOException {

		log.info("Requesting json instance");

		Response resp = null;
		String requester = "surveyMobileAPI-Webform";
		Connection connectionSD = SDDataSource.getConnection(requester);

		try {
			userIdent = GeneralUtilityMethods.getDynamicUser(connectionSD, authorisationKey);
			resp = getInstanceData(connectionSD, request, formIdent, updateid, userIdent, false);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection(requester, connectionSD);
		}

		if (userIdent == null) {
			log.info("User not found for key");
			throw new JsonAuthorisationException();
		}

		return resp;
	}

	/*
	 * Get instance data User is authenticated by the web server Respond with JSON
	 */
	@GET
	@Path("/instance/{ident}/{updateid}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getInstanceJsonNoKey(@Context HttpServletRequest request, @PathParam("ident") String formIdent,
			@PathParam("updateid") String updateid // Unique id of instance data
	) throws IOException {

		log.info("Requesting json instance no key");

		Response resp = null;
		String requester = "surveyMobileAPI-Webform";
		Connection connectionSD = SDDataSource.getConnection(requester);

		try {
			userIdent = request.getRemoteUser();
			log.info("Requesting instance as: " + userIdent);
			resp = getInstanceData(connectionSD, request, formIdent, updateid, userIdent, true);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection(requester, connectionSD);
		}

		return resp;
	}

	/*
	 * Get form data Respond with JSON
	 */
	@GET
	@Path("/key/{ident}/{key}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getFormJson(@Context HttpServletRequest request, @PathParam("ident") String formIdent,
			@PathParam("key") String authorisationKey, @QueryParam("datakey") String datakey, // Optional keys to
																								// instance data
			@QueryParam("datakeyvalue") String datakeyvalue, @QueryParam("assignment_id") int assignmentId,
			@QueryParam("callback") String callback) throws IOException {

		log.info("Requesting json");

		String requester = "WebForm - getFormJson";
		Connection connectionSD = SDDataSource.getConnection(requester);

		try {
			userIdent = GeneralUtilityMethods.getDynamicUser(connectionSD, authorisationKey);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection(requester, connectionSD);
		}

		if (userIdent == null) {
			log.info("User not found for key");
			throw new JsonAuthorisationException();
		}

		mimeType = "json";
		return getWebform(request, formIdent, datakey, datakeyvalue, assignmentId, callback, false, false,
				false);
	}

	// Respond with HTML
	@GET
	@Path("/{ident}")
	@Produces(MediaType.TEXT_HTML)
	public Response getFormHTML(@Context HttpServletRequest request, @PathParam("ident") String formIdent,
			@QueryParam("datakey") String datakey, // Optional keys to instance data
			@QueryParam("datakeyvalue") String datakeyvalue, 
			@QueryParam("assignment_id") int assignmentId,
			@QueryParam("viewOnly") boolean vo,
			@QueryParam("callback") String callback) throws IOException {

		mimeType = "html";
		if (callback != null) {
			// I guess they really want JSONP
			mimeType = "json";
		}
		viewOnly = vo;

		userIdent = request.getRemoteUser();
		return getWebform(request, formIdent, datakey, datakeyvalue, assignmentId, callback,
				false, true, false);
	}

	/*
	 * Respond with HTML Temporary User
	 */
	//
	@GET
	@Path("/id/{temp_user}/{ident}")
	@Produces(MediaType.TEXT_HTML)
	public Response getFormHTMLTemporaryUser(@Context HttpServletRequest request, @PathParam("ident") String formIdent,
			@PathParam("temp_user") String tempUser, @QueryParam("datakey") String datakey, // Optional keys to instance
																							// data
			@QueryParam("datakeyvalue") String datakeyvalue, 
			@QueryParam("assignment_id") int assignmentId,
			@QueryParam("viewOnly") boolean vo,
			@QueryParam("callback") String callback) throws IOException {

		mimeType = "html";
		if (callback != null) {
			// I guess they really want JSONP
			mimeType = "json";
		}
		viewOnly = vo;
		
		userIdent = tempUser;
		return getWebform(request, formIdent, datakey, datakeyvalue, assignmentId, callback, false,
				true, true);
	}

	/*
	 * Get the response as either HTML or JSON
	 */
	private Response getWebform(HttpServletRequest request, String formIdent, String datakey,
			String datakeyvalue, int assignmentId, String callback, boolean simplifyMedia,
			boolean isWebForm, boolean isTemporaryUser) {

		Response response = null;

		log.info("webForm:" + formIdent + " datakey:" + datakey + " datakeyvalue:" + datakeyvalue + "assignmentId:"
				+ assignmentId);

		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		}

		Survey survey = null;
		int orgId = 0;
		String accessKey = null;
		String requester = "surveyMobileAPI-getWebForm";
		boolean superUser = false;

		/*
		 * Get the media manifest so we can set the url's of media files used the form
		 * Also get the google api key
		 */
		String basePath = GeneralUtilityMethods.getBasePath(request);
		requester = "surveyMobileAPI-getWebForm2";
		TranslationManager translationMgr = new TranslationManager();
		ServerManager sm = new ServerManager();
		StringBuffer outputString = new StringBuffer();

		
		if (userIdent != null) {
			
			// Authorisation
			Connection connectionSD = SDDataSource.getConnection(requester);
			if (isTemporaryUser) {
				a.isValidTemporaryUser(connectionSD, userIdent);
			}
			a.isAuthorised(connectionSD, userIdent);
			SurveyManager surveyManager = new SurveyManager();
			survey = surveyManager.getSurveyId(connectionSD, formIdent); // Get the survey id from the templateName / key
			if (survey == null) {
				throw new NotFoundException();
			}
			try {
				superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
			} catch (Exception e) {
			}
			a.isValidSurvey(connectionSD, userIdent, survey.id, false, superUser); // Validate that the user has access																			
			a.isBlocked(connectionSD, survey.id, false); // Validate that the survey is not blocked
			// End Authorisation
			
			// Get the organisation id and an access key to upload the results of this form
			// (used from iPhones which do not do authentication on POSTs)
			try {
				orgId = GeneralUtilityMethods.getOrganisationId(connectionSD, userIdent, 0);
				accessKey = GeneralUtilityMethods.getNewAccessKey(connectionSD, userIdent, formIdent);

				// Get the users locale
				Locale locale = new Locale(
						GeneralUtilityMethods.getUserLanguage(connectionSD, request.getRemoteUser()));
				localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
				
				manifestList = translationMgr.getManifestBySurvey(connectionSD, userIdent, survey.id, basePath, formIdent);
				serverData = sm.getServer(connectionSD);
				
			} catch (Exception e) {
				log.log(Level.SEVERE, "WebForm", e);
			} finally {
				SDDataSource.closeConnection(requester, connectionSD);
			}
		} else {
			throw new AuthorisationException();
		}

		// Generate the web form
		try {

			// Get the XML of the Form
			template = new SurveyTemplate();
			template.readDatabase(survey.id, true);
			String surveyClass = template.getSurveyClass();

			// If required get the instance data
			String instanceXML = null;
			String instanceStrToEditId = null;
			if (datakey != null && datakeyvalue != null) {
				GetXForm xForm = new GetXForm();
				instanceXML = xForm.getInstance(survey.id, formIdent, template, datakey, datakeyvalue, 0, simplifyMedia,
						isWebForm);
				instanceStrToEditId = xForm.getInstanceId();
			}

			if (mimeType.equals("json")) {
				jr = new JsonResponse();
				jr.manifestList = new ArrayList<ManifestValue>();
			}

			// Convert to HTML / Json
			if (mimeType.equals("json")) {

				jr.surveyData.modelStr = getModelStr(request);
				if (instanceXML != null) {
					jr.surveyData.instanceStrToEdit = instanceXML.replace("\n", "").replace("\r", "");
				}
				jr.surveyData.instanceStrToEditId = instanceStrToEditId;

				// Add the assignment id if this was set
				if (assignmentId != 0) {
					jr.surveyData.assignmentId = assignmentId;
				}

				// Add access key for authentication
				if (accessKey != null) {
					jr.surveyData.accessKey = accessKey;
				}

				// Add survey class's - used for paging
				jr.surveyData.surveyClass = survey.surveyClass;
				//jr.surveyData.surveyClass = xForm.getSurveyClass();

				jr.main = addMain(request, instanceStrToEditId, orgId, true, surveyClass).toString();

				if (callback != null) {
					outputString.append(callback + " (");
				}
				Gson gsonResp = new GsonBuilder().disableHtmlEscaping().create();
				outputString.append(gsonResp.toJson(jr));
				if (callback != null) {
					outputString.append(")");
				}
			} else {
				outputString.append(addDocument(request, instanceXML, instanceStrToEditId, assignmentId,
						survey.surveyClass, orgId, accessKey));
			}

			/*
			 * TODO Fix issue with itemsets not having images. The best approach is probably
			 * to replace XSL with POJ rather than attempting complex text replacement
			 */
			String respString = outputString.toString();
			response = Response.status(Status.OK).entity(respString).build();

			log.info("userevent: " + userIdent + " : webForm : " + formIdent);

		} catch (Exception e) {
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
			log.log(Level.SEVERE, e.getMessage(), e);
		}

		return response;
	}

	/*
	 * Add the HTML
	 */
	private StringBuffer addDocument(HttpServletRequest request, String instanceXML,
			String dataToEditId, int assignmentId, String surveyClass, int orgId, String accessKey)
			throws TransformerFactoryConfigurationError, Exception {

		StringBuffer output = new StringBuffer();

		output.append("<!DOCTYPE html>\n");

		output.append("<html lang='en'  class='no-js'");
		if (instanceXML == null) {
			// Single shot requests do not have a manifest
			// TODO add manifest
		}
		output.append(">\n");

		output.append(
				addHead(request, instanceXML, dataToEditId, assignmentId, surveyClass, accessKey));
		output.append(addBody(request, dataToEditId, orgId, surveyClass));

		output.append("</html>\n");
		return output;
	}

	/*
	 * Add the head section
	 */
	private StringBuffer addHead(HttpServletRequest request, String instanceXML, String dataToEditId,
			int assignmentId, String surveyClass, String accessKey)
			throws TransformerFactoryConfigurationError, Exception {

		StringBuffer output = new StringBuffer();

		// head
		output.append("<head>\n");
		output.append(
				"<link href='https://fonts.googleapis.com/css?family=Open+Sans:400,700,600&subset=latin,cyrillic-ext,cyrillic,greek-ext,greek,vietnamese,latin-ext' rel='stylesheet' type='text/css'>\n");

		output.append("<link type='text/css' href='/build/css/webform.css' media='all' rel='stylesheet' />\n");
		if (surveyClass != null && surveyClass.trim().contains("theme-grid")) {
			output.append("<link type='text/css' href='/build/css/grid.css' media='all' rel='stylesheet' />\n");
			output.append("<link type='text/css' href='/build/css/grid-print.css' media='print' rel='stylesheet'/>\n");
		} else {
			output.append("<link type='text/css' href='/build/css/formhub.css' media='all' rel='stylesheet' />\n");
			output.append(
					"<link type='text/css' href='/build/css/formhub-print.css' media='print' rel='stylesheet'/>\n");
		}

		output.append("<link rel='shortcut icon' href='favicon.ico'>\n");
		// <!-- For third-generation iPad with high-resolution Retina display: -->
		output.append(
				"<link rel='apple-touch-icon-precomposed' sizes='144x144' href='images/fieldTask_144_144_min.png'>\n");
		// <!-- For iPhone with high-resolution Retina display: -->
		output.append(
				"<link rel='apple-touch-icon-precomposed' sizes='114x114' href='images/fieldTask_114_114_min.png'>\n");
		// <!-- For first- and second-generation iPad: -->
		output.append(
				"<link rel='apple-touch-icon-precomposed' sizes='72x72' href='images/fieldTask_72_72_min.png'>\n");
		// <!-- For non-Retina iPhone, iPod Touch, and Android 2.1+ devices: -->
		output.append("<link rel='apple-touch-icon-precomposed' href='images/fieldTask_57_57_min.png'>\n");

		output.append("<meta charset='utf-8' />\n");
		output.append("<meta name='viewport' content='width=device-width, initial-scale=1.0' />\n");
		output.append("<meta name='apple-mobile-web-app-capable' content='yes' />\n");
		output.append("<!--[if lt IE 10]>");
		output.append("<script type='text/javascript'>window.location = 'modern_browsers';</script>\n");
		output.append("<![endif]-->\n");

		output.append("<script src='/js/libs/modernizr.js'></script>");
		output.append(addData(request, instanceXML, dataToEditId, assignmentId, accessKey));
		// Add the google API key
		output.append("<script>");
		output.append("window.smapConfig = {};");
		if (serverData.google_key != null) {
			output.append("window.smapConfig.googleApiKey=\"");
			output.append(serverData.google_key);
			output.append("\";");
		}
		output.append("</script>");
		output.append("</head>\n");

		return output;
	}

	/*
	 * Add the data
	 */
	private StringBuffer addData(HttpServletRequest request, String instanceXML, String dataToEditId,
			int assignmentId, String accessKey)
			throws TransformerFactoryConfigurationError, Exception {

		StringBuffer output = new StringBuffer();

		output.append("<script type='text/javascript'>\n");
		output.append("settings = {};\n");

		output.append("surveyData = {};\n");

		// Data model

		output.append("surveyData.modelStr='");
		output.append(getModelStr(request));
		output.append("';\n");

		// Instance Data
		if (instanceXML != null) {
			output.append("surveyData.instanceStrToEdit='");
			output.append(instanceXML.replace("\n", "").replace("\r", ""));
			output.append("';\n");
		}

		// Add identifier of existing record, use this as a key on results submission to
		// update an existing record
		if (dataToEditId == null) {
			output.append("surveyData.instanceStrToEditId = undefined;\n");
		} else {
			output.append("surveyData.instanceStrToEditId='");
			output.append(dataToEditId);
			output.append("';\n");
		}

		// Add the assignment id if this was set
		if (assignmentId == 0) {
			output.append("surveyData.assignmentId = undefined;\n");
		} else {
			output.append("surveyData.assignmentId='");
			output.append(assignmentId);
			output.append("';\n");

		}

		// Add access key for authentication
		if (accessKey == null) {
			output.append("surveyData.key = undefined;\n");
		} else {
			output.append("surveyData.key='");
			output.append(accessKey);
			output.append("';\n");
		}
		
		// Add viewOnly flag if set
		if(viewOnly) {
			output.append("surveyData.viewOnly=true;\n");
		} else {
			output.append("surveyData.viewOnly=false;\n");
		}

		output.append("</script>\n");
		return output;
	}

	/*
	 * Get the model string
	 */
	private String getModelStr(HttpServletRequest request)
			throws TransformerFactoryConfigurationError, Exception {

		GetXForm xForm = new GetXForm();
		String model = xForm.get(template, true, true, true);
		
		//String dataDoc = transform(request, formXML, "/XSL/openrosa2xmlmodel.xsl").replace("\n", "").replace("\r", "");

		// We only want the model - remove any XML preanble
		int modelIdx = model.indexOf("<model>");
		
		if(modelIdx > 0) {
			model = model.substring(modelIdx);
			
			model = model.replace("\n", "");
			model = model.replace("\r", "");
		} 
		
		return model;

	}

	/*
	 * Add the body
	 */
	private StringBuffer addBody(HttpServletRequest request, String dataToEditId, int orgId,
			String surveyClass)
			throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		StringBuffer output = new StringBuffer();

		output.append("<body class='clearfix edit'>");
		output.append(getAside());
		output.append(addMain(request, dataToEditId, orgId, false, surveyClass));
		output.append(getDialogs());

		// Webforms script
		output.append("<script src='/build/js/webform-bundle.js'></script>\n");

		output.append("</body>");

		return output;
	}

	/*
	 * Get the "Main" element of an enketo form
	 */
	private StringBuffer addMain(HttpServletRequest request, String dataToEditId, int orgId,
			boolean minimal, String surveyClass)
			throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {

		StringBuffer output = new StringBuffer();
		output.append(openMain(orgId, minimal));

		// String transformed = transform(request, formXML,
		// "/XSL/openrosa2html5form.xsl");

		GetHtml getHtml = new GetHtml();
		String html = getHtml.get(request, template.getSurvey().getId(), false, userIdent);

		// Convert escaped XML into HTML
		html = html.replaceAll("&gt;", ">");
		html = html.replaceAll("&lt;", "<");
		html = html.replaceAll("&quot;", "\"");
		
		for (int i = 0; i < manifestList.size(); i++) {
			log.info(manifestList.get(i).fileName + " : " + manifestList.get(i).url + " : "
					+ manifestList.get(i).type);
			String type = manifestList.get(i).type;
			String name = manifestList.get(i).fileName;
			String url = manifestList.get(i).url;
			if (type.equals("image")) {
				type = "images";
			}
			
			if (url != null) {
				if (mimeType.equals("json")) {
					ManifestValue mv = new ManifestValue(); // Create a new version of the manifest to send to a
															// client that doesn't have unneeded data
					mv.type = type;
					mv.fileName = name;
					mv.url = url;
					jr.manifestList.add(mv);
				} else {
					html = html.replaceAll("jr://" + type + "/" + name, url);
				}
			}
		}

		output.append(html);

		if (!minimal) {
			output.append(closeMain(dataToEditId, surveyClass));
		}

		return output;
	}

	/*
	 * Add some dialogs
	 */
	private StringBuffer getDialogs() {
		StringBuffer output = new StringBuffer();

		output.append("<!-- Start Dialogs -->\n");
		output.append(
				"<div id='dialog-alert' class='modal fade' role='dialog' aria-labelledby='alert dialog' aria-hidden='true'  data-keyboard='true'>\n");
		output.append("<div class='modal-dialog'>\n");
		output.append("<div class='modal-content'>\n");
		output.append("<div class='modal-header'>\n");
		output.append("<button type='button' class='close' data-dismiss='modal' aria-hidden='true'>&times;</button>\n");
		output.append("<h3></h3>\n");
		output.append("</div>\n");
		output.append("<div class='modal-body'>\n");
		output.append("<p class=''></p>\n");
		output.append("</div>\n");
		output.append("<div class='modal-footer'>\n");
		output.append("<span class='self-destruct-timer'></span>\n");
		output.append("<button class='btn' data-dismiss='modal' aria-hidden='true'>Close</button>\n");
		output.append("</div>\n");
		output.append("</div>\n");
		output.append("</div>\n");
		output.append("</div>  <!-- end dialog-alert -->\n");

		output.append(
				"<div id='dialog-confirm' class='modal fade' role='dialog' aria-labelledby='confirmation dialog' aria-hidden='true'  data-keyboard='true'>\n");
		output.append("<div class='modal-dialog'>\n");
		output.append("<div class='modal-content'>\n");
		output.append("<div class='modal-header'>\n");
		output.append("<button type='button' class='close' data-dismiss='modal' aria-hidden='true'>&times;</button>\n");
		output.append("<h3></h3>\n");
		output.append("</div>\n");
		output.append("<div class='modal-body'>\n");
		output.append("<p class='alert alert-danger'></p>\n");
		output.append("<p class='msg'></p>\n");
		output.append("</div>\n");
		output.append("<div class='modal-footer'>\n");
		output.append("<span class='self-destruct-timer'></span>\n");
		output.append("<button class='negative btn'>Close</button>\n");
		output.append("<button class='positive btn btn-primary'>Confirm</button>\n");
		output.append("</div>\n");
		output.append("</div>\n");
		output.append("</div>\n");
		output.append("</div>   <!-- end dialog-confirm -->\n");

		output.append(
				"<div id='dialog-save' class='modal fade' role='dialog' aria-labelledby='save dialog' aria-hidden='true' data-keyboard='true'>\n");
		output.append("<div class='modal-dialog'>\n");
		output.append("<div class='modal-content'>\n");
		output.append("<div class='modal-header'>\n");
		output.append("<button type='button' class='close' data-dismiss='modal' aria-hidden='true'>&times;</button>\n");
		output.append("<h3></h3>\n");
		output.append("</div>\n");
		output.append("<div class='modal-body'>\n");
		output.append("<form onsubmit='return false;'>\n");
		output.append("<div class='alert alert-danger'></div>\n");
		output.append("<label>\n");
		output.append("<span>Record Name</span>\n");
		output.append(
				"<span class='or-hint active'>This name allows you to easily find your draft record to finish it later. The record name will not be submitted to the server.</span>\n");
		output.append("<input name='record-name' type='text' required='required'/>\n");
		output.append("</label>\n");
		output.append("</form>\n");
		output.append("</div>\n");
		output.append("<div class='modal-footer'>\n");
		output.append("<button class='negative btn'>Close</button>\n");
		output.append("<button class='positive btn btn-primary'>Save &amp; Close</button>\n");
		output.append("</div>\n");
		output.append("</div>\n");
		output.append("</div>\n");
		output.append("</div> <!-- end dialog-save -->\n");

		// used for Grid theme only
		output.append(
				"<div id='dialog-print' class='modal fade' role='dialog' aria-labelledby='print dialog' aria-hidden='true'  data-keyboard='true'>\n");
		output.append("<div class='modal-dialog'>\n");
		output.append("<div class='modal-content'>\n");
		output.append("<div class='modal-header'>\n");
		output.append("<button type='button' class='close' data-dismiss='modal' aria-hidden='true'>&times;</button>\n");
		output.append("<h3 class='select-format'>Select Print Settings</h3>\n");
		output.append("<h3 class='progress'>Preparing...</h3>\n");
		output.append("</div>");
		output.append("<div class='modal-body'>\n");
		output.append("<section class='progress'>\n");
		output.append("<p>Working hard to prepare your optimized print view. Hold on!</p>\n");
		output.append("<progress></progress>\n");
		output.append("</section>\n");
		output.append("<section class='select-format'>\n");
		output.append("<p>To prepare an optimized print, please select the print settings below</p>\n");
		output.append("<form onsubmit='return false;'>\n");
		output.append("<fieldset>\n");
		output.append("<legend>Paper Size</legend>\n");
		output.append("<label>\n");
		output.append("<input name='format' type='radio' value='A4' required checked/>\n");
		output.append("<span>A4</span>\n");
		output.append("</label>\n");
		output.append("<label>\n");
		output.append("<input name='format' type='radio' value='letter' required/>\n");
		output.append("<span>Letter</span>\n");
		output.append("</label>\n");
		output.append("</fieldset>\n");
		output.append("<fieldset>\n");
		output.append("<legend>Paper Orientation</legend>\n");
		output.append("<label>\n");
		output.append("<input name='orientation' type='radio' value='portrait' required checked/>\n");
		output.append("<span>Portrait</span>\n");
		output.append("</label>\n");
		output.append("<label>\n");
		output.append("<input name='orientation' type='radio' value='landscape' required/>\n");
		output.append("<span>Landscape</span>\n");
		output.append("</label>\n");
		output.append("</fieldset>\n");
		output.append("</form>\n");
		output.append(
				"<p class='alert alert-info'>Remember to set these same print settings in the browser's print menu afterwards!</p>\n");
		output.append("</section>\n");
		output.append("</div>\n");
		output.append("<div class='modal-footer'>\n");
		output.append("<button class='negative btn'>Close</button>\n");
		output.append("<button class='positive btn btn-primary'>Prepare</button>\n");
		output.append("</div>\n");
		output.append("</div>\n");
		output.append("</div>\n");
		output.append("</div> <!-- end dialog-print for grid -->\n");

		return output;
	}

	private StringBuffer getAside() {

		StringBuffer output = new StringBuffer();

		output.append("<div id='feedback-bar' class='alert alert-warning'>\n");
		output.append("<span class='glyphicon glyphicon-info-sign'></span>\n");
		output.append("<button class='close'>&times;</button>\n");
		output.append("</div>\n");

		output.append("<aside class='side-slider'>\n");
		output.append("<button type='button' class='close' data-dismiss='side-slider' aria-hidden='true'>×</button>\n");
		output.append("<nav></nav>\n");
		output.append("<div class='content'>\n");
		output.append("</div>\n");
		output.append("</aside>\n");

		output.append("<button class='handle side-slider-toggle open'></button>\n");
		output.append("<button class='handle side-slider-toggle close'></button>\n");
		output.append("<div class='side-slider-toggle slider-overlay'></div>\n");

		return output;
	}

	private StringBuffer openMain(int orgId, boolean minimal) {
		StringBuffer output = new StringBuffer();

		output.append("<div class='main'>\n");

		// Add the google api key
		if (serverData != null) {
			output.append("<div id='googleApiKey' style='display:none;'>");
			output.append(serverData.google_key);
			output.append("</div>");
		}

		output.append("<article class='paper'>\n");
		if (!minimal) {
			output.append("<header class='form-header clearfix'>\n");
			output.append("<div class='offline-enabled'>\n");
			output.append("<div title='Records Queued' class='queue-length side-slider-toggle'>0</div>\n");
			output.append("</div>\n");
			output.append("<button onclick='return false;' class='print' title='Print this Form'> </button>\n");
			output.append("<span class='form-language-selector'><span>Choose Language</span></span>\n");
			output.append("<div class='form-progress'></div>\n");

			output.append("<span class='logo-wrapper'>\n");
			output.append(addNoScriptWarning(localisation));
			output.append("<img class='banner_logo' src='/media/organisation/");
			output.append(orgId);
			output.append(
					"/settings/bannerLogo' onerror=\"if(this.src.indexOf('smap_logo.png') < 0) this.src='/images/smap_logo.png';\" alt='logo'>\n");
			output.append("</span>\n");

			output.append("</header>\n");
		}
		return output;
	}

	private StringBuffer closeMain(String dataToEditId, String surveyClass) {
		StringBuffer output = new StringBuffer();

		output.append("<section class='form-footer'>\n");
		output.append("<div class='content'>\n");
		output.append(
				"<fieldset class='draft question'><div class='option-wrapper'><label class='select'><input class='ignore' type='checkbox' name='draft'/><span class='option-label'>Save as Draft</span></label></div></fieldset>\n");

		output.append("<div class='main-controls'>\n");
		if (dataToEditId == null) {
			output.append("<button id='submit-form' class='btn btn-primary btn-large' >Submit</button>\n");
		} else {
			output.append("<button id='submit-form-single' class='btn btn-primary btn-large' >Submit</button>\n");
		}
		if (surveyClass != null && surveyClass.contains("pages")) {
			output.append("<a class='previous-page disabled' href='#'>Back</a>\n");
			output.append("<a class='next-page' href='#'>Next</span></a>\n");
		}
		output.append(
				"<div class=\"enketo-power\" style=\"margin-bottom: 30px;\">Powered by <a href=\"http://enketo.org\" title=\"enketo.org website\"><img src=\"/images/enketo_bare_150x56.png\" alt=\"enketo logo\" /></a> </div>");
		// output.append("<img src=/images/enketo.png style=\"position: absolute; right:
		// 0px; bottom: 0px; height:40px;\">");
		output.append("</div>\n"); // main controls

		if (surveyClass != null && surveyClass.contains("pages")) {

			output.append("<div class='jump-nav'>\n");
			output.append("<a class='btn btn-default disabled first-page' href='#'>Return to Beginning</a>\n");
			output.append("<a class='btn btn-default disabled last-page' href='#'>Go to End</a>\n");
			output.append("</div>");
		}

		output.append("</div>\n"); // content
		output.append("</section> <!-- end form-footer -->\n");

		output.append("</article>\n");
		output.append("</div> <!-- end main -->\n");

		return output;
	}

	/*
	 * Get instance data as JSON
	 */
	private Response getInstanceData(Connection connectionSD, HttpServletRequest request, String formIdent,
			String updateid, String user, boolean simplifyMedia) {

		Response response = null;

		log.info("webForm:" + formIdent + " updateid:" + updateid + " user: " + user);

		Survey survey = null;
		StringBuffer outputString = new StringBuffer();
		boolean superUser = false;

		// Authorisation
		if (user != null) {
			a.isAuthorised(connectionSD, user);
			SurveyManager sm = new SurveyManager();
			survey = sm.getSurveyId(connectionSD, formIdent); // Get the survey id from the templateName / key
			if (survey == null) {
				throw new NotFoundException();
			}

			try {
				superUser = GeneralUtilityMethods.isSuperUser(connectionSD, request.getRemoteUser());
			} catch (Exception e) {
			}
			a.isValidSurvey(connectionSD, user, survey.id, false, superUser); // Validate that the user can access this
																				// survey
			a.isBlocked(connectionSD, survey.id, false); // Validate that the survey is not blocked

		} else {
			throw new AuthorisationException();
		}
		// End Authorisation

		// Get the data
		try {

			// Get the XML of the Form
			SurveyTemplate template = new SurveyTemplate();
			template.readDatabase(survey.id, false);

			// template.printModel(); // debug
			//GetXForm xForm = new GetXForm();
			// String formXML = xForm.get(template);

			// If required get the instance data
			String instanceXML = null;
			String dataKey = "instanceid";

			GetXForm xForm = new GetXForm();
			instanceXML = xForm.getInstance(survey.id, formIdent, template, dataKey, updateid, 0, simplifyMedia, false);

			SurveyData sd = new SurveyData();
			sd.instanceStrToEdit = instanceXML.replace("\n", "").replace("\r", "");
			sd.instanceStrToEditId = updateid;
			sd.files = xForm.getFilenames();

			Gson gsonResp = new GsonBuilder().disableHtmlEscaping().create();
			outputString.append(gsonResp.toJson(sd));

			response = Response.status(Status.OK).entity(outputString.toString()).build();

			log.info("userevent: " + user + " : instanceData : " + formIdent + " : updateId : " + updateid);

		} catch (Exception e) {
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
			log.log(Level.SEVERE, e.getMessage(), e);
		}

		return response;
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

	/*
	 * Escape quotes
	 */
	private String escapeQuotes(String input) {
		StringBuffer output = new StringBuffer("");
		String txt;

		Pattern pattern = Pattern.compile("<value>.*<\\/value>");
		Pattern patternOutput = Pattern.compile("<output.*\\/>");
		java.util.regex.Matcher matcher = pattern.matcher(input);
		int start = 0;
		while (matcher.find()) {

			String matched = matcher.group();

			// Add any text before the match
			int startOfGroup = matcher.start();
			txt = input.substring(start, startOfGroup);
			output.append(txt);
			start = startOfGroup;

			/*
			 * Add the matched section inside a value Escape all quotes except those inside
			 * an output
			 */
			java.util.regex.Matcher matcherOutput = patternOutput.matcher(matched); // Skip over output definitions
			while (matcherOutput.find()) {

				String matchedOutput = matcherOutput.group();

				// Add text up to the output escaping quotes
				int startOfOutputGroup = matcherOutput.start();
				txt = input.substring(start, start + startOfOutputGroup).replaceAll("\"", "&quot;");
				output.append(txt);

				// Add the matched output
				output.append(matchedOutput);

				start = start + matcherOutput.end();

			}

			// Get the remainder of the string inside the value element
			if (start < input.length()) {
				txt = input.substring(start, matcher.end()).replaceAll("\"", "&quot;");
				output.append(txt);
			}

			// Reset the start
			start = matcher.end();

		}

		// Get the remainder of the string outside of the value element
		if (start < input.length()) {
			txt = input.substring(start);
			output.append(txt);
		}

		return output.toString();
	}


}
