$(window).load(
	function() {
		window.jsInterface.log("JS onload");
		window.jsInterface.onLoad();
		registerVersePositions();
//		bindTapTouchEvents();
	}
)

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
//	window.jsInterface.log("Register document height:"+document.height);
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
	var toElement = document.getElementById(toId);
	if (toElement != null) {
		toPosition = toElement.offsetTop;
		doScrollToFast(document.body, toPosition);
	}
}

function doScrollToFast(element, to) {
    element.scrollTop = to;
}

function doScrollToSlowly(element, elementPosition, to) {
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

function selectAt(x, y) {
	window.jsInterface.log("JS select at: "+x+", "+y+" WebView dimensions:"+window.innerWidth+","+window.innerHeight);

	var elem = document.elementFromPoint(x, y);

	selected(elem);
}

/** Long-press - taphold handler */
/*--- taphold start --*/
//function bindTapTouchEvents() {
//	window.jsInterface.log("Binding tap Hold");
//	$( ".verse" ).bind( "taphold", tapholdHandler );
//
//	function tapholdHandler( event ){
//		window.jsInterface.log("Tap Hold");
//		selected($(event.target))
//	}
//}

$(document).on('taphold', function(e){
	var point = {'x': holdCords.holdX, 'y': holdCords.holdY};
	var $elemSet = $('.verse');
    var $closestToPoint = $.nearest(point, $elemSet).filter(":first");
    window.jsInterface.log("Closest element: "+$closestToPoint);

    selected($closestToPoint)
});

/**
 * Unfortunately taphold does not pass the location of the touch so have to workaround as mentioned in:
 * http://stackoverflow.com/questions/14980886/jquery-mobile-clientx-and-clienty-and-the-taphold-event
 */
$(document).on('vmousedown', function(event){
    holdCords.holdX = event.pageX;
    holdCords.holdY = event.pageY;
});

var holdCords = {
    holdX : 0,
    holdY : 0
}

function selected($elem) {
	if ($elem.hasClass("verse")) {
		$elem.addClass("selected")
		var verse = parseInt($elem.attr('id'));
		window.jsInterface.log("Found verse with id: "+verse);
		window.jsInterface.verseLongPress(verse);
	}
}