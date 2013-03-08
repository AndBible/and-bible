function jsonload() {
	window.jsInterface.onLoad();
	registerVersePositions();
}

function jsonscroll() {
	window.jsInterface.onScroll(window.pageYOffset);
}

function registerVersePositions() {
	window.jsInterface.clearVersePositionCache();
	
	var verseTags = getVerseElements();
	window.jsInterface.log("Num verses found:"+verseTags.length);
	for (i=0; i<verseTags.length; i++) {
		verseTag = verseTags[i];
		// send position of each verse to java to allow calculation of current verse after each scroll
		window.jsInterface.registerVersePosition(verseTag.id, verseTag.offsetTop);
	}
//	window.jsInterface.log("*** Register document height:"+document.height);
//	window.jsInterface.setDocumentHeightWhenVersePositionsRegistered(document.height);
}

function getVerseElements() {
	return getElementsByClass("verse", document.body, "span")
}

function getElementsByClass( searchClass, domNode, tagName) { 
	if (domNode == null) domNode = document;
	if (tagName == null) tagName = '*';
	var matches = [];
	
	var tagMatches = domNode.getElementsByTagName(tagName);
	window.jsInterface.log("Num spans found:"+tagMatches.length);

	var searchClassPlusSpace = " "+searchClass+" ";
	for(i=0; i<tagMatches.length; i++) { 
		var tagClassPlusSpace = " " + tagMatches[i].className + " ";
		if (tagClassPlusSpace.indexOf(searchClassPlusSpace) != -1) 
			matches.push(tagMatches[i]);
	} 
	return matches;
}

function scrollTo(toId) {
	window.jsInterface.log("scrollTo called id:"+toId)
	var toElement = document.getElementById(toId);
	if (toElement != null) {
		toPosition = toElement.offsetTop;
		doScrollTo(document.body, document.body.scrollTop, toPosition);
	}
}
function doScrollTo(element, elementPosition, to) {
	// 25 pixels/100ms is the standard speed
	var speed = 25; 
    var difference = to - elementPosition;
    if (difference == 0) return;
    var perTick = Math.max(Math.min(speed, difference),-speed); 
    
    setTimeout(function() {
    	// scrolling is sometimes delayed so keep track of scrollTop rather than calling element.scrollTop
    	var newElementScrollTop = elementPosition + perTick;
        element.scrollTop = newElementScrollTop;
        doScrollTo(element, newElementScrollTop, to);
    }, 100);
}
