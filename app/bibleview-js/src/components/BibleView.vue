<template>
  <div>
    <HelloWorld/>
    <OsisFragment :content="osisFragment"/>
    <mycomp/>
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
  //import "@/code"
  import OsisFragment from "@/components/OsisFragment";
  import HelloWorld from "@/components/HelloWorld";

  export const Comp = {
    template: "<p>test mycomp</p>",
    created() {
      console.log("mycomp created");
      this.$options.template = "<p>asdfasdf from mycomp</p>";
    }
  };

  export default {
    name: "BibleView",
    components: {OsisFragment, HelloWorld, mycomp: Comp},
    filters: {
      toId(verse) {
        return `${verse.chapter}.${verse.verse}`;
      }
    },
    data() {
      return {
        osisFragment: "<div><b>test osisfragment via props</b><HelloWorld/></div>",
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
