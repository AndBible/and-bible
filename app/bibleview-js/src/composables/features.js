/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import {onBeforeMount, reactive, ref} from "@vue/runtime-core";
import {Deferred} from "@/utils";
import {Events, setupEventBusListener} from "@/eventbus";
import {useParsers} from "@/composables/parsers";

export function useCustomFeatures(android) {
    const features = reactive(new Set())

    const defer = new Deferred();
    const featuresLoaded = ref(false);
    const featuresLoadedPromise = ref(defer.wait());
    const {parse, initialize} = useParsers(android);

    // eslint-disable-next-line no-unused-vars
    async function reloadFeatures(featureModuleNames) {
        features.clear();
        if(featureModuleNames.includes("RefParser")) {
            await initialize();
            features.add("RefParser");
        }
    }

    setupEventBusListener(Events.RELOAD_ADDONS, ({featureModuleNames}) => {
        reloadFeatures(featureModuleNames)
    })

    onBeforeMount(() => {
        const featureModuleNames = new URLSearchParams(window.location.search).get("featureModuleNames");
        if (!featureModuleNames) return
        reloadFeatures(featureModuleNames.split(","))
            .then(() => {
                defer.resolve()
                featuresLoaded.value = true;
                console.log("Features loading finished");
            });
    })

    return {features, featuresLoadedPromise, featuresLoaded, parse};
}
