package model;
import java.sql.Timestamp;
import java.util.ArrayList;

/*
 * Smap extension
 */
public class LogsDt {
	
	public int draw;
	public int recordsTotal;
	public int recordsFiltered;
	public ArrayList<LogItemDt> data = new ArrayList<LogItemDt> ();
}
