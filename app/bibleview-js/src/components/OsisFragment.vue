<script>
import HelloWorld from "@/components/HelloWorld";

export default {
  name: "OsisFragment",
  props: {
    content: {
      type: String,
      default: "<div>test2</div>"
    }

  },
  components: {HelloWorld},
  template: "<div>test5</div>",
  created() {
    console.log("created", this.$options);
    this.$options.template = this.content; // "<div>test3 asldfkj</div>"; //this.content;
  }
}
</script>
