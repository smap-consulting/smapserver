package model;

import java.sql.Date;
import java.util.ArrayList;

public class Settings {
	public int id;
	public int seq;
	public String state;
	public String title;
	public int pId;
	public int sId;
	public String sName;
	public String type;
	public int layerId;
	public String region;
	public String lang;
	public int qId;
	public boolean qId_is_calc;
	public int dateQuestionId;
	public String question;
	public String fn;
	public String table;
	public String q1_function;		// Aggregation function for question 1
	public String key_words;		// Twitter key words
	public int groupQuestionId;
	public String groupQuestionText;
	public String groupType;
	public String timeGroup;
	public Date fromDate;
	public Date toDate;
	public String filter;
}
