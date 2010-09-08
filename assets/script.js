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
		window.jsInterface.log("registering:"+verseTag.id);
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
	var el = new Array();
	var tags = domNode.getElementsByTagName(tagName);
	window.jsInterface.log("Num spans found:"+tags.length);

	var tcl = " "+searchClass+" ";
	for(i=0,j=0; i<tags.length; i++) { 
		var test = " " + tags[i].className + " ";
		window.jsInterface.log("test classname:"+test);
		if (test.indexOf(tcl) != -1) 
			el[j++] = tags[i];
	} 
	return el;
} 


