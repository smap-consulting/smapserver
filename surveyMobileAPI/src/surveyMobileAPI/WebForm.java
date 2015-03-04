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
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.AuthorisationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Survey;
import org.smap.server.utilities.GetXForm;


/*
 * Return a survey as a webform
 */

@Path("/webForm")
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
			@QueryParam("key") String formIdent) throws IOException {
		
		Response response;
		
		log.info("webForm:" + formIdent);
		
		// Authorisation - Access
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		}

		Survey survey = null;
		String user = request.getRemoteUser();
		
		if(user != null) {
			Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-FormXML");
            a.isAuthorised(connectionSD, user);
    		SurveyManager sm = new SurveyManager();
    		survey = sm.getSurveyId(connectionSD, formIdent);	// Get the survey id from the templateName / key
    		a.isValidSurvey(connectionSD, user, survey.id, false);	// Validate that the user can access this survey
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
		
		// Extract the data
		try {	    

			SurveyTemplate template = new SurveyTemplate();
			template.readDatabase(survey.id);
			//template.printModel();	// debug
			GetXForm xForm = new GetXForm();
			String formXML = xForm.get(template);		
			
			// Get the HTML
			outputHTML.append(addDocument(request, formXML));
			
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
	private StringBuffer addDocument(HttpServletRequest request, String formXML) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
	
		StringBuffer output = new StringBuffer();
		
		output.append("<!DOCTYPE html>");
		
		output.append("<html lang='en'  class='no-js'");	
		// TODO add manifest
		output.append(">");
				
		output.append(addHead());
		output.append(addBody(request, formXML));

		output.append("</html>");			
		return output;
	}
	
	/*
	 * Add the head section
	 */
	private String addHead() {
		
		StringBuffer output = new StringBuffer();

		// head
		output.append("<head>");
		output.append("<link href='http://fonts.googleapis.com/css?family=Open+Sans:400,700,600&subset=latin,cyrillic-ext,cyrillic,greek-ext,greek,vietnamese,latin-ext' rel='stylesheet' type='text/css'>");

		// TODO add css depending on setting of grid style
		output.append("<link type='text/css' href='/css/libs/grid.css' media='all' rel='stylesheet' />");			
		output.append("<link type='text/css' href='/css/libs/webform_formhub.css' media='all' rel='stylesheet' />");			
		output.append("<link type='text/css' href='/css/libs/webform_print_formhub.css' media='print' rel='stylesheet' />");
			
		output.append("<link rel='shortcut icon' href='images/favicon.ico'>");
		//	<!-- For third-generation iPad with high-resolution Retina display: -->
		output.append("<link rel='apple-touch-icon-precomposed' sizes='144x144' href='images/fieldTask_144_144_min.png'>");
		//	<!-- For iPhone with high-resolution Retina display: -->
		output.append("<link rel='apple-touch-icon-precomposed' sizes='114x114' href='images/fieldTask_114_114_min.png'>");
		//	<!-- For first- and second-generation iPad: -->
		output.append("<link rel='apple-touch-icon-precomposed' sizes='72x72' href='images/fieldTask_72_72_min.png'>");
		//	<!-- For non-Retina iPhone, iPod Touch, and Android 2.1+ devices: -->
		output.append("<link rel='apple-touch-icon-precomposed' href='images/fieldTask_57_57_min.png'>");
			
		output.append("<meta charset='utf-8' />");
		output.append("<meta name='viewport' content='width=device-width, initial-scale=1.0' />");
		output.append("<meta name='apple-mobile-web-app-capable' content='yes' />");
		output.append("<!--[if lt IE 10]>");
	    output.append("<script type='text/javascript'>window.location = 'modern_browsers';</script>");
		output.append("<![endif]-->");
			
		output.append("<script type='text/javascript' src='/js/libs/enketo-combined.min.js'></script>");
		
		// TODO add Data
		output.append("</head>");
		
		return output.toString();		
		
	}
	
	/*
	 * Add the body
	 */
	private StringBuffer addBody(HttpServletRequest request, String formXML) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		StringBuffer output = new StringBuffer();
		
		output.append("<body class='clearfix edit'>");

		output.append(getAside());
		output.append(openMain());
		output.append(getSurveyHTML(request, formXML));
		output.append(closeMain());
		output.append(getDialogs());
		
		output.append("</body>");
		return output;
	}
	
	/*
	 * Transform the XML defintion into HTML using enketo style sheets
	 */
	private String getSurveyHTML(HttpServletRequest request, String formXML) throws UnsupportedEncodingException, TransformerFactoryConfigurationError, TransformerException {
		
		InputStream xmlStream = new ByteArrayInputStream(formXML.getBytes("UTF-8"));
		StreamSource source = new StreamSource(xmlStream);
		
		StringWriter writer = new StringWriter();
		StreamResult output = new StreamResult(writer);
		
		InputStream st = request.getServletContext().getResourceAsStream("/XSL/openrosa2html5form.xsl");
		StreamSource styleSource = new StreamSource(request.getServletContext().getResourceAsStream("/XSL/openrosa2html5form.xsl"));
		
		//System.out.println("source:" + IOUtils.toString(xmlStream));
		//System.out.println("style:" + IOUtils.toString(st));

		Transformer transformer = TransformerFactory.newInstance().newTransformer(styleSource);
    	transformer.setOutputProperty(OutputKeys.INDENT, "yes");
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
		
		output.append("<div id='feedback-bar' class='alert alert-warning'>");		
			output.append("<span class='glyphicon glyphicon-info-sign'></span>");	
			output.append("<button class='close'>&times;</button>");	
		output.append("</div>");	

		output.append("<div id='dialog-alert' class='modal fade' role='dialog' aria-labelledby='alert dialog' aria-hidden='true'  data-keyboard='true'>");	
			output.append("<div class='modal-dialog'>");	
				output.append("<div class='modal-content'>");	
					output.append("<div class='modal-header'>");	
						output.append("<button type='button' class='close' data-dismiss='modal' aria-hidden='true'>&times;</button>");	
						output.append("<h3></h3>");	
					output.append("</div>");	
					output.append("<div class='modal-body'>");	
						output.append("<p class=''></p>");	
					output.append("</div>");	
					output.append("<div class='modal-footer'>");	
						output.append("<span class='self-destruct-timer'></span>");	
						output.append("<button class='btn' data-dismiss='modal' aria-hidden='true'>Close</button>");	
					output.append("</div>");	
				output.append("</div>");	
			output.append("</div>");	
		output.append("</div>");	
	
		output.append("<div id='dialog-confirm' class='modal fade' role='dialog' aria-labelledby='confirmation dialog' aria-hidden='true'  data-keyboard='true'>");	
			output.append("<div class='modal-dialog'>");	
				output.append("<div class='modal-content'>");	
					output.append("<div class='modal-header'>");	
						output.append("<button type='button' class='close' data-dismiss='modal' aria-hidden='true'>&times;</button>");	
						output.append("<h3></h3>");	
					output.append("</div>");	
					output.append("<div class='modal-body'>");	
						output.append("<p class='alert alert-danger'></p>");	
						output.append("<p class='msg'></p>");	
					output.append("</div>");	
					output.append("<div class='modal-footer'>");	
						output.append("<span class='self-destruct-timer'></span>");	
						output.append("<button class='negative btn'>Close</button>");	
						output.append("<button class='positive btn btn-primary'>Confirm</button>");	
					output.append("</div>");	
				output.append("</div>");	
			output.append("</div>");	
		output.append("</div>");	
	
		output.append("<div id='dialog-save' class='modal fade' role='dialog' aria-labelledby='save dialog' aria-hidden='true' data-keyboard='true'>");	
			output.append("<div class='modal-dialog'>");	
				output.append("<div class='modal-content'>");	
					output.append("<div class='modal-header'>");	
						output.append("<button type='button' class='close' data-dismiss='modal' aria-hidden='true'>&times;</button>");	
						output.append("<h3></h3>");	
					output.append("</div>");	
					output.append("<div class='modal-body'>");	
						output.append("<form onsubmit='return false;'>");	
							output.append("<div class='alert alert-danger'></div>");	
							output.append("<label>");	
								output.append("<span>Record Name</span>");	
								output.append("<span class='or-hint active'>This name allows you to easily find your draft record to finish it later. The record name will not be submitted to the server.</span>");	
								output.append("<input name='record-name' type='text' required='required'/>");	
							output.append("</label>");	
						output.append("</form>");	
					output.append("</div>");	
					output.append("<div class='modal-footer'>");	
						output.append("<button class='negative btn'>Close</button>");	
						output.append("<button class='positive btn btn-primary'>Save &amp; Close</button>");	
					output.append("</div>");	
				output.append("</div>");	
			output.append("</div>");	
		output.append("</div>");	
	
		//used for Grid theme only
		output.append("<div id='dialog-print' class='modal fade' role='dialog' aria-labelledby='print dialog' aria-hidden='true'  data-keyboard='true'>");	
			output.append("<div class='modal-dialog'>");	
				output.append("<div class='modal-content'>");	
					output.append("<div class='modal-header'>");	
						output.append("<button type='button' class='close' data-dismiss='modal' aria-hidden='true'>&times;</button>");	
						output.append("<h3 class='select-format'>Select Print Settings</h3>");	
						output.append("<h3 class='progress'>Preparing...</h3>");	
					output.append("</div>");	
					output.append("<div class='modal-body'>");	
						output.append("<section class='progress'>");	
							output.append("<p>Working hard to prepare your optimized print view. Hold on!</p>");	
							output.append("<progress></progress>");	
						output.append("</section>");	
						output.append("<section class='select-format'>");	
							output.append("<p>To prepare an optimized print, please select the print settings below</p>");	
							output.append("<form onsubmit='return false;'>");	
								output.append("<fieldset>");	
									output.append("<legend>Paper Size</legend>");	
									output.append("<label>");	
										output.append("<input name='format' type='radio' value='A4' required checked/>");	
										output.append("<span>A4</span>");	
									output.append("</label>");	
									output.append("<label>");	
										output.append("<input name='format' type='radio' value='letter' required/>");	
										output.append("<span>Letter</span>");	
									output.append("</label>");	
								output.append("</fieldset>");	
								output.append("<fieldset>");	
									output.append("<legend>Paper Orientation</legend>");	
									output.append("<label>");	
										output.append("<input name='orientation' type='radio' value='portrait' required checked/>");	
										output.append("<span>Portrait</span>");	
									output.append("</label>");	
									output.append("<label>");	
										output.append("<input name='orientation' type='radio' value='landscape' required/>");	
										output.append("<span>Landscape</span>");	
									output.append("</label>");	
								output.append("</fieldset>");	
							output.append("</form>");	
							output.append("<p class='alert alert-info'>Remember to set these same print settings in the browser's print menu afterwards!</p>");	
						output.append("</section>");	
					output.append("</div>");	
					output.append("<div class='modal-footer'>");	
						output.append("<button class='negative btn'>Close</button>");	
						output.append("<button class='positive btn btn-primary'>Prepare</button>");	
					output.append("</div>");	
				output.append("</div>");	
			output.append("</div>");	
		output.append("</div>");	
		
		return output;
	}
	
	private StringBuffer getAside() {
		
		StringBuffer output = new StringBuffer();
			
		output.append("<aside class='side-slider'>");
			output.append("<button type='button' class='close' data-dismiss='side-slider' aria-hidden='true'>×</button>");
			output.append("<nav></nav>");
			output.append("<div class='content'>");
			output.append("</div>");
		output.append("</aside>");

		output.append("<button class='handle side-slider-toggle open'></button>");
		output.append("<button class='handle side-slider-toggle close'></button>");
		output.append("<div class='side-slider-toggle slider-overlay'></div>");
	
		return output;
	}
	
	private StringBuffer openMain() {
		StringBuffer output = new StringBuffer();
		
		output.append("<div class='main'>");
			output.append("<article class='paper'>");
				output.append("<header class='form-header clearfix'>");
					output.append("<div class='offline-enabled'>");
						output.append("<div title='Records Queued' class='queue-length side-slider-toggle'>0</div>");
					output.append("</div>");
					output.append("<button onclick='return false;' class='print' title='Print this Form'> </button>");
					output.append("<span class='form-language-selector'><span>Choose Language</span></span>");
					output.append("<div class='form-progress'></div>");
					output.append("<a href='http://www.smap.com.au/' title='Smap'>");
						output.append("<div class='logo-wrapper'>");
							output.append("<img src='images/smap_logo.png' alt='logo'>");
						output.append("</div>");
					output.append("</a>");
				output.append("</header>");
			
		return output;
	}
	
	private StringBuffer closeMain() {
		StringBuffer output = new StringBuffer();
		
		output.append("<section class='form-footer'>");
		output.append("<div class='content'>");
		output.append("<fieldset class='draft question'><div class='option-wrapper'><label class='select'><input class='ignore' type='checkbox' name='draft'/><span class='option-label'>Save as Draft</span></label></div></fieldset>");
		output.append("<div class='main-controls'>");
		output.append("<a class='previous-page disabled' href='#'>Back</a>");
		
		/*
		 * TODO Submit button logic
		 *
		if(empty($data_to_edit_id)) {
			echo "<button id='submit-form' class='btn btn-primary btn-large' >Submit</button>";
		} else {
			echo "<button id='submit-form-single' class='btn btn-primary btn-large' >Submit</button>";
		}
		*/
		output.append("<a class='btn btn-primary large next-page' href='#'>Next</span></a>");
		
		output.append("</div>");	// main controls
		output.append("<a class='btn btn-default disabled first-page' href='#'>Return to Beginning</a>");
		output.append("<a class='btn btn-default disabled last-page' href='#'>Go to End</a>");
		output.append("</div");	// content
		output.append("</article>");

		return output;
	}

}

