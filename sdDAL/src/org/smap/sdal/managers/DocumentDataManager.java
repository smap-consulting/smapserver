package org.smap.sdal.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.Utilities.SDDataSource;
import org.smap.sdal.model.ChangeItem;
import org.smap.sdal.model.ChangeSet;
import org.smap.sdal.model.Form;
import org.smap.sdal.model.KeyValue;
import org.smap.sdal.model.Label;
import org.smap.sdal.model.ManifestValue;
import org.smap.sdal.model.Option;
import org.smap.sdal.model.Project;
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

public class DocumentDataManager {
	
	private static Logger log =
			 Logger.getLogger(DocumentDataManager.class.getName());

	Connection sd = null;
	int sId = 0;
	
	public DocumentDataManager(Connection sd, int sId) {
		this.sd = sd;
		this.sId = sId;
	}
	
	/*
	 * Get data
	 */
	protected class Category {
		String gender;
		int age1;
		int age2;
		
		public Category(String gender, int age1, int age2) {
			this.gender = gender;
			this.age1 = age1;
			this.age2 = age2;
		}
	}
	
	public ArrayList<KeyValue> getData(
			Connection sd, 
			Connection cResults,
			int sId, java.sql.Date startDate, 
			java.sql.Date endDate) throws SQLException {
		
		String tableMain = "s" + sId + "_main";
		String tableRepeat = "s" + sId + "_consultation_rpt";
		ArrayList<KeyValue> data = new ArrayList<KeyValue> ();
		
		// Set the diagnosies to be checked
		ArrayList<String> qnames = new ArrayList<String> (
				Arrays.asList("ewarn_upper_respiratory", 
						"ewarn_lower_respiratory", 
						"ewarn_diarrhea",
						"ewarn_bloody_diarrhea",
						"ewarn_watery_diarrhea",
						"ewarn_jaundice",
						"ewarn_flaccid_paralysis",
						"ewarn_measles",
						"ewarn_meningitis",
						"ewarn_diptheria",
						"ewarn_pertusis",
						"ewarn_tetanus",
						"ewarn_fever",
						"ewarn_leishmaniasis",
						"ewarn_hem_fever",
						"ewarn_skin_diseases",
						"ewarn_animal_bite",
						"ewarn_malnutrition",
						"ewarn_other"
						));
		
		// Set the categories to use
		ArrayList<Category> categories = new ArrayList();
		Category c1 = new Category("male", 0, 5);
		Category c2 = new Category("female", 0, 5);
		Category c3 = new Category("male", 5, 500);
		Category c4 = new Category("female", 5, 500);
		categories.add(c1);
		categories.add(c2);
		categories.add(c3);
		categories.add(c4);
	
		String sqlGetColNames = "select column_name from option where ovalue = ? and l_id = "
				+ "(select l_id from question where qname = 'diagnosis' and f_id in (select f_id from form where s_id = ?))";
		
		PreparedStatement pstmt = null;
		PreparedStatement pstmtGetColNames = null;
		
		try {
		
			StringBuffer sql = new StringBuffer("");
			sql.append("select ");
			
			// Get the option column names
			pstmtGetColNames = sd.prepareStatement(sqlGetColNames);
			pstmtGetColNames.setInt(2, sId);
			boolean first = true;
			for(String col : qnames) {
				if(!first) {
					sql.append(",");
				}
				sql.append("sum(diagnosis__");
				pstmtGetColNames.setString(1, col);
				System.out.println("xxxx: " + pstmtGetColNames.toString());
				ResultSet rs = pstmtGetColNames.executeQuery();
				if(rs.next()) {
					sql.append(rs.getString(1));
				}
				sql.append(") as ");
				sql.append(col);
				first = false;
			}
			sql.append(" from ");
			sql.append(tableMain);
			sql.append(" main,");
			sql.append(tableRepeat);
			sql.append(" repeat ");
			
			sql.append("where main.prikey = repeat.parkey ");
			sql.append("and main.gender = ? ");
			sql.append("and main.age >= ? ");
			sql.append("and main.age < ? ");
			
			String sqlRestrictToDateRange = GeneralUtilityMethods.getDateRange(startDate, endDate, tableRepeat + ".consult_date");
			if(sqlRestrictToDateRange.trim().length() > 0) {
				sql.append(sqlRestrictToDateRange);
			}
			
			pstmt = cResults.prepareStatement(sql.toString());
			System.out.println("Query: " + pstmt.toString());
			int idx = 1;
			if(sqlRestrictToDateRange.trim().length() > 0) {
				pstmt.setDate(idx++, startDate);
				pstmt.setDate(idx++, endDate);
			}
			
			for(Category cat : categories) {
				int index = idx;
				pstmt.setString(index++,  cat.gender);
				pstmt.setInt(index++, cat.age1);
				pstmt.setInt(index++,  cat.age2);
			
				System.out.println("Get count: " + pstmt.toString());
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					for(String col : qnames) {
						String count = rs.getString(col);
						if(count == null) {
							count = "0";
						}
					
						StringBuffer key = new StringBuffer(col);
						if(cat.gender.equals("male")) {
							key.append("_m");
						} else {
							key.append("_f");
						}
						if(cat.age1 == 0) {
							key.append("_u5");
						} else {
							key.append("_a5");
						}
						System.out.println("   key: " + key.toString());
						data.add(new KeyValue(key.toString(), count));
					}
				}			
			}
				
			
		} finally {		
			try {if (pstmt != null) {pstmt.close();} } catch (SQLException e) {	}
			try {if (pstmtGetColNames != null) {pstmtGetColNames.close();} } catch (SQLException e) {	}
		}
		
		
	
		
		return data;
	}
	
}
