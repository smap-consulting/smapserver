package model;

import java.util.ArrayList;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;


public class Lot {
	public String name = null;
	Sheet sheet = null;
	public ArrayList<LotRow> rows = new ArrayList<LotRow> ();
	
	public Lot(String n, Workbook wb) {
		name = n;
		sheet = wb.createSheet(name);
	}
	
	public void addRow(LotRow row) {
		rows.add(row);
	}
	
	public void writeToWorkSheet() {
		for (LotRow row : rows) {
			row.writeToWorkSheet(sheet);
		}
	}
	
}
