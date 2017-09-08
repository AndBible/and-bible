(function($) {
    $(document).ready(function() {
        var TRIM = true;
        var MAX_PAGES = 5;
        var MARGIN = 10;
        var currentPos = $(window).scrollTop();
        var nextTextId = 1;

        $(window).scroll(function() {
            onScroll();
        });

        function onScroll() {
            previousPos = currentPos;
            currentPos = $(window).scrollTop();
            var scrollingUp = currentPos < previousPos;
            var scrollingDown = currentPos > previousPos;
            if (scrollingDown && currentPos >= ($('#bottomOfBibleText').offset().top - $(window).height()) - MARGIN) {
                addMoreAtEnd();
            } else if (scrollingUp && currentPos < MARGIN) {
                addMoreAtTop();
            }
            currentPos = $(window).scrollTop();
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
            var priorHeight = $('body').height();
            var originalPosition = $(window).scrollTop();
            $afterComponent.after(text);
            var changeInHeight = $('body').height() - priorHeight;
            var adjustedPosition =  originalPosition + changeInHeight;
            $(window).scrollTop(adjustedPosition);
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
    var originalPosition = $(window).scrollTop();
    var $divToInsertInto = $('#' + textId);
    var divPriorHeight = $divToInsertInto.height();
    $divToInsertInto.html(text);
    var adjustedTop = originalPosition - divPriorHeight + $divToInsertInto.height();
    $(window).scrollTop(adjustedTop);

    registerVersePositions();
}
function insertThisTextAtEnd(textId, text) {
    window.jsInterface.log("js:insertThisTextAtEnd into:"+textId);
    $('#' + textId).html(text);

    registerVersePositions();
}
