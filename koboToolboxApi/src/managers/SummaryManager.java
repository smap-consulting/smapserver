package managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import koboToolboxApi.Data_CSV;
import model.DataEndPoint;
import model.SummaryResultsC3;
import model.SummarySqlCreator;

import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.managers.SurveyManager;
import org.smap.sdal.model.Survey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SummaryManager {
	
	private static Logger log =
			 Logger.getLogger(Data_CSV.class.getName());

	private int orgId;							// Organisation id
	
	public SummaryManager(int orgId) {
		this.orgId = orgId;
	}
	/*
	 * 
	 * Columns:
	 * 	    status
	 * 			pending  (accepted in db)
	 * 			complete (submitted in db)
	 * 			cancelled
	 * x
	 *		scheduled
	 *		completed
	 * 		organisation
	 * 
	 * Period (for x = scheduled or other time based values)
	 * 	 	day
	 * 		week
	 * 		month
	 * 		year
	 * 
	 * 	
	 */
	public Response getTasks(Connection sd, String columns, String x, String period) throws Exception {
		
		Response response = null;
		
		SummarySqlCreator sqlCreator= new SummarySqlCreator("tasks", columns, x, period);
		PreparedStatement pstmt = null;
		
		try {

			SummaryResultsC3 res = new SummaryResultsC3();
			String sql = sqlCreator.getSQL();
			System.out.println("SQL: " + sql);
			
			pstmt = sd.prepareStatement(sql);
			
			// 1. Get the groups
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {			
				String group = rs.getString("group");	
				res.setGroups(group);
			}
			
			// 2. Create the output data array
			rs = pstmt.executeQuery();
			while(rs.next()) {
				String xValue = rs.getString("xvalue");
				String group = rs.getString("group");
				int value = rs.getInt("value");
				
				res.add(xValue, group, value);
			}
			
			/*
			 * 3. Translate group names
			 */
			if(res.groups != null) {
				for (int i = 0; i < res.groups.size(); i++) { 
					String g = res.groups.get(i);
					if(g.equals("accepted")) {
						res.groups.set(i, "pending");
					} else if(g.equals("submitted")) {
						res.groups.set(i, "completed");
					}
				}
			}
			
			Gson gson=  new GsonBuilder().disableHtmlEscaping().setDateFormat("yyyy-MM-dd").create();
			response = Response.ok(gson.toJson(res)).build();

			
		} catch (Exception e) {
			log.log(Level.SEVERE, "Exception", e);
			response = Response.serverError().build();
		} finally {
			try {if (pstmt != null) {pstmt.close();	}} catch (SQLException e) {	}
		}
		
		return response;
	}
	
}
