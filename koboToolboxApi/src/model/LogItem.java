package model;
import java.sql.Timestamp;

/*
 * Smap extension
 */
public class LogItem {
	
	public int id;
	public Timestamp log_time;
	public int sId;
	public String sName;
	public String userIdent;
	public String event;
	public String note;
}
