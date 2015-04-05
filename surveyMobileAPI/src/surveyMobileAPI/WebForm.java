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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
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
import org.smap.sdal.Utilities.NotFoundException;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.managers.TranslationManager;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.Survey;
import org.smap.server.utilities.GetXForm;


/*
 * Return a survey as a webform
 */

@Path("/webForm/{key}")
public class WebForm extends Application{
	
	Authorise a = new Authorise(null, Authorise.ENUM);

	private static Logger log =
			 Logger.getLogger(WebForm.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(WebForm.class);
		return s;
	}

	
	// Respond with HTML no matter what is requested
	@GET
	@Produces(MediaType.TEXT_HTML)
	public Response getForm(@Context HttpServletRequest request,
			@PathParam("key") String formIdent,
			@QueryParam("datakey") String datakey,			// Optional keys to instance data	
			@QueryParam("datakeyvalue") String datakeyvalue
			) throws IOException {
		
		Response response;
		
		log.info("webForm:" + formIdent + " datakey:" + datakey + " datakeyvalue:" + datakeyvalue);
		
		// Authorisation - Access
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		}

		Survey survey = null;
		String user = request.getRemoteUser();
		int orgId = 0;
		String accessKey = null;
		
		if(user != null) {
			Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-FormXML");
            a.isAuthorised(connectionSD, user);
    		SurveyManager sm = new SurveyManager();
    		survey = sm.getSurveyId(connectionSD, formIdent);	// Get the survey id from the templateName / key
    		if(survey == null) {
    			throw new NotFoundException();
    		}
    		a.isValidSurvey(connectionSD, user, survey.id, false);	// Validate that the user can access this survey
    		a.isBlocked(connectionSD, survey.id, false);			// Validate that the survey is not blocked
    		
    		// Get the organisation id and an access key
    		try {
    			orgId = GeneralUtilityMethods.getOrganisationId(connectionSD, user);
    			accessKey = GeneralUtilityMethods.getNewAccessKey(connectionSD, user, formIdent);
    		} catch (Exception e) {
    			log.log(Level.SEVERE, "WebForm", e);
    		}
    		
    		try {
            	if (connectionSD != null) {
            		connectionSD.close();
            		connectionSD = null;
            	}
            } catch (SQLException e) {
            	log.log(Level.SEVERE, "Failed to close connection", e);
            }
        } else {
        	throw new AuthorisationException();
        }
		// End Authorisation

		
		StringBuffer outputHTML = new StringBuffer();
		
		// Generate the web form
		try {	    

			// Get the XML of the Form
			SurveyTemplate template = new SurveyTemplate();
			template.readDatabase(survey.id);
			
			//template.printModel();	// debug
			GetXForm xForm = new GetXForm();
			String formXML = xForm.get(template);		
			
			// If required get the instance data 
			String instanceXML = null;
			String dataToEditId = null;
			if(datakey != null && datakeyvalue != null) {
				xForm = new GetXForm();
				instanceXML = xForm.getInstance(survey.id, formIdent, template, datakey, datakeyvalue, 0);
				dataToEditId = xForm.getInstanceId();
			}
			
			/*
			 * Get the media manifest so we can set the url's of media files used the form
			 */
			String basePath = request.getServletContext().getInitParameter("au.com.smap.files");
			if(basePath == null) {
				basePath = "/smap";
			} else if(basePath.equals("/ebs1")) {		// Support for legacy apache virtual hosts
				basePath = "/ebs1/servers/" + request.getServerName();
			}
			TranslationManager translationMgr = new TranslationManager();	
			Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-FormXML");
			List<ManifestValue> manifestList = translationMgr.
					getManifestBySurvey(connectionSD, request.getRemoteUser(), survey.id, basePath, formIdent);
    		try {
            	if (connectionSD != null) {
            		connectionSD.close();
            		connectionSD = null;
            	}
            } catch (SQLException e) {
            	log.log(Level.SEVERE, "Failed to close connection", e);
            }
    		
    		for(int i = 0; i < manifestList.size(); i++) {
    			log.info(manifestList.get(i).fileName + " : " + manifestList.get(i).url + " : " + manifestList.get(i).type);
    			String type = manifestList.get(i).type;
    			String name = manifestList.get(i).fileName;
    			String url = manifestList.get(i).url;
    			if(url != null) {
	    			if(type.equals("image")) {
	    				type = "images";
	    			}
	    			formXML = formXML.replaceAll("jr://" + type + "/" + name, url);
    			}
    		}
			
			// Convert to HTML
			outputHTML.append(addDocument(request, formXML, instanceXML, dataToEditId, survey.surveyClass, orgId, accessKey));
			
			log.info("userevent: " + user + " : webForm : " + formIdent);	
			
			response = Response.status(Status.OK).entity(outputHTML.toString()).build();
		
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
			String surveyClass,
			int orgId,
			String accessKey) 
			throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
	
		StringBuffer output = new StringBuffer();
		
		output.append("<!DOCTYPE html>\n");
		
		output.append("<html lang='en'  class='no-js'");
		if(instanceXML == null) {
			// Single shot requests do not have a manifest
			// TODO add manifest
		}
		output.append(">\n");
				
