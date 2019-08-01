package utilities;

import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.smap.model.FormDesc;
import org.smap.sdal.model.MetaItem;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import model.ExchangeHeader;
import utilities.SmapSheetXMLHandler.SheetContentsHandler;
import utilities.SmapSheetXMLHandler.xssfDataType;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellReference;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;

/*
Based on https://svn.apache.org/repos/asf/poi/trunk/src/examples/src/org/apache/poi/xssf/eventusermodel/XLSX2CSV.java
Provides event driven parsing of an XLS file
 */

public class XLSXEventParser {
	/**
	 * Uses the XSSF Event SAX helpers to do most of the work
	 *  of parsing the Sheet XML, and outputs the contents
	 *  as records in the database
	 */
	private class ProcessSheetHandler implements SheetContentsHandler {

		private boolean isHeader = true;
		private int currentRow = -1;
		private int currentCol = -1;
		private ArrayList<String> values = null;
		ExchangeHeader eh = new ExchangeHeader();
		boolean hasError = false;;

		Connection results;
		PreparedStatement pstmtGetCol;
		PreparedStatement pstmtGetChoices;
		PreparedStatement pstmtGetColGS;
		ArrayList<String> responseMsg;
		FormDesc form;
		ArrayList<MetaItem> preloads;
		ExchangeManager em;
		String importSource;
		Timestamp importTime;
		String serverName;
		String basePath;
		String sIdent;
		HashMap<String, File> mediaFiles;
		SimpleDateFormat sdf;

		ProcessSheetHandler(Connection results,
				FormDesc form,
				PreparedStatement pstmtGetCol,
				PreparedStatement pstmtGetChoices,
				PreparedStatement pstmtGetColGS,
				ArrayList<String> responseMsg,
				ArrayList<MetaItem> preloads,
				ExchangeManager em,
				String importSource,
				Timestamp importTime,
				String serverName,
				String basePath,
				String sIdent,
				HashMap<String, File> mediaFiles,
				SimpleDateFormat sdf) {

			this.results = results;
			this.pstmtGetCol = pstmtGetCol;
			this.pstmtGetChoices = pstmtGetChoices;
			this.pstmtGetColGS = pstmtGetColGS;
			this.responseMsg = responseMsg;
			this.form = form;
			this.preloads = preloads;
			this.em = em;
			this.importSource = importSource;
			this.importTime = importTime;
			this.serverName = serverName;
			this.basePath = basePath;
			this.sIdent = sIdent;
			this.mediaFiles = mediaFiles;
			this.sdf = sdf;
		}

		@Override
		public void startRow(int rowNum) {
			// Prepare for this row
			currentRow = rowNum;
			currentCol = -1;
			values = new ArrayList<String> ();
		}

		@Override
		public void endRow(int rowNum) {

			String[] line = values.toArray(new String[values.size()]);

			if(line.length > 0 && !hasError) {
				if(isHeader) {
					try {
						em.processHeader(results, 
								pstmtGetCol, 
								pstmtGetChoices,
								pstmtGetColGS,
								responseMsg,
								preloads,
								eh, 
								line, 
								form);
						isHeader = false;
					} catch (SQLException e) {
						responseMsg.add(e.getMessage());
						hasError = true;
						e.printStackTrace();
					}
				} else {
					try {
						recordsWritten += em.processRecord(eh, 
								line, 
								form, 
								importSource, 
								importTime, 
								responseMsg,
								serverName,
								basePath,
								sIdent,
								mediaFiles,
								sdf,
								recordsWritten);
					} catch (SQLException e) {
						responseMsg.add(e.getMessage());
						hasError = true;
						e.printStackTrace();
					}
				}
			}

		}

