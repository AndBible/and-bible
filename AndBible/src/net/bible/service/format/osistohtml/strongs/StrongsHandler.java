package net.bible.service.format.osistohtml.strongs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.bible.service.common.Constants;
import net.bible.service.common.Constants.HTML;
import net.bible.service.format.osistohtml.HtmlTextWriter;
import net.bible.service.format.osistohtml.OsisToHtmlParameters;
import net.bible.service.format.osistohtml.TagHandlerHelper;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.OSISUtil;
import org.xml.sax.Attributes;

/** 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author. 
 */
public class StrongsHandler {

	enum QType {quote, redLetter};

	private HtmlTextWriter writer;
	
	private OsisToHtmlParameters parameters;
	
	List<String> pendingStrongsAndMorphTags;

	public StrongsHandler(OsisToHtmlParameters parameters, HtmlTextWriter writer) {
		this.parameters = parameters;
		this.writer = writer;
	}
	
	
	public String getTagName() {
        return "q";
    }

	public void start(Attributes attrs) {
		if ((parameters.isShowStrongs() || parameters.isShowMorphology()) && TagHandlerHelper.isAttr(OSISUtil.ATTRIBUTE_W_LEMMA, attrs)) {
			// Strongs & morphology references
			// example of strongs refs: <w lemma="strong:H0430">God</w> <w lemma="strong:H0853 strong:H01254" morph="strongMorph:TH8804">created</w>
			// better example, because we just use Robinson: <w lemma="strong:G652" morph="robinson:N-NSM" src="2">an apostle</w>
			String strongsLemma = attrs.getValue(OSISUtil.ATTRIBUTE_W_LEMMA);
			if (strongsLemma.startsWith(OSISUtil.LEMMA_STRONGS)) {
				String morphology = attrs.getValue(OSISUtil.ATTRIBUTE_W_MORPH);
				pendingStrongsAndMorphTags = getStrongsAndMorphTags(strongsLemma, morphology);
			}
		}
	}
	
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
	private List<String> getStrongsAndMorphTags(String strongsLemma,
			String morphology) {
		// there may occasionally be more than on ref so split them into a list
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
						String display = ref.substring(OSISUtil.MORPH_ROBINSONS
								.length());

						StringBuilder tag = new StringBuilder();
						tag.append("<a href='").append(ref).append(
								"' class='morphology'>").append(display)
								.append("</a>");

						morphTags.add(tag.toString());
					}
				}
			}
		}
		return morphTags;
	}
}
