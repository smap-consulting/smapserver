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

import org.smap.sdal.Utilities.Authorise;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.ResultsDataSource;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.PDFManager;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.LQAS;
import org.smap.sdal.model.LQASGroup;
import org.smap.sdal.model.LQASItem;

import utilities.XLSFormManager;
import utilities.XLS_LQAS_Manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itextpdf.tool.xml.ElementList;
import com.itextpdf.tool.xml.parser.XMLParser;

/*
 * Creates an LQAS report in XLS
 */

@Path("/lqasExport/{sId}")
public class ExportLQAS extends Application {
	
	Authorise a = new Authorise(null, Authorise.ANALYST);
	
	private static Logger log =
			 Logger.getLogger(ExportLQAS.class.getName());
	
	// Tell class loader about the root classes.  (needed as tomcat6 does not support servlet 3)
	/*
	public Set<Class<?>> getClasses() {
		Set<Class<?>> s = new HashSet<Class<?>>();
		s.add(Items.class);
		return s;
	}
	*/

	/*
	 * Assume:
	 *  1) LQAS surveys only have one form and this form is the one that has the "lot" question in it
	 */

	
	@GET
	@Produces("application/x-download")
	public Response getXLSFormService (@Context HttpServletRequest request, 
			@Context HttpServletResponse response,
			@PathParam("sId") int sId,
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
		a.isAuthorised(sd, request.getRemoteUser());		
		a.isValidSurvey(sd, request.getRemoteUser(), sId, false);
		// End Authorisation 
		
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
			survey = sm.getById(sd, cResults, request.getRemoteUser(), sId, false, basePath, null, false, false, false, false);
			
			/*
			 * Get the LQAS definition to apply to this survey
			 * Presumably this will be obtained from the database having been set up by the user
			 */
			LQAS lqas = new LQAS("sa");
			
			// Basic information group
			LQASGroup g = new LQASGroup("Basic Information");
			g.items.add(new LQASItem("1.a1", "Gender of Head of Household", "F", "Female","head_gender", "head_gender", new String[] {"head_gender"}));
			g.items.add(new LQASItem("1.a2", "Age of Head of Household", null, "#","head_age", "head_age", new String[] {"head_age"}));
			g.items.add(new LQASItem("1.b1", "Gender of person answering questions", "F", "Female","caregiver_gender","caregiver_gender", new String[] {"caregiver_gender"}));
			g.items.add(new LQASItem("1.b2", "Age of person answering questions", null, "#","caregiver_age","caregiver_age", new String[] {"caregiver_age"}));
			g.items.add(new LQASItem("2", "Is the child a boy or a girl", "F", "Female","child_gender","child_gender", new String[] {"child_gender"}));
			lqas.groups.add(g);
			
			// Feeding group
			g = new LQASGroup("Infant and young child feeding");
			g.items.add(new LQASItem("4", "Has child ever been breastfed", "1", "Yes","breastfeed","breastfeed", new String[] {"breastfeed"}));
			g.items.add(new LQASItem("5", "Are you still breast feeding", "1", "Yes","still_bf","still_bf", new String[] {"still_bf"}));
			g.items.add(new LQASItem("6a", "Child ate vitamin A-rich foods in the past 24 hours", "t", "Q12 B, D, E, G, H, I, J",
					"cereal = '1' or leafy = '1' or vita_fruits = '1' or organ = '1' or flesh = '1' or egg = '1' or fish = '1'", "ate_vit",
					new String[] {"cereal", "leafy", "vita_fruits", "organ", "flesh", "egg", "fish"}));
			g.items.add(new LQASItem("6b", "Child ate iron-rich foods in the past 24 hours", "t", "Q12 G, H, I, J or P",
					"organ = '1' or flesh = '1' or egg = '1' or fish = '1' or insect = '1'", "ate_iron",
					new String[] {"organ", "flesh", "egg", "fish", "insect"}));
			g.items.add(new LQASItem("6c", "Child ate animal source foods in the past 24 hours", "t", "Q12 G, H, I, J, L or P",
					"organ = '1' or flesh = '1' or egg = '1' or fish = '1' or dairy = '1' or insect = '1'", "ate_iron",
					new String[] {"organ", "flesh", "egg", "fish", "dairy", "insect"}));
			g.items.add(new LQASItem("6d", "Child had food from at least 4 food groups during the previous day and night", 
					"t", "",
					"case when cereal = '1' then 1 else 0 end + case when vita = '1' then 1 else 0 end"
					+ " + case when tubers = '1' then 1 else 0 end + case when leafy = '1' then 1 else 0 end"
					+ " + case when vita_fruits = '1' then 1 else 0 end + case when fruits = '1' then 1 else 0 end"
					+ " + case when organ = '1' then 1 else 0 end + case when flesh = '1' then 1 else 0 end"
					+ " + case when egg = '1' then 1 else 0 end + case when fish = '1' then 1 else 0 end"
					+ " + case when nuts = '1' then 1 else 0 end + case when dairy = '1' then 1 else 0 end"
					+ " + case when oil = '1' then 1 else 0 end + case when sweet = '1' then 1 else 0 end"
					+ " + case when solid = '1' then 1 else 0 end + case when insect = '1' then 1 else 0 end >= 4", 
					"food_groups_4",
					new String[] {"cereal", "vita", "tubers", "leafy", "vita_fruits", "fruits", "organ", "flesh", "egg", "fish", "nuts", 
							"dairy", "oil", "sweet", "solid", "insect"}));
			lqas.groups.add(g);
			
			/*
			 * End of setting up of test definition
			 */
			
			// Set file name
			GeneralUtilityMethods.setFilenameInResponse(survey.displayName + "." + filetype, response);
			
			// Create XLSForm
			XLS_LQAS_Manager xf = new XLS_LQAS_Manager(filetype);
			xf.createLQASForm(sd, cResults, response.getOutputStream(), survey, lqas, showSources);
			
		}  catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			throw new Exception("Exception: " + e.getMessage());
		} finally {
			
			try {
				if (sd != null) {
					sd.close();
					sd = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
			try {
				if (cResults != null) {
					cResults.close();
					cResults = null;
				}
				
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}
		return Response.ok("").build();
	}
	

}
