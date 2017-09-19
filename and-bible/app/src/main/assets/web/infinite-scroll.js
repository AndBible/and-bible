/**
 * WebView js functions for continuous scrolling up and down between chapters
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
(function($) {
    $.fn.infiniScroll = function(fnLoadTextAtTop, fnLoadTextAtEnd, initialId, minId, maxId, insertAfterAtTop, insertBeforeAtBottom) {
        var TRIM = true;
        var MAX_PAGES = 5;
        var MARGIN = 200;
        var currentPos = scrollPosition();

		var topId = initialId;
		var endId = initialId;

		var scrollHandler = function() {
            previousPos = currentPos;
            currentPos = scrollPosition();
            var scrollingUp = currentPos < previousPos;
            var scrollingDown = currentPos > previousPos;
            if (scrollingDown && currentPos >= ($('#bottomOfBibleText').offset().top - $(window).height()) - MARGIN) {
                addMoreAtEnd();
            } else if (scrollingUp && currentPos < MARGIN) {
                addMoreAtTop();
            }
            currentPos = scrollPosition();
        }

		// Could add start() and stop() methods
		$(window).scroll(scrollHandler);
		//$(window).unbind("scroll", scrollHandler);

        function addMoreAtEnd() {
			if (endId<maxId && document.getElementById("stillLoadingId")==null) {
				var id = ++endId
				var textId = 'insertedText' + id;
				// place marker for text which may take longer to load
				var placeMarker = '<div id="' + textId + '" class="page_section"><p id="stillLoadingId">Loading...</p></div>'
				$(insertBeforeAtBottom).before(placeMarker);

				fnLoadTextAtEnd(id, textId);
			}
        }

        function addMoreAtTop() {
			if (topId>minId && document.getElementById("stillLoadingId")==null) {
				var id = --topId
				var textId = 'insertedText' + id;
				// place marker for text which may take longer to load
				var placeMarker = '<div id="' + textId + '" class="page_section"><p id="stillLoadingId" style="height: 100px">&nbsp;</p></div>';
				insertAtTop($(insertAfterAtTop), placeMarker);

				fnLoadTextAtTop(id, textId);
			}
        }

        function insertAtTop($afterComponent, text) {
            var priorHeight = bodyHeight();
            $afterComponent.after(text);
            var changeInHeight = bodyHeight() - priorHeight;
            var adjustedPosition =  currentPos + changeInHeight;
            setScrollPosition(adjustedPosition);
        }
    };
})(jQuery);

$(document).ready(function() {
    var chapterInfo = JSON.parse(window.jsInterface.getChapterInfo());
    if (chapterInfo.infinite_scroll) {
    	$.fn.infiniScroll(loadTextAtTop, loadTextAtEnd, chapterInfo.chapter, chapterInfo.first_chapter, chapterInfo.last_chapter, "#topOfBibleText", "#bottomOfBibleText");
    }
});

function bodyHeight() {
    return document.body.scrollHeight;
}

function scrollPosition() {
    return window.pageYOffset;
}

function setScrollPosition(offset) {
    // Android 6 fails with window.scrollTop = offset but jquery works
    $(window).scrollTop(offset);
}

/**
 * Ask java to get more text to be loaded into page
 */
function loadTextAtTop(chapter, textId) {
    window.jsInterface.log("js:loadTextAtTop");
    window.jsInterface.requestMoreTextAtTop(chapter, textId);
}

function loadTextAtEnd(chapter, textId) {
    window.jsInterface.log("js:loadTextAtEnd");
    window.jsInterface.requestMoreTextAtEnd(chapter, textId);
}

//TODO combine these 2 functions - check if inserted posn (#textId) is at top or not
/**
 * called from java after actual text has been retrieved to request text is inserted
 */
function insertThisTextAtTop(textId, text) {
    var priorHeight = bodyHeight();
	var origPosition = scrollPosition();

    var $divToInsertInto = $('#' + textId);
    $divToInsertInto.html(text);

    // do no try to get scrollPosition here becasue it has not settled
    var adjustedTop = origPosition - priorHeight + bodyHeight();
    setScrollPosition(adjustedTop);

    registerVersePositions();
}

function insertThisTextAtEnd(textId, text) {
    window.jsInterface.log("js:insertThisTextAtEnd into:"+textId);
    $('#' + textId).html(text);

    registerVersePositions();
}