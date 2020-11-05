<template>
  <div>
     <span
         v-for="verse in verses"
         :id="verse|toId"
         :key="verse|toId"
         class="verse"
     >
       
       <template v-html="verse.text"/>
     </span>
  </div>
</template>
<script>
  import "@/code"

  export default {
    name: "BibleView",
    filters: {
      toId(verse) {
        return `${verse.chapter}.${verse.verse}`;
      }
    },
    data() {
      return {
        verses: [
          {
            ordinal: 1,
            chapter: 1,
            verse: 1,
            text: "<span>some text</span>"
          }
        ],
        bookmarks: [
          {
            range: [1, 2], // ordinal range
            labels: [1, 2]
          }
        ],
        labelsStyles: [
          {
            id: 1,
            color: "#FF0000"
          },
          {
            id: 2,
            color: "#00FF00"
          }
        ]

      }
    }
  }
</script>
