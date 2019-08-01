package org.smap.sdal.model;

/*
 * Form Class
 * Used for survey editing
 */
public class RoleColumnFilter implements Comparable {
	public int id;	
	
	public RoleColumnFilter() {
		
	}
	
public RoleColumnFilter(int id) {
		this.id = id;
	}

@Override
public int compareTo(Object o) {
	
	return id - (((RoleColumnFilter) o).id);
}
	
}
