package managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import model.DataEndPoint;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Survey;

public class DataManager {
	
	private static Logger log =
			 Logger.getLogger(DataManager.class.getName());

	public ArrayList<DataEndPoint> getDataEndPoints(Connection sd, 
			HttpServletRequest request,
			boolean csv) throws SQLException {
		
		ArrayList<DataEndPoint> data = new ArrayList<DataEndPoint> ();
		
		/*
		 * Use existing survey manager call to get a list of surveys that the user can access
		 */
		ArrayList<Survey> surveys = null;	
		SurveyManager sm = new SurveyManager();
		boolean superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
		surveys = sm.getSurveysAndForms(sd, request.getRemoteUser(), superUser);
		
		String urlprefix = request.getScheme() + "://" + request.getServerName();
		if(csv) {
			urlprefix += "/api/v1/data.csv/";	
		} else {
			urlprefix += "/api/v1/data/";
		}
		
		for(Survey s: surveys) {
			DataEndPoint dep = new DataEndPoint();
			dep.id = s.id;
			dep.id_string = s.ident;
			dep.title = s.displayName;
			dep.description = s.displayName;
			dep.url = urlprefix + dep.id;
			
			if(s.forms != null && s.forms.size() > 0) {
				dep.subforms = new HashMap<String, String> ();
				for(Form f : s.forms) {
					dep.subforms.put(f.name, dep.url + "?form=" + f.id);
				}
			}
			data.add(dep);
		}
		
		return data;
	}
}
