package model;

import java.util.ArrayList;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;


public class LotSummary extends Lot {
	String name = null;
	Sheet sheet = null;
	
	public LotSummary(Workbook wb) {
		super("Summary", wb);
	}
}
