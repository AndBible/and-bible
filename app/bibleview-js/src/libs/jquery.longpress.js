/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 */
import jQuery from "jquery"

(function($) {
    $.fn.longpress = function(longCallback, shortCallback, duration) {
        if (typeof duration === "undefined") {
            duration = 500;
        }

        return this.each(function() {
            var $this = $(this);

            // to keep track of how long something was pressed
            var mouse_down_time;
            var timeout;

            // mousedown or touchstart callback
            function mousedown_callback(e) {
            	// Save pageX and pageY
            	var touch = e.originalEvent.touches[0] || e.originalEvent.changedTouches[0];
            	e.pageX = touch.pageX;
            	e.pageY = touch.pageY;

                mouse_down_time = new Date().getTime();
                var context = $(this);

                // set a timeout to call the longpress callback when time elapses
                timeout = setTimeout(function() {
                    if (typeof longCallback === "function") {
                        longCallback.call(context, e);
                    } else {
                        $.error('Callback required for long press. You provided: ' + typeof longCallback);
                    }
                }, duration);
            }

            // mouseup or touchend callback
            function mouseup_callback(e) {
                var press_time = new Date().getTime() - mouse_down_time;
                if (press_time < duration) {
                    // cancel the timeout
                    clearTimeout(timeout);

                    // call the shortCallback if provided
                    if (typeof shortCallback === "function") {
                        shortCallback.call($(this), e);
                    } else if (typeof shortCallback === "undefined") {
                        
                    } else {
                        $.error('Optional callback for short press should be a function.');
                    }
                }
            }

            // cancel long press event if the finger or mouse was moved
            function move_callback(e) {
                clearTimeout(timeout);
            }

            // Mobile Support
            $this.on('touchstart', mousedown_callback);
            $this.on('touchend', mouseup_callback);
            $this.on('touchmove', move_callback);
        });
    };
}(jQuery));
