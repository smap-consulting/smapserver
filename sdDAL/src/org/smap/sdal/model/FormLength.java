package org.smap.sdal.model;

public class FormLength {
	public static int MAX_FORM_LENGTH = 1580;		// 1,600 for Postgres table size and allow for 20 preloads
	
	public int f_id;
	public String name;
	public int questionCount;		// Number of questions in the form
	public String lastQuestionName;
	
	public FormLength(int id, String n, int l) {
		f_id = id;
		name = n;
		questionCount = l;
	}
	
	public boolean isTooLong() {
		return questionCount > MAX_FORM_LENGTH;
	}
}
