// vue.config.js
module.exports = {
  lintOnSave: false,
  runtimeCompiler: true,
  //productionSourceMap: false,
  transpileDependencies: ["dom-highlight-range"],
  publicPath: "",
  configureWebpack: {
    devtool: 'inline-source-map'
  }
}
