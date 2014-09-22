package org.smap.sdal.Utilities;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UtilityMethods {
	
	private static String [] reservedSQL = new String [] {
		"all",
		"analyse",
		"analyze",
		"and",
		"any",
		"array",
		"as",
		"asc",
		"assignment",
		"asymmetric",
		"authorization",
		"between",
		"binary",
		"both",
		"case",
		"cast",
		"check",
		"collate",
		"column",
		"constraint",
		"create",
		"cross",
		"current_date",
		"current_role",
		"current_time",
		"current_timestamp",
		"current_user",
		"default",
		"deferrable",
		"desc",
		"distinct",
		"do",
		"else",
		"end",
		"except",
		"false",
		"for",
		"foreign",
		"freeze",
		"from",
		"full",
		"grant",
		"group",
		"having",
		"ilike",
		"in",
		"initially",
		"inner",
		"intersect",
		"into",
		"is",
		"isnull",
		"join",
		"leading",
		"left",
		"like",
		"limit",
		"localtime",
		"localtimestamp",
		"natural",
		"new",
		"not",
		"notnull",
		"null",
		"off",
		"offset",
		"old",
		"on",
		"only",
		"or",
		"order",
		"outer",
		"overlaps",
		"placing",
		"primary",
		"references",
		"right",
		"select",
		"session_user",
		"similar",
		"some",
		"symmetric",
		"table",
		"then",
		"to",
		"trailing",
		"true",
		"union",
		"unique",
		"user",
		"using",
		"verbose",
		"when",
		"where"
	};


    
	/*
	 * Remove any characters from the name that will prevent it being used as a database column name
	 */
	static public String cleanName(String in) {
		
		String out = null;
		
		if(in != null) {
			out = in.trim().toLowerCase();
			//String lowerCaseOut = out.toLowerCase();	// Preserve case as this is important for odkCollect
	
			out = out.replace(" ", "");	// Remove spaces
			out = out.replaceAll("[\\.\\[\\\\^\\$\\|\\?\\*\\+\\(\\)\\]\"\';,:!@#&%/{}<>-]", "x");	// Remove special characters ;
		
			/*
			 * Rename legacy fields that are the same as postgres / sql reserved words
			 */
			for(int i = 0; i < reservedSQL.length; i++) {
				if(out.equals(reservedSQL[i])) {
					out = "__" + out;
					break;
				}
			}
		}

		
		return out;
	}
	
	
	

	/*
	 * Mark a record and all its children as either bad or good
	 */
	static public void markRecord(Connection cRel, Connection cSD, String tName, boolean value, String reason, int key, int sId, int fId) throws Exception {
		// TODO add optimistic locking		
		String sql = "update " + tName + " set _bad = ?, _bad_reason = ? " + 
				" where prikey = ?;";
			
		System.out.println(sql + " : " + value + " : " + reason + " : " + key);
		PreparedStatement pstmt = cRel.prepareStatement(sql);
		pstmt.setBoolean(1, value);
		pstmt.setString(2, reason);
		pstmt.setInt(3, key);
		int count = pstmt.executeUpdate();
		
		if(count != 1) {
			throw new Exception("Upate count not equal to 1");
		}
		
		// Get the child tables
		sql = "SELECT DISTINCT f.table_name, f_id FROM form f " +
				" where f.s_id = ? " + 
				" and f.parentform = ?;";
		System.out.println(sql + " : " + sId + " : " + fId);
		pstmt = cSD.prepareStatement(sql);
		pstmt.setInt(1, sId);
		pstmt.setInt(2, fId);
		
		ResultSet tableSet = pstmt.executeQuery();
		while(tableSet.next()) {
			String childTable = tableSet.getString(1);
			int childFormId = tableSet.getInt(2);
			
			// Get the child records to be updated
			sql = "select prikey from " + childTable + 
					" where parkey = ?;";
			PreparedStatement pstmt2 = cRel.prepareStatement(sql);	
			pstmt2.setInt(1, key);
			System.out.println(sql + " : " + key);
			
			ResultSet childRecs = pstmt2.executeQuery();
			while(childRecs.next()) {
				int childKey = childRecs.getInt(1);
				markRecord(cRel, cSD, childTable, value, reason, childKey, sId, childFormId);
			}
		}
		
		
	}
	
	
}