		output.append(addHead(request, formXML, instanceXML, dataToEditId, surveyClass, accessKey));
		output.append(addBody(request, formXML, dataToEditId, orgId));

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
			String surveyClass,
			String accessKey) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		
		StringBuffer output = new StringBuffer();

		// head
		output.append("<head>\n");
		output.append("<link href='https://fonts.googleapis.com/css?family=Open+Sans:400,700,600&subset=latin,cyrillic-ext,cyrillic,greek-ext,greek,vietnamese,latin-ext' rel='stylesheet' type='text/css'>\n");

		output.append("<link type='text/css' href='/build/css/webform_smap.css' media='all' rel='stylesheet' />\n");
		output.append("<link type='text/css' href='/build/css/webform_formhub.css' media='all' rel='stylesheet' />\n");
		output.append("<link type='text/css' href='/build/css/webform_print_formhub.css' media='print' rel='stylesheet' />\n");
		if(surveyClass != null && surveyClass.trim().contains("theme-grid")) {
			output.append("<link type='text/css' href='/build/css/grid.css' media='all' rel='stylesheet' />\n");
			output.append("<link type='text/css' href='/build/css/grid-print.css' media='print' rel='stylesheet'/>\n");	
		} else {
			output.append("<link type='text/css' href='/build/css/default.css' media='all' rel='stylesheet' />\n");			
			output.append("<link type='text/css' href='/build/css/formhub.css' media='all' rel='stylesheet' />\n");			
			output.append("<link type='text/css' href='/build/css/formhub-print.css' media='print' rel='stylesheet'/>\n");
		}
			
		output.append("<link rel='shortcut icon' href='images/favicon.ico'>\n");
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
			
		output.append(addData(request, formXML, instanceXML, dataToEditId, accessKey));
		// Development
		//output.append("<script type='text/javascript' data-main='src/js/main-webform' src='/js/libs/require.js'></script>\n");
		//output.append("<script type='text/javascript' data-main='lib/enketo-core/app' src='/js/libs/require.js'></script>\n");
		
		// Production
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
			String accessKey) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		StringBuffer output = new StringBuffer();
		
		output.append("<script type='text/javascript'>\n");
		output.append("settings = {};\n");
		
		output.append("surveyData = {};\n");    
		
		// Data model
		
		output.append("surveyData.modelStr='");
		String dataDoc=transform(request, formXML, "/XSL/openrosa2xmlmodel.xsl").replace("\n", "").replace("\r", "");
	
		// We only want the model
		dataDoc = dataDoc.substring(dataDoc.indexOf("<model>"), dataDoc.lastIndexOf("</root>"));
		output.append(dataDoc.replace("\n", "").replace("\r", ""));
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
	 * Add the body
	 */
	private StringBuffer addBody(HttpServletRequest request, String formXML, String dataToEditId, int orgId) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		StringBuffer output = new StringBuffer();
		
		output.append("<body class='clearfix edit'>");

		output.append(getAside());
		output.append(openMain(orgId));		// TODO get orgId
		//log.info(transform(request, formXML, "/XSL/openrosa2html5form.xsl"));
		output.append(transform(request, formXML, "/XSL/openrosa2html5form.xsl"));
		output.append(closeMain(dataToEditId));
		output.append(getDialogs());
		
		output.append("</body>");
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
	
	private StringBuffer openMain(int orgId) {
		StringBuffer output = new StringBuffer();
		
		output.append("<div class='main'>\n");
			output.append("<article class='paper'>\n");
				output.append("<header class='form-header clearfix'>\n");
					output.append("<div class='offline-enabled'>\n");
						output.append("<div title='Records Queued' class='queue-length side-slider-toggle'>0</div>\n");
					output.append("</div>\n");
					output.append("<button onclick='return false;' class='print' title='Print this Form'> </button>\n");
					output.append("<span class='form-language-selector'><span>Choose Language</span></span>\n");
					output.append("<div class='form-progress'></div>\n");

					output.append("<span class='logo-wrapper'>\n");
						output.append("<img class='banner_logo' src='/media/organisation/");
						output.append(orgId);
						output.append("/settings/bannerLogo' onerror=\"if(this.src.indexOf('smap_logo.png') < 0) this.src='/images/smap_logo.png';\" alt='logo'>\n");
					output.append("</span>\n");

				output.append("</header>\n");
			
		return output;
	}
	
	private StringBuffer closeMain(String dataToEditId) {
		StringBuffer output = new StringBuffer();
		
		output.append("<section class='form-footer'>\n");
		output.append("<div class='content'>\n");
		output.append("<fieldset class='draft question'><div class='option-wrapper'><label class='select'><input class='ignore' type='checkbox' name='draft'/><span class='option-label'>Save as Draft</span></label></div></fieldset>\n");
		output.append("<div class='main-controls'>\n");
		output.append("<a class='previous-page disabled' href='#'>Back</a>\n");
		
		if(dataToEditId == null) {
			output.append("<button id='submit-form' class='btn btn-primary btn-large' >Submit</button>\n");
		} else {
			output.append("<button id='submit-form-single' class='btn btn-primary btn-large' >Submit</button>\n");
		}
		
		output.append("<a class='btn btn-primary large next-page' href='#'>Next</span></a>\n");
		
		output.append("</div>\n");	// main controls
		output.append("<a class='btn btn-default disabled first-page' href='#'>Return to Beginning</a>\n");
		output.append("<a class='btn btn-default disabled last-page' href='#'>Go to End</a>\n");
		output.append("</div>\n");	// content
		output.append("</section> <!-- end form-footer -->\n");
		
		output.append("</article>\n");
		output.append("</div> <!-- end main -->\n");

		return output;
	}

}

