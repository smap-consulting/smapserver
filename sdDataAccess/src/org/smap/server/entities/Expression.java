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

package org.smap.server.entities;

import org.smap.server.managers.FormManager;

/*
 * Decompose an expression from the database into its constituent parts
 */
public class Expression {
	private String conditionQuestionName = null;
	private String operator = null;
	private String value = null;
	
	public Expression(String e) {
		
		/*
		 * Assumptions about J2ME expressions
		 *  1) First parameter is the condition name
		 *  2) Second parameter is the operator
		 *  3) Third parameter is the value compared against
		 *  4) There are exactly 3 parameters
		 *  5) However the third parameter may be a text string with spaces
		 *  6) The following pre-processing is performed
		 *  	a) Excess braces are stripped out "(", ")"
		 *  	b) "selected(X, Y)" => "X = Y"
		 */
		e = preProcess(e);
		if(e != null) {
	    	String [] s = e.split(" ");
	    	if(s.length > 2) {
		    	conditionQuestionName = s[0];
		    	operator = s[1].toLowerCase().trim();
		    	// Value can have spaces in it!
		    	value = "";
		    	for(int i = 2; i < s.length; i++) {
		    		if(!value.equals("")) {
		    			value += " ";
		    		}
		    		value += s[i];
		    	}
	    	}
		}
	}
	
	public String getConditionQuestionName() {
		return conditionQuestionName;
	}

	/*
	 * The j2me application will skip a question if the expression evaluates as true
	 * The Android application will ask the question if the expression evaluates as true
	 * By default the operator is stored in sense of the Android app (relevant)
	 * A J2ME client will need to reverse the sense
	 * 
	 * The representation of operators in j2me is also different from android
	 * 
	 * @param reverse set true to reverse the sense of the operator
	 * @return the operator
	 */
	public String getOperator(boolean reverse) {
		String o = operator;
		if(o != null && reverse) {
			if(o.equals("<")) {
				o = "greater_equals";
			} else if(o.equals(">")) {
				o = "less_equals";
			} else if(o.equals("<=")) {
				o = "greater";
			} else if(o.equals(">=")) {
				o = "less";
			} else if(o.equals("=")) {
				o = "not_equals";
			} else if(o.equals("!=")) {
				o = "equals";
			} else if(o.equals("in_list")) {
				o = "not_in_list";
			} else if(o.equals("not_in_list")) {
				o = "in_list";
			}
		}
		return o;
	}
	
	public String getValue(boolean stripQuotes) {
		// if the value is enclosed with single quotes then remove them
		String ret = value.trim();
		if(stripQuotes) {
			if(ret.charAt(0) == '\'' && ret.charAt(ret.length() -1) == '\'') {
				ret = ret.substring(1, ret.length() - 1);
			}
		}
		return ret;
	}
	
	// a) Remove enclosing brackets
	// b) Replace "Selected" with "="
	protected String preProcess(String startString) {
		String endString = startString;

		// a) Remove enclosing brackets
		if(endString != null) {
			while(true) {
				if(endString.charAt(0) == '(' && endString.charAt(endString.length() -1) == ')') {
					endString = endString.substring(1, endString.length() -1);
				} else {
					break;
				}		
			}
		}
		
		// b) Replace Selected
		if(endString.toLowerCase().startsWith("selected(")) {
			int bracket = endString.indexOf('(');
			endString = endString.substring(bracket + 1, endString.length() -1);
			String s[] = endString.split(",");
			if(s.length > 1) {
				endString = s[0].trim() + " = " + s[1].trim();
			}
			else {
				endString = null;
			}
		}
		return endString;
	}

}
