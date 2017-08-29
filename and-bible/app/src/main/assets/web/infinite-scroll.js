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
            $(window).scrollTop(changeInHeight);
        }
    });
})(jQuery);

function loadTextAtTop(textId) {
    var priorHeight = $('body').height();
    $('#' + textId).html("<div><h1>Title</h1><p>Id= " + textId + "</p><p>The cat sat on the mat</p><p>The cat sat on the mat</p></div>");
    var changeInHeight = $('body').height() - priorHeight;
    $(window).scrollTop(changeInHeight);

}

function loadTextAtEnd(textId) {
    $('#' + textId).html("<div><h1>Title</h1><p>Id= " + textId + "</p><p>The cat sat on the mat</p><p>The cat sat on the mat</p></div>");
}