		@Override
		public void cell(String cellReference, String value, XSSFComment comment, 
				xssfDataType nextDataType,
				String formatString) {

			// gracefully handle missing CellRef here in a similar way as XSSFCell does
			if(cellReference == null) {
				cellReference = new CellAddress(currentRow, currentCol).formatAsString();
			}

			// Did we miss any cells?
			int thisCol = (new CellReference(cellReference)).getCol();
			int missedCols = thisCol - currentCol - 1;
			for (int i = 0; i <missedCols; i++) {
				values.add("");
			}
			currentCol = thisCol;			 
			values.add(value);

		}
	}

	/**
	 * Number of columns to read starting with leftmost
	 */
	private final OPCPackage xlsxPackage;
	private int recordsWritten;

	/**
	 * Creates a new XLSX -> CSV examples
	 *
	 * @param pkg        The XLSX package to process
	 * @param output     The PrintStream to output the CSV to
	 */
	public XLSXEventParser(OPCPackage pkg) {
		this.xlsxPackage = pkg;
	}

	/**
	 * Get the sheet names in the XLSX file
	 *
	 * @throws IOException If reading the data from the package fails.
	 * @throws SAXException if parsing the XML data fails.
	 */
	public ArrayList<String> getSheetNames() throws IOException, OpenXML4JException, SAXException {
		XSSFReader xssfReader = new XSSFReader(this.xlsxPackage);
		XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();

		ArrayList<String> sheetNames = new ArrayList<String> ();
		while (iter.hasNext()) {
			try (InputStream stream = iter.next()) {
				String name = iter.getSheetName();
				if(name.startsWith("d_")) {
					// Legacy forms remove prefix added by older results exports  30th January 2018
					name = name.substring(2);
				}
				sheetNames.add(name);
			}
		}
		return sheetNames;
	}

	/**
	 * Process the specified sheet
	 * @return 
	 *
	 * @throws IOException If reading the data from the package fails.
	 * @throws SAXException if parsing the XML data fails.
	 * @throws SQLException 
	 */
	public int processSheet(Connection results, 
			PreparedStatement pstmtGetCol, 
			PreparedStatement pstmtGetChoices,
			PreparedStatement pstmtGetColGS, 
			ArrayList<String> responseMsg,
			FormDesc form,
			ArrayList<MetaItem> preloads,
			ExchangeManager em,
			String importSource,
			Timestamp importTime,
			String serverName,
			String basePath,
			String sIdent,
			HashMap<String, File> mediaFiles,
			SimpleDateFormat sdf) throws IOException, OpenXML4JException, SAXException, SQLException {

		ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(this.xlsxPackage);
		XSSFReader xssfReader = new XSSFReader(this.xlsxPackage);
		StylesTable styles = xssfReader.getStylesTable();
		XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();

		recordsWritten = 0;

		form.keyMap = new HashMap<String, String> ();
		pstmtGetCol.setInt(1, form.f_id);		// Prepare the statement to get column names for the form
		pstmtGetColGS.setInt(1, form.f_id);		// Prepare the statement to get column names for the form


		while (iter.hasNext()) {
			try (InputStream stream = iter.next()) {
				String sheetName = iter.getSheetName();
				if(sheetName.equals(form.name) || sheetName.equals("d_" + form.name)) {
					DataFormatter formatter = new DataFormatter();
					InputSource sheetSource = new InputSource(stream);
					try {
						XMLReader sheetParser = SAXHelper.newXMLReader();
						ContentHandler handler = new SmapSheetXMLHandler(
								styles, null, strings, 
								(SheetContentsHandler) new ProcessSheetHandler(results, form,
										pstmtGetCol, pstmtGetChoices, pstmtGetColGS, responseMsg, 
										preloads, em,
										importSource,
										importTime,
										serverName,
										basePath,
										sIdent,
										mediaFiles,
										sdf), 
								formatter, 
								false,
								sdf);
						sheetParser.setContentHandler(handler);
						sheetParser.parse(sheetSource);
					} catch(ParserConfigurationException e) {
						throw new RuntimeException("SAX parser appears to be broken - " + e.getMessage());
					}
				}
			}

		}

		return recordsWritten;
	}


}
