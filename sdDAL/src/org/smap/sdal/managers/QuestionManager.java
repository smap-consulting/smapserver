package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.UtilityMethods;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Question;
import org.smap.sdal.model.ServerSideCalculate;
import org.smap.sdal.model.Survey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

public class QuestionManager {
	
	private static Logger log =
			 Logger.getLogger(QuestionManager.class.getName());

	/*
	 * These functions are used when adding CSV files to store options
	 */
	public ArrayList<Question> getByCSV(Connection sd, 
			int sId,
			String csvFileName			
			)  {
		
		ArrayList<Question> questions = new ArrayList<Question>();	// Results of request
		
		
		Survey survey = new Survey();
		survey.setId(sId);
		
		int idx = csvFileName.lastIndexOf('.');
		String csvRoot = csvFileName;
		if(idx > 0) {
			csvRoot = csvFileName.substring(0, idx);
		}
		
		// Escape csvRoot
		csvRoot = csvRoot.replace("\'", "\'\'");
		
		ResultSet resultSet = null;
		String sql = "select q.q_id, q.qname, q.qtype, q.appearance " +
				" from question q, form f " +
				" where f.f_id = q.f_id " +
				" and q.appearance like 'search(''" + csvRoot + "''%' " +
				" and q.qtype like 'select%' " + 
				" and f.s_id = ?";
	
		String sqlOption = "select o_id, seq, label_id, ovalue " +
				" from option " +
				" where q_id = ? " +
				" and externalfile = 'true';";
		
		String sqlLanguages = "select distinct t.language from translation t " +
				"where s_id = ? order by t.language asc";
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtOption = null;
		PreparedStatement pstmtLanguages = null;
		
		try {
			pstmtOption = sd.prepareStatement(sqlOption);
			
			pstmtLanguages = sd.prepareStatement(sqlLanguages);
			pstmtLanguages.setInt(1, sId);
			ResultSet rsLanguages = pstmtLanguages.executeQuery();
			while(rsLanguages.next()) {
				survey.languages.add(rsLanguages.getString(1));
			}
			
			pstmt = sd.prepareStatement(sql);
			pstmt.setInt(1, sId);
			
			System.out.println("SQL: " + pstmt.toString());
			resultSet = pstmt.executeQuery();
	
			while (resultSet.next()) {								
	
				Question q = new Question();
				q.id = resultSet.getInt(1);
				q.name = resultSet.getString(2);
				q.type = resultSet.getString(3);
				q.appearance = resultSet.getString(4);
				
			
				pstmtOption.setInt(1, q.id);
				ResultSet rsOptions = pstmtOption.executeQuery();
				while (rsOptions.next()) {
					Option o = new Option();
					o.id = rsOptions.getInt("o_id");
					o.seq = rsOptions.getInt("seq");
					o.text_id = rsOptions.getString("label_id");
					
					UtilityMethods.getLabels( sd, survey, o.text_id, null, o.labels);
				}
				
				questions.add(q);
			} 
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(pstmt != null) try{pstmt.close();}catch(Exception e){}
			if(pstmtOption != null) try{pstmtOption.close();}catch(Exception e){}
			if(pstmtLanguages != null) try{pstmtLanguages.close();}catch(Exception e){}
		}
		
		return questions;
		
	}
	
}
