package model;

public class SummarySqlCreator {
	
	String type;
	String group;
	String x;
	String period;
	
	public SummarySqlCreator(String type, String group, String x, String period) {
		this.type = type;
		this.group = group;
		this.x = x;
		this.period = period;
	}
	
	/*
	 * The columns in the select should be:
	 *    xvalue	string
	 *    group		string
	 *    value		int
	 */
	public String getSQL() throws Exception {
		
		StringBuffer sql = new StringBuffer();
		
		String xCol = null;
		String xSel = null;
		String valueCol = null;
		String tables = null;
		String join = null;
		
		/*
		 * Set up repeating elements
		 */
		
		if(type.equals("tasks")) {
			
			// Get the x column
			if(x.equals("scheduled")) {
				xCol = "schedule_at";
			} else {
				throw new Exception("Unknown value for x axis: " + x);
			}
			
			tables = "assignments, tasks";
			join = "tasks.id = assignments.task_id";
		} else {
			throw new Exception("Unknown summary type: " + period);
		}
		
		// Get the value column
		valueCol = "count(*)";
		
		if(period != null) {
			if(period.equals("day")) {
				xSel = "extract(year from " + xCol + 
						") || '-' || extract(month from " + xCol +
						") || '-' || extract(day from " + xCol + 
						")";
			} else 	if(period.equals("month")) {
				xSel = "extract(year from " + xCol + 
						") || '-' || extract(month from " + xCol +
						")";
			} else 	if(period.equals("year")) {
				xSel = "extract(year from " + xCol + 
						")";
			} else if(period.equals("week")) {
				xSel = "extract(year from " + xCol + 
						") || '-' || extract(week from " + xCol +
						")";
			} else {
				throw new Exception("Unknown period value: " + period);
			}
		} else {
			xSel = xCol;
		}
		
		
		/*
		 * Construct sql
		 */
		sql.append("select ");
		
		// Add x axis value
		sql.append(xSel);
		sql.append(" as xvalue");
		
		// Add group value
		sql.append(",");
		sql.append(group);
		sql.append(" as group");
		
		// Add data value
		sql.append(",");
		sql.append(valueCol);
		sql.append(" as value");
		
		// Add from
		sql.append(" from ");
		sql.append(tables);
		
		// Add where
		sql.append(" where ");
		if(join != null) {
			sql.append(join);
		}
		
		// Add group by
		sql.append(" group by ");
		sql.append(xSel);
		sql.append(",");
		sql.append(group);
		
		// Add order by
		sql.append(" order by ");
		sql.append(xSel);
		sql.append(",");
		sql.append(group);
		
		sql.append(";");
		
		return sql.toString();
	}
}
