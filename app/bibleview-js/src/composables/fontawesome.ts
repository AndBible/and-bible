/*
 * Copyright (c) 2021-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

import {IconDefinition, library} from "@fortawesome/fontawesome-svg-core";
import {
    faArrowsAltV,
    faBookmark,
    faCheck,
    faChevronCircleDown,
    faCompressArrowsAlt,
    faEdit,
    faEllipsisH,
    faExpandArrowsAlt,
    faEye,
    faEyeSlash,
    faFileAlt,
    faFireAlt,
    faHandPointer,
    faHeadphones,
    faHeart,
    faHistory,
    faIndent,
    faInfoCircle,
    faOutdent,
    faPenSquare,
    faPlus,
    faPlusCircle,
    faQuestionCircle,
    faSave,
    faShareAlt,
    faSort,
    faTags,
    faTextWidth,
    faTimes,
    faTrash,
} from "@fortawesome/free-solid-svg-icons";

import {
    faClock
} from "@fortawesome/free-regular-svg-icons";

export function useFontAwesome() {
    const customWholeVerseFalse: IconDefinition = {
        prefix: 'fas',
        // @ts-ignore
        iconName: 'custom-whole-verse-false',
        icon: [100, 100, [], "", "m19.6 33.7a4.3 4.3 0 0 0-4.3 4.3 4.3 4.3 0 0 0 4.3 4.3H63.2A4.3 4.3 0 0 0 67.5 38 4.3 4.3 0 0 0 63.2 33.7Zm-0.2-16a4.3 4.3 0 0 0-4.2 4.2 4.3 4.3 0 0 0 4.2 4.2h60.9a4.3 4.3 0 0 0 4.2-4.2 4.3 4.3 0 0 0-4.2-4.2zM0 0V100H100V0ZM8 8H92.2V91.9H8ZM72.7 52.8 66.3 57.5 71 65.9H29.2l4.6-8.4-6.5-4.7-10.6 17 11 17 6.3-4.9-4.6-8.1h41.5l-4.6 8.1 6.6 4.8 10.6-16.9z"]
    };
    const customWholeVerseTrue: IconDefinition = {
        prefix: 'fas',
        // @ts-ignore
        iconName: 'custom-whole-verse-true',
        icon: [100, 100, [], "", "M0 0V100H100V0ZM19.4 17.7h60.9a4.3 4.3 0 0 1 4.2 4.2 4.3 4.3 0 0 1-4.2 4.2H19.4a4.3 4.3 0 0 1-4.2-4.2 4.3 4.3 0 0 1 4.2-4.2zm0.2 16h43.6a4.3 4.3 0 0 1 4.3 4.3 4.3 4.3 0 0 1-4.3 4.3H19.6A4.3 4.3 0 0 1 15.3 38 4.3 4.3 0 0 1 19.6 33.7Zm7.7 19.1 6.5 4.7-4.6 8.4H71l-4.7-8.4 6.4-4.7 10.8 17-10.6 16.9-6.6-4.8 4.6-8.1H29.4l4.6 8.1-6.3 4.9-11-17z"]
    };

    const customStrongs: IconDefinition = {
        prefix: 'fas',
        // @ts-ignore
        iconName: 'custom-morph',
        icon: [100, 100, [], "", "M91 82.4H88.1V67.6c0-1.6-1.4-3-2.9-3H53v-7.4c2.5-1.3 8.8-4.5 14.6-4.5 7.8 0 15.8 5.3 16 5.3 0.8 0.8 1.9 0.8 2.9 0.2 1-0.4 1.6-1.4 1.6-2.5V8.9C88.1 7.9 87.5 7 86.7 6.4 86.3 6.2 77.2 0.1 67.6 0.1 60.4 0.1 53.2 3.7 50.1 5.4 46.9 3.7 39.7 0.1 32.5 0.1 23 0.1 13.7 6.2 13.3 6.4 12.5 7 12 7.9 12 8.9V55.7c0 1.1 0.6 2.1 1.5 2.5 1 0.6 2.1 0.6 3.1-0.2 0.1 0 8.1-5.3 15.9-5.3 5.9 0 12.1 3.2 14.6 4.5v7.4H14.9c-1.6 0-2.9 1.4-2.9 3V82.4H9.1c-1.6 0-2.9 1.4-2.9 2.9V97c0 1.6 1.3 2.9 2.9 2.9H20.8c1.6 0 2.9-1.3 2.9-2.9V85.3c0-1.5-1.3-2.9-2.9-2.9H17.9V70.5h29.2v11.9h-2.9c-1.5 0-2.9 1.4-2.9 2.9V97c0 1.6 1.4 2.9 2.9 2.9h11.7c1.6 0 2.9-1.3 2.9-2.9V85.3c0-1.5-1.3-2.9-2.9-2.9H53V70.5h29.2v11.9h-2.9c-1.6 0-2.9 1.4-2.9 2.9V97c0 1.6 1.3 2.9 2.9 2.9H91c1.6 0 2.9-1.3 2.9-2.9V85.3c0-1.5-1.3-2.9-2.9-2.9zM82.2 10.5v40.3c-3.7-2-9.1-3.9-14.6-3.9-5.4 0-10.9 1.9-14.6 3.9V10.5c2.5-1.5 8.8-4.5 14.6-4.5 5.9 0 12.1 3.1 14.6 4.6zM32.5 46.9c-5.4 0-10.9 1.9-14.6 3.9V10.5c2.5-1.5 8.8-4.5 14.6-4.5 5.9 0 12.1 3.1 14.6 4.6V50.8C43.4 48.8 38 46.9 32.5 46.9ZM17.9 94.1H12v-5.9h5.9zm35.1 0H47.1V88.2H53Zm35.1 0h-5.9v-5.9h5.9zM26.7 18.2c0.2 0 0.4 0 0.5-0.1 1.8-0.3 3.6-0.4 5.3-0.4 1.8 0 3.5 0.1 5.3 0.4 1.7 0.3 3.1-0.8 3.5-2.4 0.2-1.5-0.8-3.1-2.4-3.3-2.3-0.4-4.2-0.6-6.4-0.6-1.9 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.8-2.4 3.3 0.2 1.5 1.6 2.5 3 2.5zm35.1 0c0.2 0 0.4 0 0.5-0.1 1.8-0.3 3.6-0.4 5.3-0.4 1.8 0 3.5 0.1 5.3 0.4 1.7 0.3 3.1-0.8 3.5-2.4 0.2-1.5-0.8-3.1-2.4-3.3-2.3-0.4-4.2-0.6-6.4-0.6-1.9 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.8-2.4 3.3 0.2 1.5 1.6 2.5 3 2.5zm-22.9 5.9c-2.3-0.4-4.2-0.6-6.4-0.6-1.9 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.7-2.4 3.3 0.2 1.6 1.6 2.5 3 2.5 0.2 0 0.4 0 0.5-0.2 1.8-0.2 3.6-0.3 5.3-0.3 1.8 0 3.5 0.1 5.3 0.3 1.7 0.4 3.1-0.7 3.5-2.3 0.2-1.6-0.8-3.1-2.4-3.3zm22.9 5.8c0.2 0 0.4 0 0.5-0.2 1.8-0.2 3.6-0.3 5.3-0.3 1.8 0 3.5 0.1 5.3 0.3 1.7 0.4 3.1-0.7 3.5-2.3 0.2-1.6-0.8-3.1-2.4-3.3-2.3-0.4-4.2-0.6-6.4-0.6-1.9 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.7-2.4 3.3 0.2 1.6 1.6 2.5 3 2.5zM74 35.8c-2.3-0.4-4.2-0.6-6.4-0.6-1.9 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.7-2.4 3.3 0.2 1.6 1.6 2.5 3 2.5 0.2 0 0.4 0 0.5-0.2 1.8-0.2 3.6-0.3 5.3-0.3 1.8 0 3.5 0.1 5.3 0.3 1.7 0.4 3.1-0.7 3.5-2.3C76.6 37.5 75.6 36 74 35.8Zm-35.1 0c-2.3-0.4-4.4-0.6-6.4-0.6-2.1 0-4.1 0.2-6.4 0.6-1.6 0.2-2.6 1.7-2.4 3.3 0.4 1.6 1.8 2.7 3.5 2.3 1.8-0.2 3.6-0.3 5.3-0.3 1.8 0 3.5 0.1 5.3 0.3 0.2 0.2 0.4 0.2 0.6 0.2 1.3 0 2.7-0.9 2.9-2.5 0.2-1.6-0.8-3.1-2.4-3.3z"]
    };

    const customCompare: IconDefinition = {
        prefix: 'fas',
        // @ts-ignore
        iconName: 'custom-compare',
        icon: [14, 10, [], "", "m 8.5098921,7.1756885 c -1.462855,-1.252626 -2.9612036,-2.591747 -4.2862386,-3.632718 -0.0329,0.811234 -0.07,1.4481 -0.07,2.292376 -1.38699,0.0086 -2.69238,0.01232 -4.10399001,0.02463 9.3e-4,0.925599 -0.004,1.780274 -0.009,2.7237908 1.34541001,-0.0077 2.70264001,-0.03906 4.08349001,-0.05859 0.0279,0.829355 0.009,1.7061237 0.0181,2.3690807 1.63103,-1.3167557 2.8096476,-2.3030007 4.3674666,-3.7185685 z M 6.0973105,3.6666205 c 1.4628546,-1.252626 2.9612036,-2.591747 4.2862425,-3.63271804 0.0329,0.81123404 0.06999,1.44810004 0.06999,2.29237604 1.386984,0.0086 2.692374,0.01232 4.103984,0.02463 -9.2e-4,0.925599 0.004,1.780274 0.009,2.723791 -1.34541,-0.0077 -2.70264,-0.03906 -4.083484,-0.05859 -0.02785,0.829355 -0.0089,1.706124 -0.01813,2.369081 C 8.8338891,6.0684345 7.6552711,5.0821895 6.0974525,3.6666215 Z"]
    }

    library.add(
        customCompare,
        customStrongs,
        customWholeVerseFalse,
        customWholeVerseTrue,
        faPenSquare,
        faCompressArrowsAlt,
        faExpandArrowsAlt,
        faArrowsAltV,
        faTextWidth,
        faHeadphones,
        faEdit,
        faTags,
        faBookmark,
        faPlusCircle,
        faTrash,
        faFileAlt,
        faInfoCircle,
        faTimes,
        faPlus,
        faEllipsisH,
        faChevronCircleDown,
        faSort,
        faIndent,
        faOutdent,
        faHeart,
        faHistory,
        faFireAlt,
        faEyeSlash,
        faEye,
        faShareAlt,
        faQuestionCircle,
        faHandPointer,
        faSave,
        faCheck,
        faClock,
    );
}
