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

package org.smap.sdal.Utilities;

import java.util.HashMap;
import java.util.logging.Logger;

/*
 * This class supports the filtering of rows in a CSV file
 */
public class CSVFilter {
	
	private static Logger log =
			 Logger.getLogger(Authorise.class.getName());
	
	private class Rule {
		public int column;
		public int function;	// 1: contains, 2: startswith, 3: endswith, 4: matches 
		public String value;
	}
	private boolean includeAll = true;
	private Rule r1 = null;
	private Rule r2 = null;		// Secondary filter rule
	
	public CSVFilter(String [] cols, String appearance) {
		int idx1 = appearance.indexOf('(');
		int idx2 = appearance.indexOf(')');
		
		if(cols == null) {
			// No columns in csv file
			return;
		}
		
		if(idx1 > 0 && idx2 > idx1) {
			String criteriaString = appearance.substring(idx1 + 1, idx2);
			log.info("#### criteria for csv filter: " + criteriaString);
			
			String criteria [] = criteriaString.split(",");
			if(criteria.length < 4) {
				
				// This is presumably a search without a criteria - It can't have too many rows so lets include it
				log.info("Info: Criteria elements less than 4, incude all rows");
				includeAll = true;
				
			} else {
				
				r1 = new Rule();
				r1.column = -1;
				
				// Ignore file name which is first criterion
				for(int i = 1; i < criteria.length; i++) {						
									
					// function
					if(i == 1) { 
						
						// remove quotes
						criteria[i] = criteria[i].trim();
						criteria[i] = criteria[i].substring(1, criteria[i].length() -1);
						log.info("@@@@ criterion " + i + " " + criteria[i]);
						
						if(criteria[i].equals("contains")) {
							r1.function = 1;	
						} else if(criteria[i].equals("startswith")) {
							r1.function = 2;	
						} else if(criteria[i].equals("endswith")) {
							r1.function = 3;	
						} else if(criteria[i].equals("matches")) {
							r1.function = 4;	
						} else {
							log.info("Error: unknown function, " + criteria[i]);
							return;
						}
					}
					
					// Column to match
					if(i == 2) {
						
						// remove quotes
						criteria[i] = criteria[i].trim();
						criteria[i] = criteria[i].substring(1, criteria[i].length() -1);
						log.info("@@@@ criterion " + i + " " + criteria[i]);
						
						for(int j = 0; j < cols.length; j++) {
							log.info("***: " + criteria[i] + " : " + cols[j]);
							if (criteria[i].equals(cols[j])) {
								r1.column = j;
								break;
							}	
						}
						if(r1.column == -1) {
							log.info("Error: no matching column");
							return;
						}
					}
					
					// Value to match
					if(i == 3) {
						
						// Check for quotes - only strings are supported for filtering values
						criteria[i] = criteria[i].trim();
						if(criteria[i].charAt(0) == '\'') {
							criteria[i] = criteria[i].substring(1, criteria[i].length() -1);
							log.info("@@@@ value criterion " + i + " " + criteria[i]);
							
							r1.value =  criteria[i];
							
						} else {	
							log.info("Only static values can be filtered on");
							return;
						}	
					}
					
					if(i == 4) {		// Filter column
						
						// remove quotes
						criteria[i] = criteria[i].trim();
						criteria[i] = criteria[i].substring(1, criteria[i].length() -1);
						log.info("++++ Filter criterion " + i + " " + criteria[i]);
						
						for(int j = 0; j < cols.length; j++) {
							log.info("***: " + criteria[i] + " : " + cols[j]);
							if (criteria[i].equals(cols[j])) {
								r2 = new Rule();
								r2.column = j;
								r2.function = 4;		// Exact match is only function supported by odk
								break;
							}	
						}
						if(r2 == null) {
							log.info("Error: no matching column for filter ignore");
						}
					}
					
					// Filter Value to match
					if(r2 != null && i == 5) {
						
						// Check for quotes - only strings are supported for filtering values
						criteria[i] = criteria[i].trim();
						if(criteria[i].charAt(0) == '\'') {
							criteria[i] = criteria[i].substring(1, criteria[i].length() -1);
							log.info("+++ Filter value criterion " + i + " " + criteria[i]);
							
							r2.value =  criteria[i];
							
						} else {
							log.info("Info dynamic filter value are not supported: Filter will be ignored.");
							r2 = null;
						}	
					}
				}
				includeAll = false;
				
			}
		} else {
			log.info("Error: Unknown appearance: " + appearance);
		}
		
	}
	
	/*
	 * Return the cascade_filter
	 */
	public HashMap<String, String> GetCascadeFilter(String[] optionCols) {

		HashMap<String, String> filter = new HashMap<String, String> ();
		if(r1 != null && r1.column != -1) {
			filter.put("_smap_cascade", optionCols[r1.column]);
		}
		return filter;
		
	}
	/*
	 * Return true if the row should be included
	 */
	public boolean isIncluded(String [] cols) {
		boolean include = true;
				
		if(!includeAll) {
			switch(r1.function) {
				case 1: // contains
					if(!cols[r1.column].contains(r1.value)) {
						include = false;
					}
					break;
				case 2: // startswith
					if(!cols[r1.column].startsWith(r1.value)) {
						include = false;
					}
					break;
				case 3: // endswith
					if(!cols[r1.column].startsWith(r1.value)) {
						include = false;
					}
					break;
				case 4: // matches
					if(!cols[r1.column].equals(r1.value)) {
						include = false;
					}
					break;
			}
			
			if(include) {	// Check the secondary filter
				if(r2 != null) {
					if(!cols[r2.column].equals(r2.value)) {
						include = false;
					}
				}
			}
		}
		
		return include;
	}
}
