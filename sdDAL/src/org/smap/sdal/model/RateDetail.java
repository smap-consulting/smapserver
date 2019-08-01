package org.smap.sdal.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class RateDetail {
	
	public int oId;
	public int eId;
	public LocalDate appliesFrom;	// Month bill starts from
	public LocalDateTime modified;
	public String modifiedBy;
	public String currency;			// Currency of rate
	
	public ArrayList<BillLineItem> line = null;

}
