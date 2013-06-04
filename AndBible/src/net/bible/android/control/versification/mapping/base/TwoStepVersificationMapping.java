package net.bible.android.control.versification.mapping.base;

import net.bible.android.control.versification.mapping.VersificationMapping;
import net.bible.android.control.versification.mapping.VersificationMappingFactory;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.Versification;
import org.crosswire.jsword.versification.system.Versifications;

/** 
 * Support mapping between versifications without a direct mapping by combining 2 other mappings
 * 
 * E.g. German <-> KJV, KJV <-> Synodal
 *
 */
public class TwoStepVersificationMapping extends AbstractVersificationMapping {

	private Versification intermediateVersification;
	
	private VersificationMapping leftAndIntermediateMapping;
	private VersificationMapping rightAndIntermediateMapping;
	
	public TwoStepVersificationMapping(String leftVersificationName, String intermediateVersificationName, String rightVersificationName) {
		super(leftVersificationName, rightVersificationName);
		this.intermediateVersification = Versifications.instance().getVersification(intermediateVersificationName);
	}

	/**
	 * Need to initialize lazily to prevent cyclic call to VersificationMappingFactory
	 */
	private void lazyInitialisation() {
		if (leftAndIntermediateMapping==null) {
			synchronized(this) {
				if (leftAndIntermediateMapping==null) {
					final VersificationMappingFactory versificationMappingFactory = VersificationMappingFactory.getInstance();
					leftAndIntermediateMapping = versificationMappingFactory.getVersificationMapping(getLeftVersification(), intermediateVersification);
					rightAndIntermediateMapping = versificationMappingFactory.getVersificationMapping(getRightVersification(), intermediateVersification);
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see net.bible.android.control.versification.mapping.VersificationMapping#getMappedVerse(org.crosswire.jsword.passage.Verse, org.crosswire.jsword.versification.Versification)
	 */
	@Override
	public Verse getMappedVerse(Verse verse, Versification toVersification) {
		lazyInitialisation();

		boolean isForward = toVersification.equals(getRightVersification());

		Verse mappedVerse;
		if (isForward) {
			Verse intermediateVerse = leftAndIntermediateMapping.getMappedVerse(verse, intermediateVersification); 
			mappedVerse = rightAndIntermediateMapping.getMappedVerse(intermediateVerse, toVersification); 
		} else {
			Verse intermediateVerse = rightAndIntermediateMapping.getMappedVerse(verse, intermediateVersification); 
			mappedVerse = leftAndIntermediateMapping.getMappedVerse(intermediateVerse, toVersification); 
		}
		
		return mappedVerse;
	}
}
