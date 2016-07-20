package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Form Class
 * Used for survey editing
 * If the param type is sql then this is an intermediate stage where the parameter still needs to be tokenized
 */
class Param {
	String type;		// text || sql || integer || double
	String sValue;		// text || sql
	int iValue;			// integer
	double dValue;		// double
	
	void addTextParam(String v) {
		type = "text";
		sValue = v;
	}
	
	void addNonTextParam(String v) throws Exception {
		boolean done = false;
		if(v.indexOf('.') > -1) {
			try {
				dValue = Double.parseDouble(v);
				type = "double";
				done = true;
			} catch (Exception e) {
				// Ignore
			}
		} else {
			try {
				iValue = Integer.parseInt(v);
				type = "integer";
				done = true;
			} catch (Exception e) {
				// Ignore
			}
		}
		if(!done) {
			throw new Exception("Unknown token: " + v);
		}
	}
	
	String debug() {
		if(type.equals("text") || type.equals("sql")) {
			return type + " : " + sValue;
		} else {
			return "";
		}
	}
}

public class SqlFrag {
	StringBuffer sql = new StringBuffer("");
	ArrayList<Param> params = new ArrayList<Param> ();
	ArrayList<String> columns = new ArrayList<String> ();

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
	
	public void addRaw(String in) throws Exception {
		
		ArrayList<Param> tempParams = new ArrayList<Param> ();
		ArrayList<String> tempColumns = new ArrayList<String> ();
		
		/*
		 * Get the text parameters
		 */
		int idx1 = -1,
			idx2 = -1,
			addedChars = 0,
			start = 0;
		idx1 = in.indexOf('\'');
		while(idx1 > -1) {
			
			// Add the sql fragment
			if(idx1 > 0) {
				Param p = new Param();
				p.type = "sql";
				p.sValue = in.substring(start, idx1);
				tempParams.add(p);
				addedChars = idx1;
			}
			
			// Add the text fragment
			idx2 = in.indexOf('\'', idx1 + 1);
			if(idx2 > -1) {
				Param p = new Param();
				p.type = "text";
				p.sValue = in.substring(idx1 + 1, idx2);	// Remove quotation marks
				tempParams.add(p);
				addedChars = idx2 + 1;							// Skip over quote
			} else {
				throw new Exception("Missing matching parameter in: " + in);
			}
			
			start = idx2 + 1;
			idx1 = in.indexOf('\'', idx2 + 1);		
		}
		if(addedChars < in.length()) {
			Param p = new Param();
			p.type = "sql";
			p.sValue = in.substring(addedChars);
			tempParams.add(p);
		}
		
		/*
		 * Tokenize the remainder of the SQL
		 */
		for(int i = 0; i < tempParams.size(); i++) {
			Param p = tempParams.get(i);
			if(p.type.equals("sql")) {
				String [] token = p.sValue.split(" ");
				for(int j = 0; j < token.length; j++) {
					String s = sqlToken(token[j]);
					if(s.length() > 0) {
						sql.append(" " + s + " ");
					}
				}
			} else if(p.type.equals("text")) {
				Param px = new Param();
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
		
		token = token.trim();
		
		// Check for a column name
		if(token.startsWith("${") && token.endsWith("}")) {
			out = token.substring(2, token.length() - 1);
			columns.add(out);
		} else if (token.equals(">") ||
				token.equals("<") ||
				token.equals("<=") ||
				token.equals(">=") ||
				token.equals("=") || 
				token.equals("and") || 
				token.equals("now()")) {
			out = token;
		} else if (token.equals("empty")) {
			out = "is null";
		} else if (token.equals("all")) {
			out = "";
		} else if (token.length() > 0) {
			out = "?";
			Param px = new Param();
			px.addNonTextParam(token);
			params.add(px);
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
