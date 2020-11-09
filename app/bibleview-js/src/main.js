import Vue from 'vue'
import BibleView from "@/components/BibleView";
import VueCompositionApi from "@vue/composition-api";

Vue.config.productionTip = false

new Vue({
  render: h => h(BibleView),
}).$mount('#app')

Vue.use(VueCompositionApi);
