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
            var placeMarker = '<div id="' + textId + '" class="page_section"><p>Loading...</p></div>';

            insertAtTop($("#topOfBibleText"), placeMarker);

            loadTextAtTop(textId);
        }

        function insertAtTop($afterComponent, text) {
            var priorHeight = $('body').height();
            $afterComponent.after(text);
            var changeInHeight = $('body').height() - priorHeight;
            var adjustedPosition = $(window).scrollTop() + changeInHeight;
            $(window).scrollTop(changeInHeight);
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
    window.jsInterface.log("js:insertThisTextAtTop");
    var divToInsertInto = $('#' + textId);
    var divPriorHeight = divToInsertInto.height();
    $('#' + textId).html(text);
    var adjustedTop = $(window).scrollTop() - divPriorHeight + divToInsertInto.height();
    $(window).scrollTop(adjustedTop);
}
function insertThisTextAtEnd(textId, text) {
    window.jsInterface.log("js:insertThisTextAtEnd");
    $('#' + textId).html(text);
}
