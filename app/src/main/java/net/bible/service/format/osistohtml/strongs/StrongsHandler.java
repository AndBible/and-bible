/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.format.osistohtml.strongs;

import net.bible.service.common.Constants.HTML;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.taghandler.OsisTagHandler;
import net.bible.service.format.osistohtml.taghandler.TagHandlerHelper;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 
 * Strongs tags are 'w' tags.
 * E.g.
 * 	<verse osisID='Gen.1.1'>
 *		<w lemma="strong:H07225">In the beginning</w>
 *		<w lemma="strong:H0430">God</w>
 *		<w lemma="strong:H0853 strong:H01254" morph="strongMorph:TH8804">created</w>
 *		<w lemma="strong:H08064">the heaven</w>
 *		<w lemma="strong:H0853">and</w>
 *		<w lemma="strong:H0776">the earth</w>
 *		.
 *	</verse>
 *
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class StrongsHandler implements OsisTagHandler {

	private HtmlTextWriter writer;
	
	private OsisToHtmlParameters parameters;
	
	private List<String> pendingStrongsAndMorphTags;

	public StrongsHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	@Override
	public String getTagName() {
        return OSISUtil.OSIS_ELEMENT_W;
    }

	@Override
	public void start(Attributes attrs) {
		// Strongs references
		// example of strongs refs: <w lemma="strong:H0430">God</w> <w lemma="strong:H0853 strong:H01254" morph="strongMorph:TH8804">created</w>
		// better example, because we just use Robinson: <w lemma="strong:G652" morph="robinson:N-NSM" src="2">an apostle</w>
		String strongsLemma = "";
		if (parameters.isShowStrongs() && TagHandlerHelper.isAttr(OSISUtil.ATTRIBUTE_W_LEMMA, attrs)) {
			strongsLemma = attrs.getValue(OSISUtil.ATTRIBUTE_W_LEMMA);
		}
		String morphology = "";
		if (parameters.isShowMorphology() && TagHandlerHelper.isAttr(OSISUtil.ATTRIBUTE_W_MORPH, attrs)) {
			morphology = attrs.getValue(OSISUtil.ATTRIBUTE_W_MORPH);
		}
		
		if (StringUtils.isNotBlank(strongsLemma) || StringUtils.isNotBlank(morphology)) {
			pendingStrongsAndMorphTags = getStrongsAndMorphTags(strongsLemma, morphology);
		}
	}
	
	@Override
	public void end() {
		if ((parameters.isShowStrongs() || parameters.isShowMorphology())) {
			if (pendingStrongsAndMorphTags != null) {
				for (int i = 0; i < pendingStrongsAndMorphTags.size(); i++) {
					writer.write(HTML.SPACE); // separator between adjacent tags and words
					writer.write(pendingStrongsAndMorphTags.get(i));
				}
				writer.write(HTML.SPACE); // separator between adjacent tags and words
				pendingStrongsAndMorphTags = null;
			}
		}
	}
	
	/**
	 * Convert a Strongs lemma into a url E.g. lemmas "strong:H0430",
	 * "strong:H0853 strong:H01254"
	 * 
	 * @return a single char to use as a note ref
	 */
	private List<String> getStrongsAndMorphTags(String strongsLemma, String morphology) {
		// there may occasionally be more than one ref so split them into a list
		// of single refs
		List<String> strongsTags = getStrongsTags(strongsLemma);
		List<String> morphTags = getMorphTags(morphology);

		List<String> mergedStrongsAndMorphTags = new ArrayList<String>();

		// each morph tag should relate to a Strongs tag so they should be same
		// length but can't assume that
		// merge the tags into the merge list
		for (int i = 0; i < Math.max(strongsTags.size(), morphTags.size()); i++) {
			StringBuilder merged = new StringBuilder();
			if (i < strongsTags.size()) {
				merged.append(strongsTags.get(i));
			}
			if (i < morphTags.size()) {
				merged.append(morphTags.get(i));
			}
			mergedStrongsAndMorphTags.add(merged.toString());
		}

		// for some reason the generic tags should come last and the order seems
		// always reversed in other systems
		// the second tag (once reversed) seems to relate to a missing word like
		// eth
		Collections.reverse(mergedStrongsAndMorphTags);
		return mergedStrongsAndMorphTags;
	}

	private List<String> getStrongsTags(String strongsLemma) {
		// there may occasionally be more than on ref so split them into a list
		// of single refs
		List<String> strongsTags = new ArrayList<String>();

		if (parameters.isShowStrongs()) {
			String[] refList = strongsLemma.split(" ");
			for (String ref : refList) {
				// ignore if string doesn't start with "strong;"
				if (ref.startsWith(OSISUtil.LEMMA_STRONGS)
						&& ref.length() > OSISUtil.LEMMA_STRONGS.length() + 2) {
					// reduce ref like "strong:H0430" to "H0430"
					ref = ref.substring(OSISUtil.LEMMA_STRONGS.length());

					// select Hebrew or Greek protocol
					String protocol = StrongsUtil.getStrongsProtocol(ref);

					if (protocol != null) {
						// remove initial G or H
						String strongsNumber = ref.substring(1);
						
						String strTag = StrongsUtil.createStrongsLink(protocol, strongsNumber);

						strongsTags.add(strTag);
					}
				}
			}
		}
		return strongsTags;
	}

	/**
	 * example of strongs and morphology, we just use Robinson: <w
	 * lemma="strong:G652" morph="robinson:N-NSM" src="2">an apostle</w>
	 * 
	 * @param morphology
	 * @return
	 */
	private List<String> getMorphTags(String morphology) {
		// there may occasionally be more than on ref so split them into a list
		// of single refs
		List<String> morphTags = new ArrayList<String>();

		if (parameters.isShowMorphology()) {
			if (StringUtils.isNotEmpty(morphology)) {
				String[] refList = morphology.split(" ");
				for (String ref : refList) {
					// ignore if string doesn't start with "robinson"
					if (ref.startsWith(OSISUtil.MORPH_ROBINSONS)
							&& ref.length() > OSISUtil.MORPH_ROBINSONS.length() + 2) {
						// reduce ref like "robinson:N-NSM" to "N-NSM" for
						// display
						String display = ref.substring(OSISUtil.MORPH_ROBINSONS.length());

						StringBuilder tag = new StringBuilder();
						tag.append("<a href='")
								.append(ref)
								.append("' class='morphology'>")
								.append(display)
								.append("</a>");

						morphTags.add(tag.toString());
					}
				}
			}
		}
		return morphTags;
	}
}
