(function($) {
    $(document).ready(function() {
        var TRIM = true;
        var MAX_PAGES = 5;
        var MARGIN = 10;
        var currentPos = scrollPosition();
        var nextTextId = 1;

        $(window).scroll(function() {
            onScroll();
        });

        function onScroll() {
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

        function addMoreAtEnd() {
            var textId = 'insertedText' + nextTextId++;
            // place marker for text which may take longer to load
            var placeMarker = '<div id="' + textId + '" class="page_section"><p>Loading...</p></div>'
            $("#bottomOfBibleText").before(placeMarker);

            loadTextAtEnd(textId);
        }

        function addMoreAtTop() {
            var textId = 'insertedText' + nextTextId++;
            // place marker for text which may take longer to load
            var placeMarker = '<div id="' + textId + '" class="page_section"><p>Please wait</p><p>Loading...</p></div>';

            insertAtTop($("#topOfBibleText"), placeMarker);

            loadTextAtTop(textId);
        }

        function insertAtTop($afterComponent, text) {
            var priorHeight = bodyHeight();
            $afterComponent.after(text);
            var changeInHeight = bodyHeight() - priorHeight;
            var adjustedPosition =  currentPos + changeInHeight;
            setScrollPosition(adjustedPosition);
        }

    });
})(jQuery);

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

function bodyHeight() {
    return document.body.height;
}

function scrollPosition() {
    return window.pageYOffset;
}

function setScrollPosition(offset) {
    return window.scrollTop = offset;
}