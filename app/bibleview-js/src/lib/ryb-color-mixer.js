// Copied from https://github.com/camme/ryb-color-mixer (version 0.6.1). Licence: MIT.


// Real Life Color Mixer by Camilo Tapia (github.com/Camme)
// Emulate color mixing as if you where mixing real life colors, ie substractive colors
//
// Usage:
//
// RLColorMixer.mixColorS(arrayOfColors);
// where arrayOFColos is an array of hex rgb colors ['#ff0000', '#00ff00'] or an array with the amoutn of each color
// [{color: '#ff0000', parts: 10}, {color: '#00ff00', parts: 2}].
// or a mizture of the two.
//
// You can also snap to the nearest color in an array of hex rgb colors:
// RLColorMixer.findNearest(orgColorinHex, listOfColors);
//
// Example:
// RLColorMixer.findNearest('#fff000', ['#ff0000', '#ff0f00']);
//

export var rybColorMixer = {};

var defaults = { result: "ryb", hex: true };

function mix() {
    var options = JSON.parse(JSON.stringify(defaults));

    // check if the last arguments is an options object
    var lastObject = arguments[arguments.length - 1];
    if (typeof lastObject == "object" && lastObject.constructor != Array) {
        var customOptions = lastObject;
        options.result = customOptions.result || options.result;
        options.hex = typeof customOptions.hex != "undefined" ? customOptions.hex : options.hex;
        arguments.length--;
    }

    var colors = [];

    // check if we got an array, but not if the array is just a representation of hex
    if (arguments[0].constructor == Array && typeof arguments[0][0] != "number") {
        colors = arguments[0];
    } else {
        colors = arguments;
    }

    //normalize, ie make sure all colors are in the same format
    var normalized = [];
    for(var i = 0, ii = colors.length; i < ii; i++){
        var color = colors[i];
        if (typeof color == "string") {
            color = hexToArray(color);
        }
        normalized.push(color);
    }

    var newColor = mixRYB(normalized);

    if (options.result == "rgb") {
        newColor = rybToRgb(newColor);
    }

    if (options.hex) {
        newColor = arrayToHex(newColor);
    }

    return newColor;

}

function mixRYB(colors) {

    var newR = 0;
    var newY = 0;
    var newB = 0;

    var total = 0;

    var maxR = 0;
    var maxY = 0;
    var maxB = 0;

    for(var i = 0, ii = colors.length; i < ii; i++){

        var color = colors[i];

        newR += color[0];
        newY += color[1];
        newB += color[2];

    }

    // Calculate the max of all sums for each color
    var max = Math.max(newR, newY, newB);

    // Now calculate each channel as a percentage of the max
    var totalR = Math.floor(newR / max * 255);
    var totalY = Math.floor(newY / max * 255);
    var totalB = Math.floor(newB / max * 255);

    return [totalR, totalY, totalB];

}

function findNearest(color, list) {

    var listCopy = list.concat([]);

    listCopy.sort(function(c1, c2) {

        var rgb1 = hexToArray(c1);
        var rgb2 = hexToArray(c2);
        var c = hexToArray(color);

        rgb1 = rgb2hsv(rgb1);
        rgb2 = rgb2hsv(rgb2);
        c = rgb2hsv(c);

        var euclideanDistance1 = Math.sqrt(Math.pow(c[0] - rgb1[0], 2) + Math.pow(c[1] - rgb1[1], 2) + Math.pow(c[2] - rgb1[2], 2));
        var euclideanDistance2 = Math.sqrt(Math.pow(c[0] - rgb2[0], 2) + Math.pow(c[1] - rgb2[1], 2) + Math.pow(c[2] - rgb2[2], 2));
        return euclideanDistance1 - euclideanDistance2;

    });

    return listCopy[0].replace("#", "");

}

function hexToArray(hex) {
    var hex = hex.replace("#", '');
    var r = parseInt(hex.substr(0, 2), 16);
    var g = parseInt(hex.substr(2, 2), 16);
    var b = parseInt(hex.substr(4, 2), 16);
    return [r, g, b];
}


