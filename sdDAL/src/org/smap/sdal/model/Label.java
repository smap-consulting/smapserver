package org.smap.sdal.model;

/*
 * Form Class
 * Used for survey editing
 */
public class Label {
	public String text;
	public String hint;
	public String guidance_hint;
	public String image;
	public String audio;
	public String video;
	public String imageUrl;
	public String audioUrl;
	public String videoUrl;
	public String imageThumb;
	public String audioThumb;
	public String videoThumb;
	
	public boolean hasLabels() {
		if(text == null && hint == null && guidance_hint == null && image == null && audio == null && video == null) {
			return false;
		}
		return true;
	}
}
