package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.GeneralUtilityMethods;

/*
 * Form Class
 * Used for survey editing
 * If the param type is sql then this is an intermediate stage where the parameter still needs to be tokenized
 */

public class SqlFrag {
	public StringBuffer expression = null;		// The original expression used to create this sql
	public ArrayList<String> conditions = null;	// Alternatively the original conditions used to create it
	
	public StringBuffer sql = new StringBuffer("");
	public ArrayList<SqlFragParam> params = new ArrayList<SqlFragParam> ();
	public ArrayList<String> columns = new ArrayList<String> ();

	private static Logger log =
			 Logger.getLogger(SqlFrag.class.getName());
	
	// Set the original expression used to create this SQlFrag
	public void setExpression(String in) {
		expression = new StringBuffer(in);
	}
	
	public void add(String in) {
		if(sql.length() > 0) {
			sql.append(" ");
		}
		sql.append(in);
	}
	
	public void addText(String in) {
		
		// Escape any quotes
		in = in.replaceAll("\'", "\'\'");
		if(sql.length() > 0) {
			sql.append(" ");
		}
		sql.append("'");
		sql.append(in);
		sql.append("'");
	}
	
	/*
	 * Add an SQL expression
	 */
	public void addSqlFragment(String in, ResourceBundle localisation, boolean isCondition) throws Exception {
		
		ArrayList<SqlFragParam> tempParams = new ArrayList<SqlFragParam> ();
		
		System.out.println("Add sqlFrag: " + in);
		
		/*
		 * If this SQL fragment is part of a condition then save it so that it can be exported back to XLS or edited online
		 */
		if(isCondition) {
			if(conditions == null) {
				conditions = new ArrayList<String> ();
			}
			conditions.add(in);
		}
		
		/*
		 * This SQL Fragment may actually be text without quotes
		 * If so then wrap in single quotes
		 */
		in = checkForText(in);
		
		/*
		 * Get the text parameters and the sql fragments
		 * Text parameters can include spaces, use single quotes to locate them
		 */
		int idx1 = -1,
			idx2 = -1,
			addedChars = 0,
			start = 0;
		idx1 = in.indexOf('\'');
		while(idx1 > -1) {
			
			// Add the sql fragment
			if(idx1 > 0) {
				SqlFragParam p = new SqlFragParam();
				p.type = "sql";
				p.sValue = in.substring(start, idx1);
				tempParams.add(p);
				addedChars = idx1;
			}
			
			// Add the text fragment
			idx2 = in.indexOf('\'', idx1 + 1);
			if(idx2 > -1) {
				SqlFragParam p = new SqlFragParam();
				p.type = "text";
				p.sValue = in.substring(idx1 + 1, idx2);	// Remove quotation marks
				tempParams.add(p);
				addedChars = idx2 + 1;							// Skip over quote
			} else {
				throw new Exception(localisation.getString("mf_mq") + ": " + in);
			}
			
			start = idx2 + 1;
			idx1 = in.indexOf('\'', idx2 + 1);		
		}
		if(addedChars < in.length()) {
			SqlFragParam p = new SqlFragParam();
			p.type = "sql";
			p.sValue = in.substring(addedChars);
			tempParams.add(p);
		}
		
		/*
		 * Tokenize the remainder of the SQL
		 * These can be split using white space
		 */
		for(int i = 0; i < tempParams.size(); i++) {
			SqlFragParam p = tempParams.get(i);
			if(p.type.equals("sql")) {
				String [] token = p.sValue.split("[\\s]");  // Split on white space
				for(int j = 0; j < token.length; j++) {
					String s = sqlToken(token[j]);
					
					if(s.length() > 0) {
						sql.append(" " + s + " ");
					}
				}
			} else if(p.type.equals("text")) {
				SqlFragParam px = new SqlFragParam();
				px.addTextParam(p.sValue);
				params.add(px);
				sql.append(" ? ");
			}
		}
		
		
	}
	
	/*
	 * Process a single sql token
	 */
	public String sqlToken(String token) throws Exception {
		String out = "";
		
		token = token.trim().toLowerCase();
		
		// Check for a column name
		if(token.startsWith("${") && token.endsWith("}")) {
			String name = token.substring(2, token.length() - 1);
			boolean columnNameCaptured = false;
			out = GeneralUtilityMethods.cleanName(name, true, true, true);
			for(int i = 0; i < columns.size(); i++) {
				if(columns.get(i).equals(name)) {
					columnNameCaptured = true;
					break;
				}
			}
			if(!columnNameCaptured) {
				columns.add(name);
			}
		} else if (token.equals(">") ||
				token.equals("<") ||
				token.equals("<=") ||
				token.equals(">=") ||
				token.equals("=") || 
				token.equals("-") ||
				token.equals("+") ||
				token.equals("*") ||
				token.equals("/") ||
				token.equals(")") ||
				token.equals("(") ||
				token.equals("or") ||
				token.equals("and") || 
				token.equals("integer") || 
				token.equals("current_date") ||
				token.equals("now()")) {
			out = token;
		} else if (token.equals("empty")) {
			out = "is null";
		} else if (token.equals("all")) {
			out = "";
		} else if (token.startsWith("{") && token.endsWith("}")) {	// Preserve {xx} syntax if xx is integer
			String content = token.substring(1, token.length() - 1);
			try {
				Integer iValue = Integer.parseInt(content);
				out = "{" + iValue.toString() + "}";
			} catch (Exception e) {
				log.log(Level.SEVERE,"Error", e);
			}	
		} else if (token.length() > 0) {
			// Non text parameter, accept decimal or integer
			try {
				if(token.indexOf('.') >= 0) {
					Double dValue = Double.parseDouble(token);
					out = dValue.toString();
				} else {
					Integer iValue = Integer.parseInt(token);
					out = iValue.toString();
				}
			} catch (Exception e) {
				log.log(Level.SEVERE,"Error", e);
			}
			
		}
		return out;
	}
	
	/*
	 * This function is used as it has been allowed to represent text without quotes when setting a condition value
	 * It may return false negatives so it is recommended that quotes always be used to identify text
	 */
	private String checkForText(String in) {
		String out = null;
		boolean isText = true;
		if(in != null) {
			if(in.indexOf('\'') > -1) {
				isText = false; // Contains a text fragment
			} else if(in.contains("{")) {
				isText = false; // Contains a column name
			} else if(in.contains("()")) {
				isText = false; // Contains a function without parameters such as now()
			}
		}
		if(isText) {
			out = "'" + in + "'";
		} else {
			out = in;
		}
		return out;
	}
	public void debug() {
		System.out.println("======");
		System.out.println("sql     " + sql.toString());
		for(int i = 0; i < params.size(); i++) {
			System.out.println("   " + params.get(i).debug());
		}
	}
}
