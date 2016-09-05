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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.xalan.processor.TransformerFactoryImpl;
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
import org.smap.server.utilities.GetXForm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


/*
 * Return a survey as a webform
 */

@Path("/webForm")
public class WebForm extends Application{
	
	Authorise a = new Authorise(null, Authorise.ENUM);

	private static Logger log =
			 Logger.getLogger(WebForm.class.getName());
	
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
	

	/*
	 * Get instance data
	 * Respond with JSON
	 */
	@GET
	@Path("/key/instance/{ident}/{updateid}/{key}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getInstanceJson(@Context HttpServletRequest request,
			@PathParam("ident") String formIdent,
			@PathParam("updateid") String updateid,	// Unique id of instance data
			@PathParam("key") String authorisationKey
			) throws IOException {
		
		log.info("Requesting json instance");
		
		String user = null;		
		Response resp = null;
		String requester = "surveyMobileAPI-Webform";
		Connection connectionSD = SDDataSource.getConnection(requester);
		
		try {
			user = GeneralUtilityMethods.getDynamicUser(connectionSD, authorisationKey);
			resp = getInstanceData(connectionSD, request, formIdent, updateid, user, false);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection(requester, connectionSD);
		}
		
		if (user == null) {
			log.info("User not found for key");
			throw new JsonAuthorisationException();
		}
		
		return resp;
	}
		
	/*
	 * Get instance data
	 * User is authenticated by the web server
	 * Respond with JSON
	 */
	@GET
	@Path("/instance/{ident}/{updateid}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getInstanceJsonNoKey(@Context HttpServletRequest request,
			@PathParam("ident") String formIdent,
			@PathParam("updateid") String updateid	// Unique id of instance data
			) throws IOException {
		
		log.info("Requesting json instance no key");
		
		String user = null;		
		Response resp = null;
		String requester = "surveyMobileAPI-Webform";
		Connection connectionSD = SDDataSource.getConnection(requester);
		
		try {
			user = request.getRemoteUser();
			log.info("Requesting instance as: " + user);
			resp = getInstanceData(connectionSD, request, formIdent, updateid, user, true);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection(requester, connectionSD);
		}
		
		return resp;
	}
		
	/*
	 * Get form data
	 * Respond with JSON
	 */
	@GET
	@Path("/key/{ident}/{key}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getFormJson(@Context HttpServletRequest request,
			@PathParam("ident") String formIdent,
			@PathParam("key") String authorisationKey,
			@QueryParam("datakey") String datakey,			// Optional keys to instance data	
			@QueryParam("datakeyvalue") String datakeyvalue,
			@QueryParam("assignment_id") int assignmentId,
			@QueryParam("callback") String callback
			) throws IOException {
		
		log.info("Requesting json");
		
		String user = null;		
		String requester = "WebForm - getFormJson";
		Connection connectionSD = SDDataSource.getConnection(requester);
		
		try {
			user = GeneralUtilityMethods.getDynamicUser(connectionSD, authorisationKey);
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			SDDataSource.closeConnection(requester, connectionSD);
		}
		
		if (user == null) {
			log.info("User not found for key");
			throw new JsonAuthorisationException();
		}
		
		return getWebform(request, "json", formIdent, datakey, 
				datakeyvalue, assignmentId, callback, user, false, false, false);
	}
	
	// Respond with HTML
	@GET
	@Path("/{ident}")
	@Produces(MediaType.TEXT_HTML)
	public Response getFormHTML(@Context HttpServletRequest request,
			@PathParam("ident") String formIdent,
			@QueryParam("datakey") String datakey,			// Optional keys to instance data	
			@QueryParam("datakeyvalue") String datakeyvalue,
			@QueryParam("assignment_id") int assignmentId,
			@QueryParam("callback") String callback
			) throws IOException {
		
		String type = "html";
		if(callback != null) {
			// I guess they really want JSONP
			type = "json";
			
		} 
		log.info("Requesting " + type);
		
		System.out.println();
		return getWebform(request, type, formIdent, datakey, datakeyvalue, assignmentId, callback, 
				request.getRemoteUser(), 
				false,
				true,
				false);
	}
	
