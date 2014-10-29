package org.smap.server.utilities;

import java.util.Comparator;
import java.util.List;

import org.smap.model.IE;
import org.smap.server.entities.Option;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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

	public static String getLastFromPath(String path) {
		int startIdx = path.lastIndexOf('/') + 1;
		return path.substring(startIdx);
	}
	
	public static String getGroupFromPath(String path) {
		if(path != null) {
			int startIdx = path.lastIndexOf('/');
			return path.substring(0, startIdx);
		} else {
			return null;
		}
	}
	
	static String getFirstFromPath(String path) {
		String firstForm = null;
		
		try {
			int startIdx = path.indexOf('/') + 1;
			int lastIdx = path.indexOf('/', startIdx);
			firstForm = path.substring(startIdx, lastIdx);
		} catch (StringIndexOutOfBoundsException e) {		
			
		}
		
		return firstForm; 
	}
	
	static String getQuotedText(String text) {
		int startIdx = text.indexOf('\'') + 1;
    	int endIdx = text.lastIndexOf('\'');
    	return text.substring(startIdx, endIdx);
	}
	
	// XML Parsing methods
	
    /*
     * Method to get the first child element
     * 
     * @param n the node
     * @return Node the first element node
     */
    static Node getFirstElement(Node n) {
    	Node firstElement = null;
    	NodeList children = n.getChildNodes();
    	for(int i = 0; i < children.getLength(); i++) {
    		if(children.item(i).getNodeType() == Node.ELEMENT_NODE) {
    			firstElement = children.item(i);
    		}
    	}
    	return firstElement;
    }
    
    static boolean hasChildElement(Node n) {
    	boolean result = false;
    	
    	NodeList eList = n.getChildNodes();
    	if (eList != null) {
			for (int i = 0; i < eList.getLength(); i++) {
				if(eList.item(i).getNodeType() == Node.ELEMENT_NODE) {
					result = true;
					break;
				}
			}
		}
    	
    	return result;
    }   
    
	/*
	 * Remove any characters from the name that will prevent it being used as a database column name
	 */
	static public String cleanName(String in) {
		String out = in.trim().toLowerCase();
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

		
		return out;
	}
	
	/*
	 * TODO replace the next two functions by a single function that sorts any object that
	 * implements the appropriate interface
	 */
	static public void sortElements(List <IE> in) {
		
		java.util.Collections.sort(in, new Comparator<IE>() {
			@Override
			public int compare(IE object1, IE object2) {
				if (object1.getSeq() < object2.getSeq())
					return -1;
				else if (object1.getSeq() == object2.getSeq())
					return 0;
				else
					return 1;
			}
		});
	}
	
	static public void sortOptions(List <Option> in) {
		
		java.util.Collections.sort(in, new Comparator<Option>() {
			@Override
			public int compare(Option object1, Option object2) {
				if (object1.getSeq() < object2.getSeq())
					return -1;
				else if (object1.getSeq() == object2.getSeq())
					return 0;
				else
					return 1;
			}
		});
	}
	
	/*
	 * Deprecated - conversion of language codes for j2me will no longer be done
	static public String getLanguageCode(String code) {
		String outCode = null;
		
		boolean found = false;
		if(code != null) {
			for(int i = 0; !found && languageCodes[i][0] != null; i++) {
				for(int j = 0; !found && languageCodes[i][j] != null; j++) {
					if(code.toLowerCase().equals(languageCodes[i][j])) {
						outCode = languageCodes[i][0];
						found = true;
					}
				}
			} 
		}
		return outCode;			
	}
	*/
	
}
