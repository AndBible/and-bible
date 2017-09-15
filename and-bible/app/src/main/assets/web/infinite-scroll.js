(function($) {
    $.fn.infiniScroll = function(fnLoadTextAtTop, fnLoadTextAtEnd, initialId, minId, maxId) {
        var TRIM = true;
        var MAX_PAGES = 5;
        var MARGIN = 10;
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

		//TODO add start() and stop() methods
		$(window).scroll(scrollHandler);
		//$(window).unbind("scroll", scrollHandler);

        function addMoreAtEnd() {
			if (endId<maxId) {
				endId++
				var textId = 'insertedText' + endId;
				// place marker for text which may take longer to load
				var placeMarker = '<div id="' + textId + '" class="page_section"><p>Loading...</p></div>'
				$("#bottomOfBibleText").before(placeMarker);

				fnLoadTextAtEnd(textId);
			}
        }

        function addMoreAtTop() {
			if (topId>minId) {
				topId--
				var textId = 'insertedText' + topId;
				// place marker for text which may take longer to load
				var placeMarker = '<div id="' + textId + '" class="page_section"><p style="height: 1000px"></p></div>';
				insertAtTop($("#topOfBibleText"), placeMarker);

				fnLoadTextAtTop(textId);
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
	$.fn.infiniScroll(loadTextAtTop, loadTextAtEnd, chapterInfo.chapter, chapterInfo.first_chapter, chapterInfo.last_chapter);
});

function bodyHeight() {
    return document.body.height;
}

function scrollPosition() {
    return window.pageYOffset;
}

function setScrollPosition(offset) {
    return window.scrollTop = offset;
}

function loadTextAtTop(textId) {
    window.jsInterface.log("js:loadTextAtTop");
    window.jsInterface.requestMoreTextAtTop(textId);
}

function loadTextAtEnd(textId) {
    window.jsInterface.log("js:loadTextAtEnd");
    window.jsInterface.requestMoreTextAtEnd(textId);
}

//TODO combine these 2 functions - check if inserted posn (#textId) is at top or not
function insertThisTextAtTop(textId, text) {
    window.jsInterface.log("js:insertThisTextAtTop into:"+textId);
    var priorHeight = bodyHeight();
    var origPosition = scrollPosition();

    var $divToInsertInto = $('#' + textId);
    $divToInsertInto.html(text);

    var changeInHeight = bodyHeight() - priorHeight;
    var adjustedPosition =  origPosition + changeInHeight;
    setScrollPosition(adjustedPosition);

    registerVersePositions();
}
function insertThisTextAtEnd(textId, text) {
    window.jsInterface.log("js:insertThisTextAtEnd into:"+textId);
    $('#' + textId).html(text);

    registerVersePositions();
}
