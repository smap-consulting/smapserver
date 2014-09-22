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
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.smap.model.SurveyTemplate;
import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Survey;
import org.smap.server.entities.Form;
import org.smap.server.entities.Option;
import org.smap.server.entities.Question;
import org.smap.server.utilities.UtilityMethods;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


/*
 * Get instance data
 * Output is in JavaRosa compliant XForms results file
 */

@Path("/instanceXML")
public class InstanceXML extends Application{

	Authorise a = new Authorise(Authorise.ENUM);
	
	private static Logger log =
			 Logger.getLogger(InstanceXML.class.getName());
	
	class Results {
		public String name;
		public Form subForm;	// Non null if this results item is a sub form
		public String value;
		public boolean begin_group;
		public boolean end_group;
		
		public Results (String n, Form f, String v, boolean bg, boolean eg) {
			name = n;
			subForm = f;
			value = v;
			begin_group = bg;
			end_group = eg;
		}
	}
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(InstanceXML.class);
		return s;
	}


	/*
	 * Parameters
	 *  sName : Survey Name
	 *  prikey : Primary key of data record in the top level table of this survey
	 *  instructions on whether to preserve or replace each record
	 */
	@GET
	@Path("/{sName}/{priKey}")
	@Produces(MediaType.TEXT_XML)
	public Response getInstance(@Context HttpServletRequest request,
			@PathParam("sName") String templateName,
			@PathParam("priKey") int priKey,
			@QueryParam("user") String userId,
			@QueryParam("key") String key,		// Optional
			@QueryParam("keyval") String keyval	// Optional
			) throws IOException {

		Response response = null;
		
		log.info("instanceXML: Survey=" + templateName + " priKey=" + priKey);
		
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			String msg = "Error: Can't find PostgreSQL JDBC Driver";
			log.log(Level.SEVERE, msg, e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		    return response;
		}
		
		// Authorisation - Access
		try {
		    Class.forName("org.postgresql.Driver");	 
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Can't find PostgreSQL JDBC Driver", e);
		}
		
		// If the user did not logon then get the user id from the query parameter
		// Note the server should be configured so that access without logging on is only possible
		//  from the local server
		String user = request.getRemoteUser();
		if(user == null) {
		    user = userId;
		} 
		
		Connection connectionSD = SDDataSource.getConnection("surveyMobileAPI-InstanceXML");
		SurveyManager sm = new SurveyManager();
		Survey survey = sm.getSurveyId(connectionSD, templateName);	// Get the survey id from the templateName / key
		a.isAuthorised(connectionSD, user);
		a.isValidSurvey(connectionSD, user, survey.id, false);	// Validate that the user can access this survey
		a.isBlocked(connectionSD, survey.id, false);			// Validate that the survey is not blocked
		try {
			if (connectionSD != null) {
				connectionSD.close();
				connectionSD = null;
			}
		} catch (SQLException e) {
			log.log(Level.SEVERE, "Failed to close connection", e);
		}
		// End Authorisation
		
		Connection connection = null; 
		 
		// Extract the data
		try {
			
    		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    		DocumentBuilder b = dbf.newDocumentBuilder();    		
    		Document outputXML = b.newDocument(); 
    		
           	Writer outWriter = new StringWriter();
           	Result outStream = new StreamResult(outWriter);

           	// Get template details
           	SurveyTemplate template = new SurveyTemplate();
			template.readDatabase(survey.id);
			String firstFormRef = template.getFirstFormRef();
			System.out.println("First form ref: " + firstFormRef);
			Form firstForm = template.getForm(firstFormRef);
			
			// Get database driver and connection to the results database
			Class.forName("org.postgresql.Driver");			
			connection = ResultsDataSource.getConnection("surveyMobileAPI-InstanceXML");
		 
			/*
			 * Replace the primary key with the primary key of the record that matches the passed in key and key value
			 */
			if(key != null && keyval != null) { 
				if(key.equals("prikey")) {
					priKey = Integer.parseInt(keyval);
					if(!priKeyValid(connection, firstForm, priKey)) {
						priKey = 0;
					}
				} else {
					priKey = getPrimaryKey(connection, firstForm, key, keyval);
				}
			} else {
				if(!priKeyValid(connection, firstForm, priKey)) {
					priKey = 0;
				}
			}
			
			// Generate the XML
			boolean hasData = false;
			if(priKey > 0) {
				hasData = true;
				populateForm(outputXML, firstForm, priKey, -1, connection, template, null, survey.id, templateName);    
			} else if(key != null && keyval != null)  {
				// Create a blank form containing only the key values
				hasData = true;
				System.out.println("Outputting blank form");
				populateBlankForm(outputXML, firstForm, connection,  template, null, survey.id, key, keyval, templateName);
			} 
			
	   		// Write the survey to a string and return it to the calling program
			if(hasData) {
	        	Transformer transformer = TransformerFactory.newInstance().newTransformer();
	        	transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	        	DOMSource source = new DOMSource(outputXML);
	        	transformer.transform(source, outStream);
			
	        	response = Response.ok(outWriter.toString()).build();
			} else {
				response = Response.ok("").build();
			}
			
		
		} catch (SQLException e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} catch (ApplicationException e) {
		    String msg = e.getMessage();	
			log.info(msg);	
			response = Response.status(Status.NOT_FOUND).entity(msg).build();
		}  catch (Exception e) {
			log.log(Level.SEVERE,"Exception", e);
			response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
		} finally {
			try {
				if (connection != null) {
					connection.close();
					connection = null;
				}
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
		}
				
		return response;
	}
	
	/*
	 * Get the primary key from the passed in key values
	 *  The key must be in the top level form
	 */
	int getPrimaryKey(Connection connection, Form firstForm, String key, String keyval) throws ApplicationException, SQLException {
		int prikey = 0;
		String table = firstForm.getTableName().replace("'", "''");	// Escape apostrophes
		key = key.replace("'", "''");	// Escape apostrophes
		String type = null;
		
		// Get the key type
		List<Question> questions = firstForm.getQuestions();
		for(int i = 0; i < questions.size(); i++) {
			Question q = questions.get(i);
			if(q.getName().equals(key)) {
				type = q.getType();
				break;
			}
		}
		if(type == null) {
			throw new ApplicationException("Key: " + key + " not found");
		}
		String sql = "select prikey from " + table + " where " + key + " = ? " +
				"and (_bad = false or (_bad = true and _bad_reason not like 'Replaced by%'));";
		System.out.println("Getting primary key: " + sql + " : " + keyval);
		PreparedStatement pstmt = connection.prepareStatement(sql);
		if(type.equals("string") || type.equals("barcode")) {
			pstmt.setString(1, keyval);
		} else if (type.equals("int")) {
			pstmt.setInt(1, Integer.parseInt(keyval));
		} else if (type.equals("decimal")) {
			pstmt.setFloat(1, Float.parseFloat(keyval));
		} else {
			throw new ApplicationException("Invalid question type: " + type + ", allowed values are text, barcode, integer, decimal");
		}
		
		ResultSet rs = null;
		boolean hasKey;
		try {
			rs = pstmt.executeQuery();
			hasKey = rs.next();
		} catch (Exception e) {
			String msg = e.getMessage();
			if(msg.contains("does not exist")) {
				// Presumably no data has been uploaded yet - therefore no key but not an error
			} else {			
				e.printStackTrace();
			}
			hasKey = false;	
			//throw new ApplicationException("Record not found for key: " + key + " and value " + keyval);
		}
		if(hasKey) {
			prikey = rs.getInt(1);
			if(rs.next()) {
				throw new ApplicationException("Multiple records found for key: " + key + " and value " + keyval);
			}

		} else {
			log.info("Key " + key + " and value " + keyval + " not found");
			//throw new ApplicationException("Record not found for key: " + key + " and value " + keyval);
		}

		return prikey;
	}
	
	/*
	 * Make sure the primary key is valid and can return data
	 */
	boolean priKeyValid(Connection connection, Form firstForm,  int priKey) throws ApplicationException, SQLException {
		
		String table = firstForm.getTableName().replace("'", "''");	// Escape apostrophes
		boolean isValid = false;
	
		String sql = "select count(*) from " + table + " where prikey = ? " +
				"and (_bad = false or (_bad = true and _bad_reason not like 'Replaced by%'));";
		System.out.println("Checking primary key: " + sql + " : " + priKey);
		PreparedStatement pstmt = connection.prepareStatement(sql);
		pstmt.setInt(1, priKey);
		
		ResultSet rs = null;
		try {
			rs = pstmt.executeQuery();
			if(rs.next()) {
				if(rs.getInt(1) > 0) {
					isValid = true;
				}
			}

		} catch (Exception e) {
			String msg = e.getMessage();
			if(msg.contains("does not exist")) {
				// Presumably no data has been uploaded yet - therefore no key but not an error
			} else {			
				e.printStackTrace();
			}
		}
	

		return isValid;
	}
	
	/*
     * Add the data for this form
     * @param outputDoc
     */
    public void populateForm(Document outputDoc, Form form, int id, int parentId, 
    		Connection connection,
    		SurveyTemplate template,
    		Element parentElement,
    		int sId,
    		String survey_ident) throws SQLException {
	
		List<List<Results>> results = getResults(form, id, parentId, connection, template);  // Add the child elements
    	
		// For each record returned from the database add a form element
    	for(int i = 0; i < results.size(); i++) {
    		
        	Element currentParent = outputDoc.createElement(form.getName());   // Create a form element
    		List<Results> record = results.get(i);
  
    		Results priKey = record.get(0);	// Get the primary key
    		
    		/*
    		 * Add data for the remaining questions
    		 */
    		Results item = null;
	       	Stack<Element> elementStack = new Stack<Element>();	// Store the elements for non repeat groups
    		for(int j = 1; j < record.size(); j++){

    			item= record.get(j);   			

    			if(item.subForm != null) {
    				populateForm(outputDoc, item.subForm, -1, 
    						Integer.parseInt(priKey.value), connection, template, currentParent, sId, survey_ident);
    			} else if (item.begin_group) { 
    				Element childElement = null;
    				childElement = outputDoc.createElement(item.name);
        			currentParent.appendChild(childElement);
        			
        			elementStack.push(currentParent);
					currentParent = childElement;
					
    			} else if (item.end_group) { 
    				
    				currentParent = elementStack.pop();
    				
    			} else {  // Question
    				
    				// Set some default values for task management questions
    				if(item.name != null && item.name.equals("_task_key")) {
    					item.value = priKey.value;
    				} 
    				
    				// Create the question element
        			Element childElement = null;
    				childElement = outputDoc.createElement(item.name);
    				childElement.setTextContent(item.value);
        			currentParent.appendChild(childElement);
    			}

    		}
    		// Append this new form to its parent (if the parent is null append to output doc)
    		if(parentElement != null) {
    			parentElement.appendChild(currentParent);
    		} else {
    			currentParent.setAttribute("id", survey_ident);
    			outputDoc.appendChild(currentParent);
    		}
    	}
    	    	
    }
    
	/*
     * Create a blank form populated only by the key data
     * @param outputDoc
     */
    public void populateBlankForm(Document outputDoc, Form form, Connection connection, SurveyTemplate template,
       		Element parentElement,
    		int sId,
    		String key,
    		String keyval,
    		String survey_ident) throws SQLException {
	
 		List<Results> record = new ArrayList<Results> ();
 		
    	List<Question> questions = form.getQuestions();
		for(Question q : questions) {
			
			String qName = q.getName();
			String qType = q.getType(); 
			
			String value = "";
			if(qName.equals(key)) {
				value = keyval;
			} 
			
			System.out.println("Qtype: " + qType + " qName: " + qName);
			if(qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {	
			
				Form subForm = template.getSubForm(form, q);
    			
    			if(subForm != null) {	
            		record.add(new Results(qName, subForm, null, false, false));
    			}
    			
    		} else if(qType.equals("begin group")) { 
    			
    			record.add(new Results(qName, null, null, true, false));
    			
    		} else if(qType.equals("end group")) { 
    			
    			record.add(new Results(qName, null, null, false, true));
    			
    		} else {

        		record.add(new Results(qName, null, value, false, false));
			}
		}
    	
		
        Element currentParent = outputDoc.createElement(form.getName());   // Create a form element

    	Results item = null;
	    Stack<Element> elementStack = new Stack<Element>();	// Store the elements for non repeat groups
    	for(int j = 0; j < record.size(); j++){

    		item= record.get(j);   			

    		if(item.subForm != null) {
				populateBlankForm(outputDoc, item.subForm, connection, template, currentParent, sId, key, keyval, survey_ident);				
    		} else if (item.begin_group) { 
    			System.out.println("Begin group: " + item.name);
    			Element childElement = null;
    			childElement = outputDoc.createElement(item.name);
        		currentParent.appendChild(childElement);
        			
        		elementStack.push(currentParent);
				currentParent = childElement;
					
    		} else if (item.end_group) { 
    				
    			System.out.println("End group: " + item.name);
    			currentParent = elementStack.pop();
    				
    		} else {  // Question
    				
				// Create the question element
    			System.out.println("Question: " + item.name);
    			Element childElement = null;
				childElement = outputDoc.createElement(item.name);
				childElement.setTextContent(item.value);
    			currentParent.appendChild(childElement);
			}
    		
    	}
   		
		// Append this new form to its parent (if the parent is null append to output doc)
		if(parentElement != null) {
			parentElement.appendChild(currentParent);
		} else {
			currentParent.setAttribute("id", survey_ident);
			outputDoc.appendChild(currentParent);
		}
    	
    }
    
    /*
     * Get the results
     * @param form
     * @param id
     * @param parentId
     */
    List<List<Results>> getResults(Form form, int id, int parentId, Connection connection,
    		SurveyTemplate template) throws SQLException{
 
    	List<List<Results>> output = new ArrayList<List<Results>> ();
    	
    	/*
    	 * Retrieve the results record from the database (excluding select questions)
    	 *  Select questions are retrieved using a separate query as there are multiple 
    	 *  columns per question
    	 */
    	
    	String sql = "select prikey";
    	List<Question> questions = form.getQuestions();
    	for(Question q : questions) {
    		String col = null;
    				
    		if(template.getSubForm(form, q) == null) {
    			// This question is not a place holder for a subform
    			if(q.getSource() != null) {		// Ignore questions with no source, these can only be dummy questions that indicate the position of a subform
		    		String qType = q.getType();
		    		log.fine("    QType:" + qType );
		    		if(qType.equals("geopoint")) {
		    			col = "ST_AsText(" + q.getName() + ")";
		    		} else if(qType.equals("select")){
		    			continue;	// Select data columns are retrieved separately as there are multiple columns per question
		    		} else {
		    			col = q.getName();
		    		}
		
		    		sql += "," + col;
    			}
    		}

    	}
    	sql += " from " + form.getTableName();
    	if(id != -1) {
    		sql += " where prikey=" + id + ";";
    	} else {
    		sql += " where parkey=" + parentId + ";";
    	}
    	log.info(sql);

    	PreparedStatement pstmt = connection.prepareStatement(sql);	 			
    	ResultSet resultSet = pstmt.executeQuery();
		
    	// For each record returned from the database add the data values to the instance
    	while(resultSet.next()) {
    		
    		List<Results> record = new ArrayList<Results> ();
    		
    		String priKey = resultSet.getString(1);
    		record.add(new Results("prikey", null, priKey, false, false));
    		
    		/*
    		 * Add data for the remaining questions
    		 */
    		int index = 2;
    		
    		for(Question q : questions) {
    			
    			String qName = q.getName();
				String qType = q.getType(); 
				String qPath = q.getPath();
				String qSource = q.getSource();
				
    			if(qType.equals("begin repeat") || qType.equals("geolinestring") || qType.equals("geopolygon")) {	
    				System.out.println("Repeat: " + qName + " : " + qType);
	    			Form subForm = template.getSubForm(form, q);
	    			
	    			if(subForm != null) {	
	            		record.add(new Results(qName, subForm, null, false, false));
	    			}
	    			
	    		} else if(qType.equals("begin group")) { 
	    			
	    			System.out.println("Begin group: " + qName + " : " + qType);
	    			record.add(new Results(qName, null, null, true, false));
	    			
	    		} else if(qType.equals("end group")) { 
	    			
	    			System.out.println("End group: " + qName + " : " + qType);
	    			record.add(new Results(qName, null, null, false, true));
	    			
	    		} else if(qType.equals("select")) {		// Get the data from all the option columns
	    				
	    			System.out.println("Select: " + qName + " : " + qType);
					String sqlSelect = "select ";
					List<Option> options = new ArrayList<Option>(q.getChoices());
					UtilityMethods.sortOptions(options);	// Order within an XForm is not actually required, this is just for consistency of reading

					boolean hasColumns = false;
					for(Option option : options) {
						if(hasColumns) {
							sqlSelect += ",";
						}
						sqlSelect += q.getName() + "__" + option.getValue();
						hasColumns = true;
					}
					sqlSelect += " from " + form.getTableName() + " where prikey=" + priKey + ";";
			    	log.info(sqlSelect);
			    	
			    	pstmt = connection.prepareStatement(sqlSelect);	 			
			    	ResultSet resultSetOptions = pstmt.executeQuery();
			    	resultSetOptions.next();		// There will only be one record
		    		
			    	String optValue = "";
			    	hasColumns = false;
			    	for(Option option : options) {
			    		String opt = q.getName() + "__" + option.getValue();
			    		boolean optSet = resultSetOptions.getBoolean(opt);
			    		log.fine("Option " + opt + ":" + resultSetOptions.getString(opt));
			    		if(optSet) {
				    		if(hasColumns) {
				    			optValue += " ";
				    		}
				    		optValue += option.getValue(); 
				    		hasColumns = true;
			    		}
					}
			    	
	        		record.add(new Results(UtilityMethods.getLastFromPath(qPath), null, optValue, false, false));
				
    			} else if(qSource != null) {
  
    				System.out.println("Other: " + qName + " : " + qType);
    				String value = resultSet.getString(index);
    				log.info("     value: " + value);

    				
    				if(value != null && qType.equals("geopoint")) {
    					int idx1 = value.indexOf('(');
    					int idx2 = value.indexOf(')');
    					if(idx1 > 0 && (idx2 > idx1)) {
	    					value = value.substring(idx1 + 1, idx2 );
	    					// These values are in the order longitude latitude.  This needs to be reversed for the XForm
	    					String [] coords = value.split(" ");
	    					if(coords.length > 1) {
	    						value = coords[1] + " " + coords[0] + " 0 0";
	    					}
    					} else {
    						log.severe("Invalid value for geopoint");
    						value = null;
    					}
    				}
    				
    				// Ignore data not provided by user
    				if(!qSource.equals("user")) {	
    					value="";
    				}

            		record.add(new Results(UtilityMethods.getLastFromPath(qPath), null, value, false, false));

	    			index++;
    			}
    			
    		}
    		output.add(record);
    	}   	
    	
		return output;
    }
 
}

