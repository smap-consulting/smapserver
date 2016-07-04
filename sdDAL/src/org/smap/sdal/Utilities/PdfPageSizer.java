package org.smap.sdal.Utilities;

import java.io.File;
import java.util.logging.Logger;

import org.smap.sdal.managers.PDFTableManager;
import org.smap.sdal.model.User;

import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;

/*
 * Class to write headers and footers on each page
 */
public class PdfPageSizer extends PdfPageEventHelper {
	int pagenumber = 0;
	User user = null;
	String title;
	String project;
	String basePath;
	int marginLeft;
	int marginRight;
	int marginTop_2;
	int marginBottom_2;
	
	private static Logger log =
			 Logger.getLogger(PDFTableManager.class.getName());
	
	public PdfPageSizer(String title, String project, User user, String basePath,
			int marginLeft,
			int marginRight,
			int marginTop_2,
			int marginBottom_2) {
		
		super();
		
		this.title = title;
		this.project = project;
		this.user = user;
		this.basePath = basePath;
		this.marginLeft = marginLeft;
		this.marginRight = marginRight;
		this.marginTop_2 = marginTop_2;
		this.marginBottom_2 = marginBottom_2;
		
	}
	
	public void onStartPage(PdfWriter writer, Document document) {
		pagenumber++;

		document.setMargins(marginLeft, marginRight, marginTop_2, marginBottom_2);
		
		//if(pageNumber > 1) {
		//	writer.setCropBoxSize(new Rectangle(marginLeft, marginRight, marginTop_2, marginBottom_2));
		//}
	}
	public void onEndPage(PdfWriter writer, Document document) {
		
		Rectangle pageRect = writer.getPageSize();
		
		// Write header on first page only
		if(pagenumber == 1) {
			
			// Add Title
			Font titleFont = new Font();
			titleFont.setSize(18);
			Phrase titlePhrase = new Phrase();
			titlePhrase.setFont(titleFont);
			titlePhrase.add(title);
			ColumnText.showTextAligned(writer.getDirectContent(), 
					Element.ALIGN_CENTER, titlePhrase, 
					(pageRect.getLeft() + pageRect.getRight()) /2, pageRect.getTop() - 100, 0);
			
			// Add Project
			Phrase projectPhrase = new Phrase();
			Font projectFont = new Font();
			projectFont.setSize(14);
			projectPhrase.setFont(projectFont);
			projectPhrase.add("Project: " +  project);
			ColumnText.showTextAligned(writer.getDirectContent(), 
					Element.ALIGN_LEFT, projectPhrase, 
					pageRect.getLeft() + marginLeft, pageRect.getTop() - 120, 0);
			
			
			if(user != null) {
				// Show the logo
				String fileName = null;
				try {
					fileName = basePath + File.separator + "media" + File.separator +
						"organisation" + File.separator + user.o_id + File.separator +
						"settings" + File.separator + "bannerLogo";

						Image img = Image.getInstance(fileName);
						img.scaleToFit(200, 50);
						float w = img.getScaledWidth();
						img.setAbsolutePosition(
					            pageRect.getRight() - (marginRight + w),
					            pageRect.getTop() - 75);
					        document.add(img);
						
				} catch (Exception e) {
					log.info("Error: Failed to add image " + fileName + " to pdf");
				}
			}
		}
		
		// Footer is always written
		if(user != null) {
			// Add organisation
			ColumnText.showTextAligned(writer.getDirectContent(), 
					Element.ALIGN_CENTER, new Phrase(user.company_name), 
					(pageRect.getLeft() + pageRect.getRight()) /2, pageRect.getBottom() + 80, 0);
			// Add organisation address
			ColumnText.showTextAligned(writer.getDirectContent(), 
					Element.ALIGN_CENTER, new Phrase(user.company_address), 
					(pageRect.getLeft() + pageRect.getRight()) /2, pageRect.getBottom() + 65, 0);
			ColumnText.showTextAligned(writer.getDirectContent(), 
					Element.ALIGN_CENTER, new Phrase(user.company_phone), 
					(pageRect.getLeft() + pageRect.getRight()) /4, pageRect.getBottom() + 50, 0);
			ColumnText.showTextAligned(writer.getDirectContent(), 
					Element.ALIGN_CENTER, new Phrase(user.company_email), 
					(pageRect.getLeft() + pageRect.getRight()) * 3 / 4, pageRect.getBottom() + 50, 0);
		}
		
		// Add page number
		ColumnText.showTextAligned(writer.getDirectContent(), 
				Element.ALIGN_CENTER, new Phrase(String.format("page %d", pagenumber)), 
				pageRect.getRight() - 100, pageRect.getBottom() + 25, 0);
	}
}
