function getNum() {
	return 300;
}

function jsonload() {
	window.jsInterface.onLoad();
	registerVersePositions();
}

function jsonscroll() {
	window.jsInterface.onScroll(window.pageYOffset);
}

function registerVersePositions() {
	var verseTags = getVerseElements();
	window.jsInterface.log("Num verses found:"+verseTags.length);
	for (i=0; i<verseTags.length; i++) {
		verseTag = verseTags[i];
		// send position of each verse to java to allow calculation of current verse after each scroll
		window.jsInterface.registerVersePosition(verseTag.id, verseTag.offsetTop);
	}
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


