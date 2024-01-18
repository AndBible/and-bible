/*
 * Copyright (c) 2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import {defineConfig, UserConfig} from 'vite'
import { readFileSync } from 'fs';
import {config} from "./vite.config";
import "vitest";


function rawText() {
  return {
    name: 'raw-text-loader',
    transform(src: string, id: string) {
      const xmlRegEx = /\.(xml|html)$/;
      if (xmlRegEx.test(id)) {
        const xml = JSON.stringify(readFileSync(id).toString());
        return {
          code: `export default ${xml}`,
        };
      }
    },
  };
}

const testConfig: UserConfig = {
  ...config,
  // @ts-ignore
  test: {
    environment: "jsdom",
    css: {
      modules: {
        classNameStrategy: "non-scoped",
      }
    }
  }
}

testConfig.plugins!.push(rawText());

export default defineConfig(testConfig)
