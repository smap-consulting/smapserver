package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import org.smap.sdal.model.Query;
import org.smap.sdal.model.QueryForm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

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

/*
 * Manage the table that stores queries
 */
public class QueryManager {
	
	private static Logger log =
			 Logger.getLogger(QueryManager.class.getName());
	
	LogManager lm = new LogManager();		// Application log
	
	/*
	 * Get all queries for a user ident
	 */
	public ArrayList<Query> getQueries(Connection sd, String userIdent) throws SQLException {
		
		ArrayList<Query> queries = new ArrayList<Query>();	// Results of request
		
		String sql = "select query.id, query.name, query.query "
				+ "from custom_query query, users u "
				+ "where u.id = query.u_id "
				+ "and u.ident = ? "
				+ "order by query.name asc";
		PreparedStatement pstmt = null;
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Type type = new TypeToken<ArrayList<QueryForm>>(){}.getType();

		try {
	
			pstmt = sd.prepareStatement(sql);
			pstmt.setString(1, userIdent);

			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				Query q = new Query();
				q.id = rs.getInt(1);
				q.name = rs.getString(2);
				String formsString = rs.getString(3);
				q.forms = gson.fromJson(formsString, type);
				
				queries.add(q);
			}
		} finally {
			try { if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}
		}
		