	/*
	 * Respond with HTML
	 * Temporary User
	 */
	// 
	@GET
	@Path("/id/{temp_user}/{ident}")
	@Produces(MediaType.TEXT_HTML)
	public Response getFormHTMLTemporaryUser(@Context HttpServletRequest request,
			@PathParam("ident") String formIdent,
			@PathParam("temp_user") String tempUser,
			@QueryParam("datakey") String datakey,			// Optional keys to instance data	
			@QueryParam("datakeyvalue") String datakeyvalue,
			@QueryParam("assignment_id") int assignmentId,
			@QueryParam("callback") String callback
			) throws IOException {
		
		String type = "html";
		if(callback != null) {
			// I guess they really want JSONP
			type = "json";
			
		} 
		log.info("Requesting " + type);
		
		System.out.println("Tempuser: " + tempUser);
		return getWebform(request, type, formIdent, datakey, datakeyvalue, assignmentId, callback, 
				tempUser, 
				false,
				true,
				true);
	}
	
	
	/*
	 * Get the response as either HTML or JSON
	 */
	private Response getWebform(HttpServletRequest request, 
			String mimeType, 
			String formIdent, 
			String datakey, 
			String datakeyvalue, 
			int assignmentId,
			String callback,
			String user,
			boolean simplifyMedia,
			boolean isWebForm,
			boolean isTemporaryUser) {
		
		Response response = null;
		JsonResponse jr = null;
		
		log.info("webForm:" + formIdent + " datakey:" + datakey + " datakeyvalue:" + datakeyvalue + "assignmentId:" + assignmentId);
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		}

		Survey survey = null;
		int orgId = 0;
		String accessKey = null;
		String requester = "surveyMobileAPI-getWebForm";
		ResourceBundle localisation = null;
		
		// Authorisation 
		if(user != null) {
			Connection connectionSD = SDDataSource.getConnection(requester);
			if(isTemporaryUser) {
				a.isValidTemporaryUser(connectionSD, user);
			}
            a.isAuthorised(connectionSD, user);
    		SurveyManager sm = new SurveyManager();
    		survey = sm.getSurveyId(connectionSD, formIdent);	// Get the survey id from the templateName / key
    		if(survey == null) {
    			throw new NotFoundException();
    		}
    		a.isValidSurvey(connectionSD, user, survey.id, false);	// Validate that the user can access this survey
    		a.isBlocked(connectionSD, survey.id, false);			// Validate that the survey is not blocked
    		
    		// Get the organisation id and an access key to upload the results of this form (used from iPhones which do not do authentication on POSTs)
    		try {
    			orgId = GeneralUtilityMethods.getOrganisationId(connectionSD, user);
    			accessKey = GeneralUtilityMethods.getNewAccessKey(connectionSD, user, formIdent);
    			
				// Get the users locale
				Locale locale = new Locale(GeneralUtilityMethods.getUserLanguage(connectionSD, request.getRemoteUser()));
				localisation = ResourceBundle.getBundle("org.smap.sdal.resources.SmapResources", locale);
    		} catch (Exception e) {
    			log.log(Level.SEVERE, "WebForm", e);
    		} finally {
    			SDDataSource.closeConnection(requester, connectionSD);
    		}
        } else {
        	throw new AuthorisationException();
        }
		// End Authorisation

		
		StringBuffer outputString = new StringBuffer();
		ServerData serverData = null;
		
