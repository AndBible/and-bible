/**
 * WebView js functions for continuous scrolling up and down between chapters
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
(function($) {
    $.fn.infiniScroll = function(fnLoadTextAtTop, fnLoadTextAtEnd, initialId, minId, maxId, insertAfterAtTop, insertBeforeAtBottom) {
        // up is very hard when still scrolling so make the margin tiny to cause scroll to stop before pre-filling up
        var UP_MARGIN = 2;
        var DOWN_MARGIN = 200;
        var currentPos = scrollPosition();

		var topId = initialId;
		var endId = initialId;

        var lastAddMoreTime = 0;
        var addMoreAtTopOnTouchUp = false;

		var scrollHandler = function() {
            previousPos = currentPos;
            currentPos = scrollPosition();
            var scrollingUp = currentPos < previousPos;
            var scrollingDown = currentPos > previousPos;
            if (scrollingDown && currentPos >= ($('#bottomOfBibleText').offset().top - $(window).height()) - DOWN_MARGIN && Date.now()>lastAddMoreTime+1000) {
                lastAddMoreTime = Date.now()
                addMoreAtEnd();
            } else if (scrollingUp && currentPos < UP_MARGIN && Date.now()>lastAddMoreTime+1000) {
                lastAddMoreTime = Date.now()
                addMoreAtTop();
            }
            currentPos = scrollPosition();
        }

		// Could add start() and stop() methods
		$(window).scroll(scrollHandler);
		//$(window).unbind("scroll", scrollHandler);
    	 window.addEventListener('touchstart', touchstartListener, false);
         window.addEventListener('touchend', touchendListener, false);
         window.addEventListener("touchcancel", touchendListener, false);

        function addMoreAtEnd() {
			if (endId<maxId && !stillLoading) {
			    stillLoading = true;
				var id = ++endId;
				var textId = 'insertedText' + id;
				// place marker for text which may take longer to load
				var placeMarker = '<div id="' + textId + '" class="page_section">&nbsp;</div>';
				$(insertBeforeAtBottom).before(placeMarker);

				fnLoadTextAtEnd(id, textId);
			}
        }

        function addMoreAtTop() {
            if (touchDown) {
                // adding at top is tricky and if the user is stil holding there seems no way to set the scroll position after insert
                addMoreAtTopOnTouchUp = true;
            } else if (topId>minId && !stillLoading) {
			    stillLoading = true;
			    var id = --topId;
				var textId = 'insertedText' + id;
				// place marker for text which may take longer to load
				var placeMarker = '<div id="' + textId + '" class="page_section">&nbsp;</div>';
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

        function touchstartListener(event){
            touchDown = true;
        }
        function touchendListener(event){
            touchDown = false;
            if (textToBeInsertedAtTop && idToInsertTextAt) {
                var text = textToBeInsertedAtTop;
                var id = idToInsertTextAt;
                textToBeInsertedAtTop = null;
                idToInsertTextAt = null;
                insertThisTextAtTop(id, text);
            }
            if (addMoreAtTopOnTouchUp) {
                addMoreAtTopOnTouchUp = false;
                addMoreAtTop()
            }
        }
    };
})(jQuery);

var stillLoading = false;
var touchDown = false;
var textToBeInsertedAtTop = null;
var idToInsertTextAt = null;

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

/**
 * called from java after actual text has been retrieved to request text is inserted
 */
function insertThisTextAtTop(textId, text) {
    if (touchDown) {
        textToBeInsertedAtTop = text;
        idToInsertTextAt = textId;
    } else {
        var priorHeight = bodyHeight();
        var origPosition = scrollPosition();

        var $divToInsertInto = $('#' + textId);
        $divToInsertInto.html(text);

        // do no try to get scrollPosition here becasue it has not settled
        var adjustedTop = origPosition - priorHeight + bodyHeight();
        setScrollPosition(adjustedTop);

        registerVersePositions();
        stillLoading = false;
    }
}

function insertThisTextAtEnd(textId, text) {
    window.jsInterface.log("js:insertThisTextAtEnd into:"+textId);
    $('#' + textId).html(text);

    registerVersePositions();
    stillLoading = false;
}