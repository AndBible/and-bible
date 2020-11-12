<!--
  - Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
  -
  - This file is part of And Bible (http://github.com/AndBible/and-bible).
  -
  - And Bible is free software: you can redistribute it and/or modify it under the
  - terms of the GNU General Public License as published by the Free Software Foundation,
  - either version 3 of the License, or (at your option) any later version.
  -
  - And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  - without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  - See the GNU General Public License for more details.
  -
  - You should have received a copy of the GNU General Public License along with And Bible.
  - If not, see http://www.gnu.org/licenses/.
  -->

<script>
import {inject} from "@vue/composition-api";
import {sprintf} from "sprintf-js";

export default {
  name: "TagMixin",
  setup() {
    const config = inject("config");
    const strings = inject("strings");
    return {config, strings}
  },
  filters: {
    sprintf(string, ...args) {
      return sprintf(string, ...args);
    },
    split(string, separator, n) {
      return string.split(separator)[n]
    }
  },
  created() {
    if(Object.values(this.$attrs).length > 0) {
      console.error(this.$options.name, this.$attrs);
    }
  }
}
</script>
