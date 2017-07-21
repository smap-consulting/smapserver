package org.smap.sdal.Utilities;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.managers.PDFTableManager;

import com.itextpdf.text.Anchor;
import com.itextpdf.text.BadElementException;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PushbuttonField;

public class PdfUtilities {

	private static Logger log =
			 Logger.getLogger(PDFTableManager.class.getName());
	
	public static void addImageTemplate(AcroFields pdfForm, String fieldName, String basePath, 
			String value, String serverRoot, PdfStamper stamper, Font Symbols) throws IOException, DocumentException {
		PushbuttonField ad = pdfForm.getNewPushbuttonFromField(fieldName);
		if(ad != null) {
			ad.setLayout(PushbuttonField.LAYOUT_ICON_ONLY);
			ad.setProportionalIcon(true);
			try {
				ad.setImage(Image.getInstance(basePath + "/" + value));
				pdfForm.replacePushbuttonField(fieldName, ad.getField());
			} catch (Exception e) {
				log.info("Error: Failed to add image " + basePath + "/" + value + " to pdf: " + e.getMessage());
			}
			
			log.info("Adding image to: " + fieldName);
		} else {
			
			String imageUrl = serverRoot + value;
			try {
				Rectangle targetPosition = pdfForm.getFieldPositions(fieldName).get(0).position;
			    Font fontNormal = FontFactory.getFont("Courier", 8, Font.UNDERLINE, BaseColor.BLUE);
			    Anchor url = new Anchor("\uf08e", Symbols);
			    url.setReference(imageUrl);
			    ColumnText data = new ColumnText(stamper.getOverContent(1));
			    data.setSimpleColumn(url, targetPosition.getLeft(), targetPosition.getBottom(), targetPosition.getRight(), targetPosition.getTop(), 0,0);
			    data.go();
			} catch (Exception e) {
				log.info("Field not found for: " + fieldName);
			}
		    
		    /*
			Anchor anchor = new Anchor(serverRoot + value);
			anchor.setReference(serverRoot + value);
			pdfForm.setField(fieldName, anchor.toString());  // set as hyper link
			*/
		}
	}
	
	public static void addMapImageTemplate(AcroFields pdfForm, String fieldName, Image img) throws IOException, DocumentException {
		
		PushbuttonField ad = pdfForm.getNewPushbuttonFromField(fieldName);
		if(ad != null) {
			ad.setLayout(PushbuttonField.LAYOUT_ICON_ONLY);
			ad.setProportionalIcon(true);
			try {
				ad.setImage(img);
			} catch (Exception e) {
				log.info("Error: Failed to add image to pdf: " + e.getMessage());
			}
			pdfForm.replacePushbuttonField(fieldName, ad.getField());
			log.info("Adding image to: " + fieldName);
		} else {
			//log.info("Picture field: " + fieldName + " not found");
		}
	}
	
	public static Image getMapImage(Connection sd, 
			String map, 
			String value, 
			String location, 
			String zoom,
			String mapbox_key) throws BadElementException, MalformedURLException, IOException, SQLException {
		
		Image img = null;
		
		StringBuffer url = new StringBuffer();
		boolean getMap = false;
		url.append("https://api.mapbox.com/v4/");
		if(map != null) {
			url.append(map);
		} else {
			url.append("mapbox.streets");	// default map
		}
		url.append("/");
		
		if(value != null && value.trim().length() > 0) {
			// GeoJson data
			url.append("geojson(");

			String jsonValue = value;
			url.append(URLEncoder.encode(jsonValue, "UTF-8"));
			url.append(")/");
			if(zoom != null && zoom.trim().length() > 0) {
				url.append(GeneralUtilityMethods.getGeoJsonCentroid(value) + "," + zoom);
			} else if(location != null) {
				url.append(location);
			} else {
				url.append("auto");
			}
			url.append("/");
			getMap = true;
		} else {
			// Attempt to get default map boundary from appearance
			if(location != null) {
				url.append(location);
				url.append("/");
				getMap = true;
			}					
		}
		
		if(getMap && mapbox_key == null) {
			log.info("Mapbox key not specified.  PDF Map not created");
		}
		
		if(getMap) {
			url.append("500x300.png?access_token=");
			url.append(mapbox_key);
			try {
				img = Image.getInstance(url.toString());
			} catch (Exception e) {
				log.log(Level.SEVERE, "Exception", e);
			}
		} 
		
		return img;
	}
}
