<template>
  <div>
    Test from vue <span class="test">{{test}}</span>
  </div>
</template>
<script>
  export default {
    name: "BibleView",
    data() {
      return {
        test: "World"
      }
    }
  }
</script>
