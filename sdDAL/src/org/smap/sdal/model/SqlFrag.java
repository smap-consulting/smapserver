package org.smap.sdal.model;

import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.Utilities.ApplicationException;
import org.smap.sdal.Utilities.GeneralUtilityMethods;
import org.smap.sdal.constants.SmapServerMeta;

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
	public ArrayList<String> humanNames = new ArrayList<String> ();

	private static Logger log = Logger.getLogger(SqlFrag.class.getName());
	
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
	public void addSqlFragment(String in, boolean isCondition, ResourceBundle localisation) throws Exception {
		
		ArrayList<SqlFragParam> tempParams = new ArrayList<SqlFragParam> ();
		
		/*
		 * If this SQL fragment is part of a condition then save it so that it can be exported back to XLS or edited online
		 */
		if(isCondition) {
			if(conditions == null) {
				conditions = new ArrayList<String> ();
			}
			conditions.add(in);
		}
		String charTokens = "=+-><*/()";
		in = GeneralUtilityMethods.addSurroundingWhiteSpace(in, charTokens.toCharArray());
		in = GeneralUtilityMethods.addSurroundingWhiteSpace(in, new String[] {"<=", ">=", "!="});
		
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
				p.setType("sql");
				p.sValue = in.substring(start, idx1);
				tempParams.add(p);
				addedChars = idx1;
			}
			
			// Add the text fragment
			idx2 = in.indexOf('\'', idx1 + 1);
			if(idx2 > -1) {
				SqlFragParam p = new SqlFragParam();
				p.setType("text");
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
			p.setType("sql");
			p.sValue = in.substring(addedChars);
			tempParams.add(p);
		}
		
		/*
		 * Tokenize the remainder of the SQL
		 * These can be split using white space
		 */
		for(int i = 0; i < tempParams.size(); i++) {
			SqlFragParam p = tempParams.get(i);
			if(p.getType().equals("sql")) {
				String [] token = p.sValue.split("[\\s]");  // Split on white space
				for(int j = 0; j < token.length; j++) {
					String s = sqlToken(token[j], localisation);
					
					if(s.length() > 0) {
						sql.append(" " + s + " ");
					}
				}
			} else if(p.getType().equals("text") || p.getType().equals("date")) {
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
	public String sqlToken(String token, ResourceBundle localisation) throws Exception {
		String out = "";
		
		token = token.trim();
		
		// Check for a column name
		if(token.startsWith("${") && token.endsWith("}")) {
			String name = token.substring(2, token.length() - 1);
			boolean columnNameCaptured = false;
			out = GeneralUtilityMethods.cleanName(name, true, true, false);
			for(int i = 0; i < columns.size(); i++) {
				if(columns.get(i).equals(out)) {
					columnNameCaptured = true;
					break;
				}
			}
			if(!columnNameCaptured) {
				columns.add(out);
				humanNames.add(name);
			}
		} else if (token.equals(">") ||
				token.equals("<") ||
				token.equals("<=") ||
				token.equals(">=") ||
				token.equals("=") || 
				token.equals("!=") || 
				token.equals("-") ||
				token.equals("+") ||
				token.equals("*") ||
				token.equals("/") ||
				token.equals(")") ||
				token.equals("(") ||
				token.equals("or") ||
				token.equals(SmapServerMeta.UPLOAD_TIME_NAME) ||
				token.equals(SmapServerMeta.SCHEDULED_START_NAME) ||
				token.equals("and") || 
				token.equals("is") || 
				token.equals("null") || 
				token.equals("not") || 
				token.equals("like") || 
				token.equals("integer") || 
				token.equals("current_date") ||
				token.equals("now()")) {
			out = token;
		} else if (token.equals("empty")) {
			out = "is null";
		} else if (token.equals("all")) {
			out = "";
		} else if (token.startsWith("{") && token.endsWith("}")) {	// Preserve {xx} syntax if xx is integer
			out = "";
			String content = token.substring(1, token.length() - 1);
			
			if(content != null) {
				String [] contentArray = content.split("_");
				String [] contentArray2 = content.split(":");
				if(contentArray.length == 1 && contentArray2.length == 1) {
					// simple integer assumed to be days
					try {
						Integer iValue = Integer.parseInt(contentArray[0]);
						out = "'" + iValue.toString() + "'";
					} catch (Exception e) {
						log.log(Level.SEVERE,"Error", e);
					}	
				} else if(contentArray.length == 2) {
					// 2 elements first of which must be an integer				
					try {
						Integer iValue = Integer.parseInt(contentArray[0]);
							
						out = "interval '" + iValue.toString() + " ";
						if(contentArray[1].equals("day")) {
							out += contentArray[1] + "'";
						} else if(contentArray[1].equals("days")) {
							out += contentArray[1] + "'";
						} else if(contentArray[1].equals("hour")) {
							out += contentArray[1] + "'";
						} else if(contentArray[1].equals("hours")) {
							out += contentArray[1] + "'";
						} else if(contentArray[1].equals("minute")) {
							out += contentArray[1] + "'";
						} else if(contentArray[1].equals("minutes")) {
							out += contentArray[1] + "'";
						} else if(contentArray[1].equals("second")) {
							out += contentArray[1] + "'";
						} else if(contentArray[1].equals("seconds")) {
							out += contentArray[1] + "'";
						} else {
							out = "";
						}
					} catch (Exception e) {
						log.log(Level.SEVERE,"Error", e);
					}	
				} else if(contentArray2.length == 3) {
					try {
						Integer hValue = Integer.parseInt(contentArray2[0]);
						Integer mValue = Integer.parseInt(contentArray2[1]);
						Integer sValue = Integer.parseInt(contentArray2[2]);
						out = " interval '" + content +"'";		// all looks good
					} catch (Exception e) {
						log.log(Level.SEVERE,"Error", e);
					}
				}
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
				String msg = localisation.getString("inv_token");
				msg = msg.replace("%s1", token);
				throw new ApplicationException(msg);
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
			} else if(in.contains("(")) {
				isText = false; // Contains a function possibly
			} else if(in.contains("where") || in.contains("and")) {
				isText = false; // Contains some sql reserved words
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