// taken from the INTERNET
function rgb2hsv (color) {
    var rr, gg, bb,
        r = color[0] / 255,
        g = color[1] / 255,
        b = color[2] / 255,
        h, s,
        v = Math.max(r, g, b),
        diff = v - Math.min(r, g, b),
        diffc = function(c){
            return (v - c) / 6 / diff + 1 / 2;
        };

    if (diff == 0) {
        h = s = 0;
    }
    else {
        s = diff / v;
        rr = diffc(r);
        gg = diffc(g);
        bb = diffc(b);

        if (r === v) {
            h = bb - gg;
        }
        else if (g === v) {
            h = (1 / 3) + rr - bb;
        }
        else if (b === v) {
            h = (2 / 3) + gg - rr;
        }
        if (h < 0) {
            h += 1;
        }
        else if (h > 1) {
            h -= 1;
        }
    }
    return [
        Math.round(h * 360),
        Math.round(s * 100),
        Math.round(v * 100)
    ];
}

function arrayToHex(rgbArray) {
    var rHex = Math.round(rgbArray[0]).toString(16); rHex = rHex.length == 1 ? "0" + rHex : rHex;
    var gHex = Math.round(rgbArray[1]).toString(16); gHex = gHex.length == 1 ? "0" + gHex : gHex;
    var bHex = Math.round(rgbArray[2]).toString(16); bHex = bHex.length == 1 ? "0" + bHex : bHex;
    return rHex + gHex + bHex;;
}

function cubicInt(t, A, B){
    var weight = t*t*(3-2*t);
    return A + weight*(B-A);
}

function getR(iR, iY, iB) {
    // red
    var x0 = cubicInt(iB, 1.0, 0.163);
    var x1 = cubicInt(iB, 1.0, 0.0);
    var x2 = cubicInt(iB, 1.0, 0.5);
    var x3 = cubicInt(iB, 1.0, 0.2);
    var y0 = cubicInt(iY, x0, x1);
    var y1 = cubicInt(iY, x2, x3);
    return Math.ceil (255 * cubicInt(iR, y0, y1));
}

function getG(iR, iY, iB) {
    // green
    var x0 = cubicInt(iB, 1.0, 0.373);
    var x1 = cubicInt(iB, 1.0, 0.66);
    var x2 = cubicInt(iB, 0.0, 0.0);
    var x3 = cubicInt(iB, 0.5, 0.094);
    var y0 = cubicInt(iY, x0, x1);
    var y1 = cubicInt(iY, x2, x3);
    return Math.ceil (255 * cubicInt(iR, y0, y1));
}

function getB(iR, iY, iB) {
    // blue
    var x0 = cubicInt(iB, 1.0, 0.6);
    var x1 = cubicInt(iB, 0.0, 0.2);
    var x2 = cubicInt(iB, 0.0, 0.5);
    var x3 = cubicInt(iB, 0.0, 0.0);
    var y0 = cubicInt(iY, x0, x1);
    var y1 = cubicInt(iY, x2, x3);
    return Math.ceil (255 * cubicInt(iR, y0, y1));
}

function rybToRgb(color, options){

    if (typeof color == "string") {
        color = hexToArray(color);
    }

    var R = color[0] / 255;
    var Y = color[1] / 255;
    var B = color[2] / 255;
    var R1 = getR(R,Y,B) ;
    var G1 = getG(R,Y,B) ;
    var B1 = getB(R,Y,B) ;
    var ret = [ R1, G1, B1 ];

    if (options && options.hex == true) {
        ret = arrayToHex(ret);
    }

    return ret;
}

function rybToRgbHex(color) {
    var rgb = rybToRgb(color);
    return arrayToHex(rgb);
}

/**
 * Return the complementary color values for a given color.
 * You must also give it the upper limit of the color values, typically 255 for
 * GUIs, 1.0 for OpenGL.
 */
function complimentary(color, limit) {
    var r = color[0], g = color[1], b = color[2];
    limit = limit || 255;
    return [limit - r, limit - g, limit - b];
}

if ( typeof define === "function" && define.amd && typeof window != "undefined") {
    define( function() {
        return rybColorMixer;
    });
} else if (typeof window != "undefined") {
    window.rybColorMixer = rybColorMixer;
} else if (module && exports) {
    module.exports = rybColorMixer;
}

rybColorMixer.mix = mix;
rybColorMixer.rybToRgb = rybToRgb;
rybColorMixer.findNearest = findNearest;

