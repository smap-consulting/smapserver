package org.smap.sdal.managers;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
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
				if(qfList != null) {
					// Add the table name
					for(int i = 0; i < qfList.size(); i++) {
						
					}
				}
			}
			
			extendFormList(sd, qfList);
			Collections.reverse(qfList);		// Reverse the order to parent then child
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
	 */
	public void getQueryTree() {
		
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