		// Generate the web form
		try {	    

			/*
			 * Get the media manifest so we can set the url's of media files used the form
			 * Also get the google api key
			 */
			String basePath = GeneralUtilityMethods.getBasePath(request);
			requester = "surveyMobileAPI-getWebForm2";
			TranslationManager translationMgr = new TranslationManager();	
			ServerManager sm = new ServerManager();
			Connection connectionSD = SDDataSource.getConnection(requester);
			List<ManifestValue> manifestList = null;
			try {
				manifestList = translationMgr.getManifestBySurvey(
						connectionSD, 
						user, 
						survey.id, 
						basePath, 
						formIdent);
				
				serverData = sm.getServer(connectionSD);
				
			} catch (Exception e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			} finally {
				SDDataSource.closeConnection("surveyKPI-Dashboard", connectionSD);
			}
			
			// Get the XML of the Form
			SurveyTemplate template = new SurveyTemplate();
			template.readDatabase(survey.id);
			String surveyClass = template.getSurveyClass();
			
			//template.printModel();	// debug
			GetXForm xForm = new GetXForm();
			String formXML = xForm.get(template, true);		
			
			// If required get the instance data 
			String instanceXML = null;
			String instanceStrToEditId = null;
			if(datakey != null && datakeyvalue != null) {
				xForm = new GetXForm();
				instanceXML = xForm.getInstance(survey.id, formIdent, template, datakey, 
						datakeyvalue, 
						0, 
						simplifyMedia,
						isWebForm);
				instanceStrToEditId = xForm.getInstanceId();
			}
			
    		
			if(mimeType.equals("json")) {
				jr = new JsonResponse();
				jr.manifestList = new ArrayList<ManifestValue> ();
			}

    		for(int i = 0; i < manifestList.size(); i++) {
    			log.info(manifestList.get(i).fileName + " : " + manifestList.get(i).url + " : " + manifestList.get(i).type);
    			String type = manifestList.get(i).type;
    			String name = manifestList.get(i).fileName;
    			String url = manifestList.get(i).url;
    			if(type.equals("image")) {
    				type = "images";
    			}
    			if(url != null) {
    				if(mimeType.equals("json")) {
    					ManifestValue mv = new ManifestValue();	// Create a new version of the manifest to send to a client that doesn't have unneeded data
    					mv.type = type;
    					mv.fileName = name;
    					mv.url = url;
    					jr.manifestList.add(mv);
    				} else {
    					if(type.equals("csv")) {
    						//formXML = formXML.replaceFirst("</instance>", "</instance><instance id=\"hhplotdetails\" src=\"/media/organisation/1/hhplotdetails.csv\" />");
    					} else {
    						formXML = formXML.replaceAll("jr://" + type + "/" + name, url);
    					}
    				}
    			}
			}
    		
			
			// Convert to HTML / Json
    		if(mimeType.equals("json")) {
    			
    			jr.surveyData.modelStr = getModelStr(request, formXML).toString();
    			if(instanceXML != null) {
    				jr.surveyData.instanceStrToEdit = instanceXML.replace("\n", "").replace("\r", "");
    			}
    			jr.surveyData.instanceStrToEditId = instanceStrToEditId;

    			// Add the assignment id if this was set
    			if(assignmentId != 0) {
    				jr.surveyData.assignmentId = assignmentId;
    			}
    			
    			// Add access key for authentication
    			if(accessKey != null) {
    				jr.surveyData.accessKey = accessKey;
    			}
    			
    			// Add survey class's - used for paging
    			jr.surveyData.surveyClass = xForm.getSurveyClass();
    			
    			jr.main = addMain(request, formXML, instanceStrToEditId, 
    					orgId, true, surveyClass, serverData, localisation).toString();
    				
    			if(callback != null) {
    				outputString.append(callback + " (");
    			}
    			Gson gsonResp = new GsonBuilder().disableHtmlEscaping().create();
				outputString.append(gsonResp.toJson(jr));
				if(callback != null) {
					outputString.append(")");
				}
    		} else {
    			outputString.append(addDocument(request, formXML, instanceXML, instanceStrToEditId, 
    					assignmentId, 
    					survey.surveyClass, 
    					orgId, 
    					accessKey,
    					serverData,
    					localisation));
    		}
    		
			response = Response.status(Status.OK).entity(outputString.toString()).build();
    		
			log.info("userevent: " + user + " : webForm : " + formIdent);	
			

		
		} catch (Exception e) {
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
			log.log(Level.SEVERE, e.getMessage(), e);
		} 
				
