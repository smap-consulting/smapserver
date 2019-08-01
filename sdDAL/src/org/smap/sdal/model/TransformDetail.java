package org.smap.sdal.model;

import java.util.ArrayList;

/*
 * Transformation details
 * 
 * splitterQuestion has multiple values specified in the values list
 * There are multiple columns that will then get transformed
 * Hence a splitter question which has 2 values and 3 transformation columns would be converted into
 * column_0 - value_0, column_0 - value_1, column_1 - value_0, column_1 - value_1, column_2 - value_0, column_2 - value_1
 */
public class TransformDetail {	
	
	public String valuesQuestion;
	public ArrayList<String> values = new ArrayList<> ();			// Each value is applied to each column
	public ArrayList<String> wideColumns = new ArrayList<> ();
	
}
