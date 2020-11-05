import Vue from 'vue'
import BibleView from "@/components/BibleView";

Vue.config.productionTip = false

new Vue({
  render: h => h(BibleView),
}).$mount('#app')