		return response;
	}
	
	/*
	 * Add the HTML
	 */
	private StringBuffer addDocument(HttpServletRequest request, 
			String formXML, 
			String instanceXML,
			String dataToEditId,
			int assignmentId,
			String surveyClass,
			int orgId,
			String accessKey,
			ServerData serverData,
			ResourceBundle localisation) 
			throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
	
		StringBuffer output = new StringBuffer();
		
		output.append("<!DOCTYPE html>\n");
		
		output.append("<html lang='en'  class='no-js'");
		if(instanceXML == null) {
			// Single shot requests do not have a manifest
			// TODO add manifest
		}
		output.append(">\n");
				
		output.append(addHead(request, formXML, instanceXML, dataToEditId, assignmentId, surveyClass, 
				accessKey,
				serverData));
		output.append(addBody(request, formXML, dataToEditId, orgId, surveyClass, localisation));

		output.append("</html>\n");			
		return output;
	}
	
	/*
	 * Add the head section
	 */
	private StringBuffer addHead(HttpServletRequest request, 
			String formXML, 
			String instanceXML, 
			String dataToEditId, 
			int assignmentId,
			String surveyClass,
			String accessKey,
			ServerData serverData) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		
		StringBuffer output = new StringBuffer();

		// head
		output.append("<head>\n");
		output.append("<link href='https://fonts.googleapis.com/css?family=Open+Sans:400,700,600&subset=latin,cyrillic-ext,cyrillic,greek-ext,greek,vietnamese,latin-ext' rel='stylesheet' type='text/css'>\n");

		//output.append("<link type='text/css' href='/build/css/webform_smap.css' media='all' rel='stylesheet' />\n");
		output.append("<link type='text/css' href='/build/css/webform.css' media='all' rel='stylesheet' />\n");
		//output.append("<link type='text/css' href='/build/css/webform_print_formhub.css' media='print' rel='stylesheet' />\n");
		if(surveyClass != null && surveyClass.trim().contains("theme-grid")) {
			output.append("<link type='text/css' href='/build/css/grid.css' media='all' rel='stylesheet' />\n");
			output.append("<link type='text/css' href='/build/css/grid-print.css' media='print' rel='stylesheet'/>\n");	
		} else {
			output.append("<link type='text/css' href='/build/css/default.css' media='all' rel='stylesheet' />\n");			
			output.append("<link type='text/css' href='/build/css/formhub.css' media='all' rel='stylesheet' />\n");			
			output.append("<link type='text/css' href='/build/css/formhub-print.css' media='print' rel='stylesheet'/>\n");
		}
			
		output.append("<link rel='shortcut icon' href='favicon.ico'>\n");
		//	<!-- For third-generation iPad with high-resolution Retina display: -->
		output.append("<link rel='apple-touch-icon-precomposed' sizes='144x144' href='images/fieldTask_144_144_min.png'>\n");
		//	<!-- For iPhone with high-resolution Retina display: -->
		output.append("<link rel='apple-touch-icon-precomposed' sizes='114x114' href='images/fieldTask_114_114_min.png'>\n");
		//	<!-- For first- and second-generation iPad: -->
		output.append("<link rel='apple-touch-icon-precomposed' sizes='72x72' href='images/fieldTask_72_72_min.png'>\n");
		//	<!-- For non-Retina iPhone, iPod Touch, and Android 2.1+ devices: -->
		output.append("<link rel='apple-touch-icon-precomposed' href='images/fieldTask_57_57_min.png'>\n");
			
		output.append("<meta charset='utf-8' />\n");
		output.append("<meta name='viewport' content='width=device-width, initial-scale=1.0' />\n");
		output.append("<meta name='apple-mobile-web-app-capable' content='yes' />\n");
		output.append("<!--[if lt IE 10]>");
	    output.append("<script type='text/javascript'>window.location = 'modern_browsers';</script>\n");
		output.append("<![endif]-->\n");
			
		output.append(addData(request, formXML, instanceXML, dataToEditId, assignmentId, accessKey));
		// Add the google API key
		output.append("<script>");
			output.append("window.smapConfig = {};");
				if(serverData.google_key != null) {
					output.append("window.smapConfig.googleApiKey=\"");
					output.append(serverData.google_key);
					output.append("\";");
				}
		output.append("</script>");
		
		// Webforms script
		output.append("<script type='text/javascript' src='/build/js/webform-combined.min.js'></script>\n");
		
		output.append("</head>\n");
		
		return output;
	}
	
	/*
	 * Add the data
	 */
	private StringBuffer addData(HttpServletRequest request, 
			String formXML, String instanceXML, 
			String dataToEditId,
			int assignmentId,
			String accessKey) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		
		StringBuffer output = new StringBuffer();
		
		output.append("<script type='text/javascript'>\n");
		output.append("settings = {};\n");
		
		output.append("surveyData = {};\n");    
		
		// Data model
		
		output.append("surveyData.modelStr='");
		output.append(getModelStr(request, formXML));
		output.append("';\n");
		
		// Instance Data
		if(instanceXML != null) {
			output.append("surveyData.instanceStrToEdit='");			
			output.append(instanceXML.replace("\n", "").replace("\r", ""));
			output.append("';\n");
		} 
		
		// Add identifier of existing record, use this as a key on results submission to update an existing record
		if(dataToEditId == null) {
			output.append("surveyData.instanceStrToEditId = undefined;\n");
		} else {
			output.append("surveyData.instanceStrToEditId='");
			output.append(dataToEditId);
			output.append("';\n");
		}
		
		// Add the assignment id if this was set
		if(assignmentId == 0) {
			output.append("surveyData.assignmentId = undefined;\n");
		} else {
			output.append("surveyData.assignmentId='");
			output.append(assignmentId);
			output.append("';\n");

		}
		
		// Add access key for authentication
		if(accessKey == null) {
			output.append("surveyData.key = undefined;\n");
		} else {
			output.append("surveyData.key='");
			output.append(accessKey);
			output.append("';\n");
		}
		
		output.append("</script>\n");
		return output;
	}
	
	/*
	 * Get the model string
	 */
	private StringBuffer getModelStr(HttpServletRequest request, String formXML) 
			throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		
		StringBuffer output = new StringBuffer();
		
		String dataDoc=transform(request, formXML, "/XSL/openrosa2xmlmodel.xsl").replace("\n", "").replace("\r", "");
		
		// We only want the model
		int modelIdx = dataDoc.indexOf("<model>");
		int rootIdx = dataDoc.lastIndexOf("</root>");
		if(modelIdx >=0 && rootIdx >= 0) {
			dataDoc = dataDoc.substring(modelIdx, rootIdx);
		} else {
			log.info("Error: Invalid model: " + dataDoc);
		}
		output.append(dataDoc.replace("\n", "").replace("\r", ""));
		
		return output;
		
	}
	
	/*
	 * Add the body
	 */
	private StringBuffer addBody(HttpServletRequest request, String formXML, 
			String dataToEditId, 
			int orgId,
			String surveyClass,
			ResourceBundle localisation) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		StringBuffer output = new StringBuffer();
		
		output.append("<body class='clearfix edit'>");
		output.append(getAside());
		output.append(addMain(request, formXML, dataToEditId, orgId, false, surveyClass, null, localisation));
		output.append(getDialogs());
		
		output.append("</body>");
		return output;
	}
	
	/*
	 * Get the "Main" element of an enketo form
	 */
	private StringBuffer addMain(HttpServletRequest request, String formXML, 
			String dataToEditId, 
			int orgId, 
			boolean minimal,
			String surveyClass,
			ServerData serverData,
			ResourceBundle localisation) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		StringBuffer output = new StringBuffer();
		
		output.append(openMain(orgId, minimal, serverData, localisation));
		output.append(transform(request, formXML, "/XSL/openrosa2html5form.xsl"));
		if(!minimal) {
			output.append(closeMain(dataToEditId, surveyClass));
		}
		
		return output;
	}
	
	/*
	 * Transform the XML defintion into HTML using enketo style sheets
	 */
	private String transform(HttpServletRequest request, String formXML, String xslt) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		
		InputStream xmlStream = new ByteArrayInputStream(formXML.getBytes("UTF-8"));
		StreamSource source = new StreamSource(xmlStream);
		
		StringWriter writer = new StringWriter();
		StreamResult output = new StreamResult(writer);
		
		StreamSource styleSource = new StreamSource(request.getServletContext().getResourceAsStream(xslt));
		
		TransformerFactory tf = TransformerFactory.newInstance("org.apache.xalan.processor.TransformerFactoryImpl",null);
		//tf.setAttribute(TransformerFactoryImpl.FEATURE_SOURCE_LOCATION, Boolean.TRUE);
		tf.setAttribute(TransformerFactoryImpl.FEATURE_OPTIMIZE, Boolean.FALSE);
		
		Transformer transformer = tf.newTransformer(styleSource);
    	//transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    	transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    	transformer.setOutputProperty(OutputKeys.METHOD, "xml");
    	
    	

    	transformer.transform(source, output);
    	
		
		return writer.toString();
	}
	
	/*
	 * Add some dialogs
	 */
	private StringBuffer getDialogs() {
		StringBuffer output = new StringBuffer();
		
		output.append("<!-- Start Dialogs -->\n");
		output.append("<div id='dialog-alert' class='modal fade' role='dialog' aria-labelledby='alert dialog' aria-hidden='true'  data-keyboard='true'>\n");	
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
	
		output.append("<div id='dialog-confirm' class='modal fade' role='dialog' aria-labelledby='confirmation dialog' aria-hidden='true'  data-keyboard='true'>\n");	
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
	
		output.append("<div id='dialog-save' class='modal fade' role='dialog' aria-labelledby='save dialog' aria-hidden='true' data-keyboard='true'>\n");	
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
								output.append("<span class='or-hint active'>This name allows you to easily find your draft record to finish it later. The record name will not be submitted to the server.</span>\n");	
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
	
		//used for Grid theme only
		output.append("<div id='dialog-print' class='modal fade' role='dialog' aria-labelledby='print dialog' aria-hidden='true'  data-keyboard='true'>\n");	
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
							output.append("<p class='alert alert-info'>Remember to set these same print settings in the browser's print menu afterwards!</p>\n");	
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
	
	private StringBuffer openMain(int orgId, boolean minimal, ServerData serverData, ResourceBundle localisation) {
		StringBuffer output = new StringBuffer();
		
		output.append("<div class='main'>\n");
			
			// Add the google api key
			if(serverData != null) {
				output.append("<div id='googleApiKey' style='display:none;'>");
				output.append(serverData.google_key);
				output.append("</div>");
			}
			
			output.append("<article class='paper'>\n");
			if(!minimal) {
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
						output.append("/settings/bannerLogo' onerror=\"if(this.src.indexOf('smap_logo.png') < 0) this.src='/images/smap_logo.png';\" alt='logo'>\n");
					output.append("</span>\n");

				output.append("</header>\n");
			}
		return output;
	}
	
	private StringBuffer closeMain(String dataToEditId, String surveyClass) {
		StringBuffer output = new StringBuffer();
		
		output.append("<section class='form-footer'>\n");
		output.append("<div class='content'>\n");
		output.append("<fieldset class='draft question'><div class='option-wrapper'><label class='select'><input class='ignore' type='checkbox' name='draft'/><span class='option-label'>Save as Draft</span></label></div></fieldset>\n");
		
		output.append("<div class='main-controls'>\n");
		if(dataToEditId == null) {
			output.append("<button id='submit-form' class='btn btn-primary btn-large' >Submit</button>\n");
		} else {
			output.append("<button id='submit-form-single' class='btn btn-primary btn-large' >Submit</button>\n");
		}
		if(surveyClass !=null && surveyClass.contains("pages")) {
			output.append("<a class='previous-page disabled' href='#'>Back</a>\n");
			output.append("<a class='next-page' href='#'>Next</span></a>\n");
		}
		output.append("</div>\n");	// main controls
		
		if(surveyClass !=null && surveyClass.contains("pages")) {
			
			output.append("<div class='jump-nav'>\n");
			output.append("<a class='btn btn-default disabled first-page' href='#'>Return to Beginning</a>\n");
			output.append("<a class='btn btn-default disabled last-page' href='#'>Go to End</a>\n");
			output.append("</div>");
		}
				

		output.append("</div>\n");	// content
		output.append("</section> <!-- end form-footer -->\n");
		
		output.append("</article>\n");
		output.append("</div> <!-- end main -->\n");

		return output;
	}
	
	/*
	 * Get instance data as JSON
	 */
	private Response getInstanceData(Connection connectionSD, HttpServletRequest request, 
			String formIdent, 
			String updateid, 
			String user,
			boolean simplifyMedia) {
		
		Response response = null;
		
		log.info("webForm:" + formIdent + " updateid:" + updateid + " user: " + user);

		Survey survey = null;
		StringBuffer outputString = new StringBuffer();
		
		// Authorisation 
		if(user != null) {
            a.isAuthorised(connectionSD, user);
    		SurveyManager sm = new SurveyManager();
    		survey = sm.getSurveyId(connectionSD, formIdent);	// Get the survey id from the templateName / key
    		if(survey == null) {
    			throw new NotFoundException();
    		}
    		a.isValidSurvey(connectionSD, user, survey.id, false);	// Validate that the user can access this survey
    		a.isBlocked(connectionSD, survey.id, false);			// Validate that the survey is not blocked
    		
        } else {
        	throw new AuthorisationException();
        }
		// End Authorisation
		
		// Get the data
		try {	    

			// Get the XML of the Form
			SurveyTemplate template = new SurveyTemplate();
			template.readDatabase(survey.id);
			
			//template.printModel();	// debug
			GetXForm xForm = new GetXForm();
			//String formXML = xForm.get(template);		
			
			// If required get the instance data 
			String instanceXML = null;
			String dataKey = "instanceid";
			
			System.out.println("Getting instance data");
			xForm = new GetXForm();
			instanceXML = xForm.getInstance(survey.id, formIdent, template, dataKey, updateid, 0, 
					simplifyMedia,
					false);
			
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

}

