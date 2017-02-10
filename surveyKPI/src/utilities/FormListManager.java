package utilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.model.ExportForm;

import surveyKPI.ExportSurveyMisc;

public class FormListManager {
	
	private static Logger log =
			 Logger.getLogger(ExportSurveyMisc.class.getName());

	/*
	 * Embellish a form list with additional data needed to create the report
	 */
	public void setFormList(Connection sd, ArrayList<ExportForm> formList) throws Exception {
		
		PreparedStatement pstmt = null;
		
		try {
			String sqlGetTable = "select table_name, parentform from form "
					+ "where s_id = ? "		// Security validation
					+ "and f_id = ?;";	
			pstmt = sd.prepareStatement(sqlGetTable);
			
			int previousSurveyId = 0;
			int surveyLevel = 0;
			for(ExportForm ef : formList) {
	
				// Add the table name to each form
				pstmt.setInt(1, ef.sId);
				pstmt.setInt(2, ef.fId);
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					ef.table = rs.getString(1);
					ef.parent = rs.getInt(2);
				} else {
					String msg = "Exporting survey, Form not found:" + ef.sId + ":" + ef.fId;
					log.info(msg);
					throw new Exception(msg);
				}
				
				// If the preceeding form was in a different survey then add the linking question
				if(previousSurveyId == 0) {
					previousSurveyId = ef.sId;
					ef.surveyLevel = surveyLevel;
				} else if(previousSurveyId != ef.sId) {
					// Different survey
					// TODO
					//ef.fromQuestionId = GeneralUtilityMethods.getLinkingQuestion(sd, ef.fId, previousSurveyId);
					ef.surveyLevel = ++surveyLevel;
					previousSurveyId = ef.sId;
				} else {
					ef.surveyLevel = surveyLevel;
				}
				
			}
		} finally  {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}	
		}
		
		for(int i = 0; i < formList.size(); i++) {
			System.out.println("   Survey: " + formList.get(i).sId);
			System.out.println("   Form: " + formList.get(i).fId);
			System.out.println("   Question: " + formList.get(i).fromQuestionId);
			System.out.println("   Table: " + formList.get(i).table);
			System.out.println("   SurveyLevel: " + formList.get(i).surveyLevel);
			System.out.println();
		}
		
	}
	
	/*
	 * Get a form list from a single passed in formId
	 * The formList will consist of the passed in form and its parents
	 */
	public ArrayList<ExportForm> getFormList(Connection sd, int sId, int fId) throws Exception {
		
		PreparedStatement pstmt = null;
		ArrayList<ExportForm> formList = new ArrayList<ExportForm> ();
		
		try {
			String sqlGetTable = "select table_name, parentform from form "
					+ "where s_id = ? "		// Security validation
					+ "and f_id = ?;";	
			pstmt = sd.prepareStatement(sqlGetTable);
			pstmt.setInt(1, sId);
			
			while(fId > 0) {
	
				ExportForm ef = new ExportForm();
				formList.add(ef);
				
				// Add the table name to each form
				pstmt.setInt(2, fId);
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					ef.sId = sId;
					ef.fId = fId;
					ef.surveyLevel = 0;
					ef.table = rs.getString(1);
					ef.parent = rs.getInt(2);
				} else {
					String msg = "Exporting survey, Form not found:" + ef.sId + ":" + ef.fId;
					log.info(msg);
					throw new Exception(msg);
				}
				
				fId = ef.parent;
			}
			
			// Set survey level in reverse
		} finally  {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}	
		}
		
		for(int i = 0; i < formList.size(); i++) {
			System.out.println("   Survey: " + formList.get(i).sId);
			System.out.println("   Form: " + formList.get(i).fId);
			System.out.println("   Question: " + formList.get(i).fromQuestionId);
			System.out.println("   Table: " + formList.get(i).table);
			System.out.println("   SurveyLevel: " + formList.get(i).surveyLevel);
			System.out.println();
		}
		
		return formList;
		
	}
	
	
}
