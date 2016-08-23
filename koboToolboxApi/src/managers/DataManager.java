package managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import koboToolboxApi.Data_CSV;
import model.DataEndPoint;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Survey;

public class DataManager {
	
	private static Logger log =
			 Logger.getLogger(Data_CSV.class.getName());

	public ArrayList<DataEndPoint> getDataEndPoints(Connection sd, 
			HttpServletRequest request,
			boolean csv) throws SQLException {
		
		ArrayList<DataEndPoint> data = new ArrayList<DataEndPoint> ();
		
		/*
		 * Use existing survey manager call to get a list of surveys that the user can access
		 */
		ArrayList<Survey> surveys = null;	
		PreparedStatement pstmt = null;
		SurveyManager sm = new SurveyManager();
		try {
			boolean superUser = GeneralUtilityMethods.isSuperUser(sd, request.getRemoteUser());
			surveys = sm.getSurveys(sd, pstmt, request.getRemoteUser(), false, true, 0, superUser);
		} finally {
			
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		
			try {
				if (sd != null) {
					sd.close();
					sd = null;
				}	
			} catch (SQLException e) {
				log.log(Level.SEVERE, "Failed to close connection", e);
			}
			
		}
		
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
			data.add(dep);
		}
		
		return data;
	}
}
