package org.smap.sdal.Utilities;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.smap.sdal.managers.PDFTableManager;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.PushbuttonField;

public class PdfUtilities {

	private static Logger log =
			 Logger.getLogger(PDFTableManager.class.getName());
	
	public static void addImageTemplate(AcroFields pdfForm, String fieldName, String basePath, String value) throws IOException, DocumentException {
		PushbuttonField ad = pdfForm.getNewPushbuttonFromField(fieldName);
		if(ad != null) {
			ad.setLayout(PushbuttonField.LAYOUT_ICON_ONLY);
			ad.setProportionalIcon(true);
			try {
				ad.setImage(Image.getInstance(basePath + "/" + value));
			} catch (Exception e) {
				log.info("Error: Failed to add image " + basePath + "/" + value + " to pdf: " + e.getMessage());
				log.log(Level.SEVERE, "Image error detail", e);
			}
			pdfForm.replacePushbuttonField(fieldName, ad.getField());
			log.info("Adding image to: " + fieldName);
		} else {
			//log.info("Picture field: " + fieldName + " not found");
		}
	}
}