		return queries;
		
	}
	
	/*
	 * Get a form list from the query
	 */
	public ArrayList<QueryForm> getFormListFromQuery(Connection sd, int queryId) throws Exception {
		
		PreparedStatement pstmt = null;
		ArrayList<QueryForm> qfList = null;
		
		Gson gson=  new GsonBuilder().setDateFormat("yyyy-MM-dd").create();
		Type type = new TypeToken<ArrayList<QueryForm>>(){}.getType();
		
		try {
			String sql = "select query from custom_query "
					+ "where id = ? ";	
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, queryId);
			
			ResultSet rs = pstmt.executeQuery();
			if(rs.next()) {
				String queryString = rs.getString(1);
				qfList = gson.fromJson(queryString, type);
			}
			
			//extendFormList(sd, qfList);
			//Collections.reverse(qfList);		// Reverse the order to parent then child
		} finally  {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}	
		}
		
		return qfList;
		
	}
	
	/*
	 * Get a list of forms that make up a query from a single passed in formId
	 * The formList will consist of the passed in form and its parents
	 */
	public ArrayList<QueryForm> getFormList(Connection sd, int sId, int fId) throws Exception {
		
		PreparedStatement pstmt = null;
		ArrayList<QueryForm> formList = new ArrayList<QueryForm> ();
		
		try {
			String sqlGetTable = "select table_name, parentform from form "
					+ "where s_id = ? "		// Security validation
					+ "and f_id = ?;";	
			pstmt = sd.prepareStatement(sqlGetTable);
			pstmt.setInt(1, sId);
			
			while(fId > 0) {
	
				QueryForm qf = new QueryForm();
				formList.add(qf);
				
				// Add the table name to each form
				pstmt.setInt(2, fId);
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					qf.survey = sId;
					qf.form = fId;
					qf.surveyLevel = 0;
					qf.table = rs.getString(1);
					qf.parent = rs.getInt(2);
				} else {
					String msg = "Exporting survey, Form not found:" + qf.survey + ":" + qf.form;
					log.info(msg);
					throw new Exception(msg);
				}
				
				fId = qf.parent;
			}
			
			Collections.reverse(formList);		// Reverse the order to parent then child
		} finally  {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}	
		}
		
		return formList;
		
	}
	
	/*
	 * Convert a list of query forms into a tree structure that can be used to create the sql
	 * Also extend the data stored with each form entry to assist with query generation
	 */
	private class SurveyDefn {
		int sId;
		ArrayList<QueryForm> forms = new ArrayList<QueryForm> ();
		
		public SurveyDefn(int sId) {
			this.sId = sId;
		}
	}
	
	public QueryForm getQueryTree(Connection sd, ArrayList<QueryForm> queryList) throws SQLException {
		
		HashMap<Integer, SurveyDefn> surveys = new HashMap<Integer, SurveyDefn> ();
		
		String sql = "select table_name, parentform from form " 
				+ "where f_id = ? "
				+ "and s_id = ?";	
		PreparedStatement pstmt = null;
		
		try {
			pstmt = sd.prepareStatement(sql);
			
			// 1. Get the surveys and add the forms for each survey in an ordered list
			for(QueryForm qf : queryList) {
				if(surveys.get(qf.survey) == null) {
					int sId = qf.survey;
					SurveyDefn surveyDefn = new SurveyDefn(sId);
					
					// Add the array of forms from parent to child in this survey that are included in the form list
					// These forms must be in a single path, no branches
					// Start with any arbitrary form and create an array of forms up to the highest level parent
					parentsToForm(pstmt, surveyDefn.forms, qf, queryList, -1);
					surveys.put(qf.survey, surveyDefn);
				} else {
					// Add the form to the array of forms in its survey
					SurveyDefn surveyDefn = surveys.get(qf.survey);
					QueryForm lastForm = surveyDefn.forms.get(surveyDefn.forms.size() - 1);
					parentsToForm(pstmt, surveyDefn.forms, qf, queryList, lastForm.form);
				}
			}
		} finally {
			if(pstmt != null) try{pstmt.close();} catch(Exception e) {};
		}
		
		// Get the top level form and convert to a tree
		List<Integer> surveyIds = new ArrayList<Integer>(surveys.keySet());
		SurveyDefn topSurvey = null;
		for(int i = 0; i < surveyIds.size(); i++) {
			topSurvey = surveys.get(surveyIds.get(new Integer(i)));	// TODO modify to work with more than one survey
			break;
		}
		QueryForm startingForm = topSurvey.forms.get(0);
		if(topSurvey.forms.size() > 1) {
			QueryForm currentForm = startingForm;
			for(int i = 1; i < topSurvey.forms.size(); i++) {
				currentForm.childForms = new ArrayList<QueryForm> ();
				currentForm.childForms.add(topSurvey.forms.get(i));
				currentForm = topSurvey.forms.get(i);
			}
		}
		
		return startingForm;
		
	}
	
	private ArrayList<QueryForm> parentsToForm(
			PreparedStatement pstmt, 
			ArrayList<QueryForm> orderedList,
			QueryForm qf, 
			ArrayList<QueryForm> queryList,
			int stopForm) throws SQLException {
		
		ArrayList<QueryForm> tempList = new ArrayList<QueryForm> ();
		getFormsAscending(pstmt, qf.survey, qf.form, tempList, stopForm);
		Collections.reverse(tempList);    // Order the list from parent to child
		
		// Add items to the ordered list if they are part of the query or are linking forms
		boolean added = false;
		for(int i = 0; i < tempList.size(); i++) {
			QueryForm tempForm = tempList.get(i);
			QueryForm listForm = getFormFromQuery(tempForm.survey, tempForm.form, queryList);
			if (listForm != null) {
				listForm.table = tempForm.table;
				orderedList.add(listForm);
			} else if(added == true) {
				listForm = new QueryForm();
				listForm.form = tempForm.form;
				listForm.survey = tempForm.survey;
				listForm.table = tempForm.table;
				listForm.hidden = true;
				orderedList.add(listForm);
			}
		}
		
		return orderedList;
	}
	
	private QueryForm getFormFromQuery(int sId, int fId, ArrayList<QueryForm> queryList) {
		QueryForm qf = null;
		for(int i = 0; i < queryList.size(); i++) {
			if(queryList.get(i).survey == sId && queryList.get(i).form == fId) {
				qf = queryList.get(i);
				break;
			}
		}
		return qf;
	}
	
	private void getFormsAscending(PreparedStatement pstmt, int sId, 
			int fId, 
			ArrayList<QueryForm> tempList, 
			int stopForm) throws SQLException {
		
		pstmt.setInt(1, fId);
		pstmt.setInt(2, sId);
		
		ResultSet rs = pstmt.executeQuery();
		if(rs.next()) {
			int parent = rs.getInt(2);
			
			QueryForm qf = new QueryForm();
			qf.survey = sId;
			qf.form = fId;
			qf.table = rs.getString(1);
			
			tempList.add(qf);
			
			if(parent != stopForm && parent != 0) {
				getFormsAscending(pstmt, sId, parent, tempList, stopForm);
			}
			
			
		}
		
	}
	
	/*
	 * Embellish a form list with additional data needed to create the report
	 */
	public void extendFormList(Connection sd, ArrayList<QueryForm> formList) throws Exception {
		
		PreparedStatement pstmt = null;
		
		try {
			String sqlGetTable = "select table_name, parentform from form "
					+ "where s_id = ? "		// Security validation
					+ "and f_id = ?;";	
			pstmt = sd.prepareStatement(sqlGetTable);
			
			int previousSurveyId = 0;
			int surveyLevel = 0;
			for(QueryForm qf : formList) {
	
				// Add the table name to each form
				pstmt.setInt(1, qf.survey);
				pstmt.setInt(2, qf.form);
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					qf.table = rs.getString(1);
					qf.parent = rs.getInt(2);
				} else {
					String msg = "Exporting survey, Form not found:" + qf.survey + ":" + qf.form;
					log.info(msg);
					throw new Exception(msg);
				}
				
				// If the preceeding form was in a different survey then add the linking question
				if(previousSurveyId == 0) {
					previousSurveyId = qf.survey;
					qf.surveyLevel = surveyLevel;
				} else if(previousSurveyId != qf.survey) {
					// Different survey
					// TODO
					//ef.fromQuestionId = GeneralUtilityMethods.getLinkingQuestion(sd, ef.fId, previousSurveyId);
					qf.surveyLevel = ++surveyLevel;
					previousSurveyId = qf.survey;
				} else {
					qf.surveyLevel = surveyLevel;
				}
				
			}
		} finally  {
			try {if (pstmt != null) {pstmt.close();}} catch (SQLException e) {}	
		}
		
		for(int i = 0; i < formList.size(); i++) {
			System.out.println("   Survey: " + formList.get(i).survey);
			System.out.println("   Form: " + formList.get(i).form);
			System.out.println("   Question: " + formList.get(i).fromQuestionId);
			System.out.println("   Table: " + formList.get(i).table);
			System.out.println("   SurveyLevel: " + formList.get(i).surveyLevel);
			System.out.println();
		}
		
	}
	
	
}


